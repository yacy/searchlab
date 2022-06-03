/**
 *  CrawlStartService
 *  Copyright 21.04.2022 by Michael Peter Christen, @orbiterlab
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package eu.searchlab.http.services;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.elasticsearch.index.query.QueryBuilders;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import eu.searchlab.Searchlab;
import eu.searchlab.corpus.Action;
import eu.searchlab.corpus.ActionSequence;
import eu.searchlab.corpus.CrawlStart;
import eu.searchlab.http.Service;
import eu.searchlab.http.ServiceRequest;
import eu.searchlab.http.ServiceResponse;
import eu.searchlab.tools.Digest;
import eu.searchlab.tools.Domains;
import eu.searchlab.tools.JSONList;
import eu.searchlab.tools.Logger;
import eu.searchlab.tools.MultiProtocolURL;
import net.yacy.grid.io.index.CrawlstartDocument;
import net.yacy.grid.io.index.CrawlstartMapping;
import net.yacy.grid.io.index.FulltextIndex;
import net.yacy.grid.io.index.Sort;
import net.yacy.grid.io.index.WebMapping;

/**
 *
 * Test URL:
 * http://localhost:8400/en/api/crawlStart.json?crawlingURL=yacy.net&indexmustnotmatch=.*Mitmachen.*&mustmatch=.*yacy.net.*
 * http://localhost:8400/en/api/crawlStart.json?crawlingURL=ix.de&crawlingDepth=6&priority=true
 * http://localhost:8400/en/api/crawlStart.json?crawlingURL=tagesschau.de&loaderHeadless=false
 *
 * then check crawl queue status at http://localhost:15672/
 * default account is guest:guest
 */
public class CrawlStartService  extends AbstractService implements Service {

    @Override
    public String[] getPaths() {
        return new String[] {"/api/crawlstart.json", "/production/crawler/"};
    }

    public static JSONObject crawlStartDefaultClone() {
        final JSONObject json = new JSONObject(true);
        CrawlStart.defaultValues.keySet().forEach(key -> {
            try {
                json.put(key, CrawlStart.defaultValues.get(key));
            } catch (final JSONException e) {}
        });
        return json;
    }

    public static class CrawlstartURLSplitter {

        private final List<MultiProtocolURL> crawlingURLArray;
        private final List<String> badURLStrings;

        public CrawlstartURLSplitter(String crawlingURLsString) {
            Logger.info(this.getClass(), "splitting url list: " + crawlingURLsString);
            crawlingURLsString = crawlingURLsString.replaceAll("\\|http", "\nhttp").replaceAll("%7Chttp", "\nhttp").replaceAll("%0D%0A", "\n").replaceAll("%0A", "\n").replaceAll("%0D", "\n").replaceAll(" ", "\n");
            final String[] crawlingURLs = crawlingURLsString.split("\n");
            this.crawlingURLArray = new ArrayList<>();
            this.badURLStrings = new ArrayList<>();
            for (final String u: crawlingURLs) {
                if (u.length() == 0) continue;
                try {
                    final MultiProtocolURL url = new MultiProtocolURL(u);
                    Logger.info(this.getClass(), "splitted url: " + url.toNormalform(true));
                    this.crawlingURLArray.add(url);
                } catch (final MalformedURLException e) {
                    this.badURLStrings.add(u);
                    Logger.warn(this.getClass(), "error when starting crawl with splitter url " + u + "; splitted from " + crawlingURLsString, e);
                }
            }
        }

        public List<MultiProtocolURL> getURLs() {
            return this.crawlingURLArray;
        }

        public List<String> getBadURLs() {
            return this.badURLStrings;
        }
    }

    public final static DateFormat secondDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
    public static String getCrawlID(final MultiProtocolURL url, final Date date, final int count) {
        String id = url.getHost();
        if (id.length() > 80) id = id.substring(0, 80) + "-" + id.hashCode();
        id = id + "-" + secondDateFormat.format(date).replace(':', '-').replace(' ', '-') + "-" + count;
        return id;
    }

