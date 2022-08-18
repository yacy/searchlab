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

package eu.searchlab.http.services.production;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.elasticsearch.index.query.QueryBuilders;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import eu.searchlab.Searchlab;
import eu.searchlab.aaaaa.Authorization.Grade;
import eu.searchlab.corpus.Action;
import eu.searchlab.corpus.ActionSequence;
import eu.searchlab.corpus.CrawlStart;
import eu.searchlab.http.AbstractService;
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
import net.yacy.grid.io.index.ElasticsearchClient;
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

    public static String siteFilter(final Collection<? extends MultiProtocolURL> urls) {
        final StringBuilder filter = new StringBuilder();
        filter.append("(smb|ftp|https?)://(www.)?(");
        for (final MultiProtocolURL url: urls) {
            String host = url.getHost();
            if (host == null) continue;
            if (host.startsWith("www.")) host = host.substring(4);
            filter.append(Pattern.quote(host.toLowerCase(Locale.ROOT))).append(".*|");
        }
        filter.setCharAt(filter.length() - 1, ')');
        return filter.toString();
    }

    public static String subpathFilter(final Collection<? extends MultiProtocolURL> urls) {
        final LinkedHashSet<String> filters = new LinkedHashSet<>(); // first collect in a set to eliminate doubles
        for (final MultiProtocolURL url: urls) filters.add(mustMatchSubpath(url));
        final StringBuilder filter = new StringBuilder();
        for (final String urlfilter: filters) filter.append('|').append(urlfilter);
        return filter.length() > 0 ? filter.substring(1) : ".*";
    }

    public static String mustMatchSubpath(final MultiProtocolURL url) {
        String host = url.getHost();
        if (host == null) return url.getProtocol() + ".*";
        if (host.startsWith("www.")) host = host.substring(4);
        String protocol = url.getProtocol();
        if ("http".equals(protocol) || "https".equals(protocol)) protocol = "https?+";
        return new StringBuilder(host.length() + 20).append(protocol).append("://(www.)?").append(Pattern.quote(host.toLowerCase(Locale.ROOT))).append(url.getPath()).append(".*").toString();
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
        final JSONObject aclLevel = serviceRequest.getACL();

        // read call attributes using the default crawlstart key names
        String user_id = serviceRequest.getUser();
        if (!"en".equals(user_id)) try {
            aclLevel.getJSONObject("crawler").getJSONObject("forUser").put("value", user_id);
        } catch (final JSONException e1) {}
        final String for_user_id = serviceRequest.get("for_user_id", user_id);
        if (for_user_id.length() > 0 && serviceRequest.getAuthorizationGrade() == Grade.L08_Maintainer) user_id = for_user_id;

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
            final CrawlstartURLSplitter crawlstartURLs = new CrawlstartURLSplitter(crawlstart.getString("crawlingURL"));
            int crawlingDepth = crawlstart.optInt("crawlingDepth", 3);
            crawlingDepth = Math.min(crawlingDepth, aclLevel.optJSONObject("crawler").optJSONObject("crawlingDepth").optInt("max"));
            crawlstart.put("crawlingDepth", Math.min(crawlingDepth, 8)); // crawlingDepth shall not exceed 8 - this is used for enhanced balancing to be able to reach crawl leaves
            String mustmatch = crawlstart.optString("mustmatch", CrawlStart.defaultValues.getString("mustmatch")).trim();
            String range = crawlstart.optString("range", "wide");
            if (aclLevel.optJSONObject("crawler").optJSONObject("collection").optBoolean("disabled")) range = aclLevel.optJSONObject("crawler").optJSONObject("collection").optString("value", "domain");
            final boolean fullDomain = "domain".equals(range); // special property in simple crawl start
            final boolean subPath    = "subpath".equals(range); // special property in simple crawl start
            final boolean wide       = "wide".equals(range); // special property in simple crawl start

            // compute mustmatch filter according to rootURLs
            if (fullDomain || subPath) {
                String siteFilter = ".*";
                if (fullDomain) {
                    siteFilter = siteFilter(crawlstartURLs.getURLs());
                } else if (subPath) {
                    siteFilter = subpathFilter(crawlstartURLs.getURLs());
                }
                if (".*".equals(mustmatch)) {
                    mustmatch = siteFilter;
                } else if (!".*".equals(siteFilter)) {
                    // combine both
                    mustmatch = "(" + mustmatch + ")|(" + siteFilter + ")";
                }
            }
            if (wide) mustmatch = ".*";
            crawlstart.put("mustmatch", mustmatch);

            final Map<String, Pattern> collections = WebMapping.collectionParser(crawlstart.optString("collection").trim());

            // set the crawl id
            final Date now = new Date();
            // start the crawls; each of the url in a separate crawl to enforce parallel loading from different hosts
            int count = 0;
            crawlstarturls: for (final MultiProtocolURL url: crawlstartURLs.getURLs()) {
                final JSONObject singlecrawl = new JSONObject();
                for (final String key: crawlstart.keySet()) singlecrawl.put(key, crawlstart.get(key)); // create a clone of crawlstart

                // find all user_ids which have participated in the same crawl
                final Map<String, Long> agg_host_s = Searchlab.ec.aggregation(
                        System.getProperties().getProperty("grid.elasticsearch.indexName.web", ElasticsearchClient.DEFAULT_INDEXNAME_WEB),
                        WebMapping.host_s.name(), url.getHost(), WebMapping.user_id_s.name());
                final Map<String, Long> agg_host_sxt = Searchlab.ec.aggregation(
                        System.getProperties().getProperty("grid.elasticsearch.indexName.web", ElasticsearchClient.DEFAULT_INDEXNAME_WEB),
                        WebMapping.host_s.name(), url.getHost(), WebMapping.user_id_sxt.name());
                for (final String s: agg_host_s.keySet()) {
                    if (!agg_host_sxt.containsKey(s)) agg_host_sxt.put(s, agg_host_s.get(s));
                }
                agg_host_sxt.put(user_id, 1L);
                final JSONArray user_ids = new JSONArray();
                for (final String s: agg_host_sxt.keySet()) user_ids.put(s);

                final String crawl_id = getCrawlID(url, now, count++);
                final String start_url = url.toNormalform(true);
                final String start_ssld = Domains.getSmartSLD(url.getHost());
                singlecrawl.put("id", crawl_id);
                singlecrawl.put("user_id", user_id);
                singlecrawl.put("user_ids", user_ids);
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
                        .put("user_ids", user_ids)
                        .put("depth", 0)
                        .put("sourcegraph", "rootasset");
                final Action crawlAction = new Action(action);
                final JSONObject graph = new JSONObject(true).put(WebMapping.canonical_s.getMapping().name(), start_url);
                crawlAction.setJSONListAsset("rootasset", new JSONList().add(graph));
                json.addAction(crawlAction);
                allCrawlstarts.addAction(crawlAction);
                final byte[] b = json.toString().getBytes(StandardCharsets.UTF_8);
                Searchlab.queues.getQueue(queueName).send(b);
                Searchlab.accounting.storeCrawlStart(user_id, json);
                Searchlab.accounting.storeCorpus(user_id, range, crawlstartURLs.getURLs(), collections.keySet(), crawlingDepth, 0);
            }

            // construct a crawl start message
            allCrawlstarts.setData(new JSONArray().put(crawlstart));
            allCrawlstarts.put("success", allCrawlstarts.getActions().size() > 0);
        } catch (final IOException | JSONException e) {
            Logger.warn(this.getClass(), "error when starting crawl", e);
        }

        final JSONObject json = new JSONObject(true);
        try {
            json.put("crawl", allCrawlstarts);
            json.put("acl", aclLevel);
        } catch (final JSONException e) {
            Logger.error(e);
        }

        // finally add the crawl start on the queue
        return new ServiceResponse(json);
    }
}