    @Override
    public ServiceResponse serve(final ServiceRequest serviceRequest) {
        final JSONObject crawlstart = crawlStartDefaultClone();

        // read call attributes using the default crawlstart key names
        final String user_id = serviceRequest.getUser();
        try {
            for (final String key: crawlstart.keySet()) {
                final Object object = crawlstart.get(key);
                if (object instanceof String) {
                	String v = serviceRequest.get(key, crawlstart.getString(key));
                	if (v.equals("on")) v = "true";
                	if (v.equals("off")) v = "false";
                	crawlstart.put(key, v);
                }
                else if (object instanceof Integer) crawlstart.put(key, serviceRequest.get(key, crawlstart.getInt(key)));
                else if (object instanceof Long) crawlstart.put(key, serviceRequest.get(key, crawlstart.getLong(key)));
                else {
                    System.out.println("unrecognized type: " + object.getClass().toString());
                }
            }
            crawlstart.put("user_id", user_id); // we MUST overwrite that property otherwise the use is able to fake another user ID
        } catch (final JSONException e) {}

        // fix attributes
        final ActionSequence allCrawlstarts = new ActionSequence();
        try {
            final int crawlingDepth = crawlstart.optInt("crawlingDepth", 3);
            crawlstart.put("crawlingDepth", Math.min(crawlingDepth, 8)); // crawlingDepth shall not exceed 8 - this is used for enhanced balancing to be able to reach crawl leaves
            final String mustmatch = crawlstart.optString("mustmatch", CrawlStart.defaultValues.getString("mustmatch")).trim();
            crawlstart.put("mustmatch", mustmatch);
            final Map<String, Pattern> collections = WebMapping.collectionParser(crawlstart.optString("collection").trim());

            // set the crawl id
            final CrawlstartURLSplitter crawlstartURLs = new CrawlstartURLSplitter(crawlstart.getString("crawlingURL"));
            final Date now = new Date();
            // start the crawls; each of the url in a separate crawl to enforce parallel loading from different hosts
            int count = 0;
            crawlstarturls: for (final MultiProtocolURL url: crawlstartURLs.getURLs()) {
                final JSONObject singlecrawl = new JSONObject();
                for (final String key: crawlstart.keySet()) singlecrawl.put(key, crawlstart.get(key)); // create a clone of crawlstart
                final String crawl_id = getCrawlID(url, now, count++);
                final String start_url = url.toNormalform(true);
                final String start_ssld = Domains.getSmartSLD(url.getHost());
                singlecrawl.put("id", crawl_id);
                singlecrawl.put("user_id", user_id);
                singlecrawl.put("start_url", start_url);
                singlecrawl.put("start_ssld", start_ssld);

                //singlecrawl.put("crawlingURLs", new JSONArray().put(url.toNormalform(true)));

                // Create a crawlstart index entry: this will keep track of all crawls that have been started.
                // once such an entry is created, it is never changed or deleted again by any YaCy Grid process.
                final CrawlstartDocument crawlstartDoc = new CrawlstartDocument()
                        .setCrawlID(crawl_id)
                        .setUserID(user_id)
                        .setMustmatch(mustmatch)
                        .setCollections(collections.keySet())
                        .setCrawlstartURL(start_url)
                        .setCrawlstartSSLD(start_ssld)
                        .setInitDate(now)
                        .setData(singlecrawl);
                String crawlid = crawlstartDoc.getCrawlID();
                final boolean success = Searchlab.ec.writeMap(Searchlab.crawlstartIndexName, Searchlab.crawlstartTypeName, crawlid, crawlstartDoc.toMap());
                if (!success) {
                    Logger.warn("NOT CRAWLED: " + url.toString());
                    continue crawlstarturls;
                }

                // Create a crawler url tracking index entry: this will keep track of single urls and their status
                // While it is processed. The entry also serves as a double-check entry to terminate a crawl even if the
                // crawler is restarted.

                // delete the start url
                final String url_id = Digest.encodeMD5Hex(start_url);
                long deleted = Searchlab.ec.deleteByQuery(Searchlab.crawlerIndexName, QueryBuilders.termQuery("_id", url_id));
                Logger.info(this.getClass(), "deleted " + deleted + " old crawl index entries for _id");

                // Because 'old' crawls may block new ones we identify possible blocking entries using the mustmatch pattern.
                // We therefore delete all entries with the same mustmatch pattern before a crawl starts.
                if (mustmatch.equals(".*")) {
                    // we cannot delete all wide crawl status urls!
                    final FulltextIndex.Query q = Searchlab.ec.query(
                            Searchlab.crawlstartIndexName,
                            QueryBuilders.termQuery(CrawlstartMapping.start_url_s.name(), start_url),
                            null, Sort.DEFAULT, null, 0, 0, 100, 0, false);
                    final List<Map<String, Object>> results = q.results;
                    // from there we pick out the crawl start id and delete using them
                    for (int hitc = 0; hitc < results.size(); hitc++) {
                        final Map<String, Object> map = results.get(hitc);
                        crawlid = (String) map.get(CrawlstartMapping.crawl_id_s.name());
                        if (crawlid != null && crawlid.length() > 0) {
                            deleted = Searchlab.ec.deleteByQuery(Searchlab.crawlerIndexName, QueryBuilders.termQuery("crawl_id_s", crawlid));
                            Logger.info(this.getClass(), "deleted " + deleted + " old crawl index entries for crawl_id_s");
                        }
                    }
                    // we also delete all entries with same start_url and start_ssld
                    deleted = Searchlab.ec.deleteByQuery(Searchlab.crawlerIndexName, QueryBuilders.termQuery("start_url_s", start_url));
                    Logger.info(this.getClass(), "deleted " + deleted + " old crawl index entries for start_url_s");
                    deleted = Searchlab.ec.deleteByQuery(Searchlab.crawlerIndexName, QueryBuilders.termQuery("start_ssld_s", start_ssld));
                    Logger.info(this.getClass(), "deleted " + deleted + " old crawl index entries for start_ssld_s");
                } else {
                    // this should fit exactly on the old urls
                    // test url:
                    // curl -s -H 'Content-Type: application/json' -X GET http://localhost:9200/crawler/_search?q=_id:0a800a8ec1cc76b5eb8412ec494babc9 | python3 -m json.tool
                    deleted = Searchlab.ec.deleteByQuery(Searchlab.crawlerIndexName, QueryBuilders.termQuery("mustmatch_s", mustmatch.replace("\\", "\\\\")));
                    Logger.info(this.getClass(), "deleted " + deleted + " old crawl index entries");
                }
                // we do not create a crawler document entry here because that would conflict with the double check.
                // crawler documents must be written after the double check has happened.

                // create a crawl queue entry
                final String queueName = "crawler_webcrawler_00";
                final ActionSequence json = new ActionSequence();
                json.setData(new JSONArray().put(singlecrawl));
                final JSONObject action = new JSONObject()
                        .put("type", "crawler")
                        .put("queue", queueName)
                        .put("id", crawl_id)
                        .put("user_id", user_id)
                        .put("depth", 0)
                        .put("sourcegraph", "rootasset");
                final Action crawlAction = new Action(action);
                final JSONObject graph = new JSONObject(true).put(WebMapping.canonical_s.getMapping().name(), start_url);
                crawlAction.setJSONListAsset("rootasset", new JSONList().add(graph));
                json.addAction(crawlAction);
                allCrawlstarts.addAction(crawlAction);
                final byte[] b = json.toString().getBytes(StandardCharsets.UTF_8);
                Searchlab.queues.getQueue(queueName).send(b);
            }

            // construct a crawl start message
            allCrawlstarts.setData(new JSONArray().put(crawlstart));
            allCrawlstarts.put("success", allCrawlstarts.getActions().size() > 0);
        } catch (final IOException | JSONException e) {
            Logger.warn(this.getClass(), "error when starting crawl", e);
        }

        // finally add the crawl start on the queue
        return new ServiceResponse(allCrawlstarts);
    }
}
