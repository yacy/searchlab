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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import eu.searchlab.Searchlab;
import eu.searchlab.aaa.Authentication;
import eu.searchlab.corpus.Action;
import eu.searchlab.corpus.ActionSequence;
import eu.searchlab.corpus.CrawlStart;
import eu.searchlab.http.Service;
import eu.searchlab.tools.Classification;
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
import net.yacy.grid.io.index.YaCyQuery;

public class CrawlStartService  extends AbstractService implements Service {

    @Override
    public String[] getPaths() {
        return new String[] {"/api/crawlStart.json"};
    }

    @Override
    public Type getType() {
        return Service.Type.OBJECT;
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
    public JSONObject serveObject(final JSONObject call) {
        final JSONObject crawlstart = crawlStartDefaultClone();

        // read call attributes using the default crawlstart key names
        for (final String key: crawlstart.keySet()) try {
            final Object object = crawlstart.get(key);
            if (object instanceof String) crawlstart.put(key, call.optString(key, crawlstart.getString(key)));
            else if (object instanceof Integer) crawlstart.put(key, call.optInt(key, crawlstart.getInt(key)));
            else if (object instanceof Long) crawlstart.put(key, call.optLong(key, crawlstart.getLong(key)));
            else if (object instanceof JSONArray) {
                final JSONArray a = crawlstart.getJSONArray(key);
                final Object cv = call.get(key);
                if (cv != null) crawlstart.put(key, cv);
            } else {
                System.out.println("unrecognized type: " + object.getClass().toString());
            }
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
            for (final MultiProtocolURL url: crawlstartURLs.getURLs()) {
                final JSONObject singlecrawl = new JSONObject();
                for (final String key: crawlstart.keySet()) singlecrawl.put(key, crawlstart.get(key)); // create a clone of crawlstart
                final String crawl_id = getCrawlID(url, now, count++);
                final String user_id = Authentication.ANONYMOUS_ID;
                final String start_url = url.toNormalform(true);
                final String start_ssld = Domains.getSmartSLD(url.getHost());
                singlecrawl.put("id", crawl_id);
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
                Searchlab.ec.writeMap(Searchlab.crawlstartIndexName, Searchlab.crawlstartTypeName, crawlid, crawlstartDoc.toMap());

                // Create a crawler url tracking index entry: this will keep track of single urls and their status
                // While it is processed. The entry also serves as a double-check entry to terminate a crawl even if the
                // crawler is restarted.

                // delete the start url
                final String urlid = Digest.encodeMD5Hex(start_url);
                long deleted = Searchlab.ec.deleteByQuery(Searchlab.crawlerIndexName, new YaCyQuery("{ \"_id\":\"" + urlid + "\"}", null, Classification.ContentDomain.ALL, 0));
                Logger.info(this.getClass(), "deleted " + deleted + " old crawl index entries for _id");

                // Because 'old' crawls may block new ones we identify possible blocking entries using the mustmatch pattern.
                // We therefore delete all entries with the same mustmatch pattern before a crawl starts.
                if (mustmatch.equals(".*")) {
                    // we cannot delete all wide crawl status urls!
                    final FulltextIndex.Query q = Searchlab.ec.query(Searchlab.crawlstartIndexName, new YaCyQuery("{ \"" + CrawlstartMapping.start_url_s.name() + "\":\"" + start_url + "\"}", null, Classification.ContentDomain.ALL, 0), null, Sort.DEFAULT, null, 0, 0, 100, 0, false);
                    final List<Map<String, Object>> results = q.results;
                    // from there we pick out the crawl start id and delete using them
                    for (int hitc = 0; hitc < results.size(); hitc++) {
                        final Map<String, Object> map = results.get(hitc);
                        crawlid = (String) map.get(CrawlstartMapping.crawl_id_s.name());
                        if (crawlid != null && crawlid.length() > 0) {
                            deleted = Searchlab.ec.deleteByQuery(Searchlab.crawlerIndexName, new YaCyQuery("{ \"crawl_id_s\":\"" + crawlid + "\"}", null, Classification.ContentDomain.ALL, 0));
                            Logger.info(this.getClass(), "deleted " + deleted + " old crawl index entries for crawl_id_s");
                        }
                    }
                    // we also delete all entries with same start_url and start_ssld
                    deleted = Searchlab.ec.deleteByQuery(Searchlab.crawlerIndexName, new YaCyQuery("{ \"start_url_s\":\"" + start_url + "\"}", null, Classification.ContentDomain.ALL, 0));
                    Logger.info(this.getClass(), "deleted " + deleted + " old crawl index entries for start_url_s");
                    deleted = Searchlab.ec.deleteByQuery(Searchlab.crawlerIndexName, new YaCyQuery("{ \"start_ssld_s\":\"" + start_ssld + "\"}", null, Classification.ContentDomain.ALL, 0));
                    Logger.info(this.getClass(), "deleted " + deleted + " old crawl index entries for start_ssld_s");
                } else {
                    // this should fit exactly on the old urls
                    // test url:
                    // curl -s -H 'Content-Type: application/json' -X GET http://localhost:9200/crawler/_search?q=_id:0a800a8ec1cc76b5eb8412ec494babc9 | python3 -m json.tool
                    final String deletequery = "{ \"mustmatch_s\":\"" + mustmatch.replace("\\", "\\\\") + "\"}";
                    deleted = Searchlab.ec.deleteByQuery(Searchlab.crawlerIndexName, new YaCyQuery(deletequery, null, Classification.ContentDomain.ALL, 0));
                    Logger.info(this.getClass(), "deleted " + deleted + " old crawl index entries");
                }
                // we do not create a crawler document entry here because that would conflict with the double check.
                // crawler documents must be written after the double check has happened.

                // create a crawl queue entry
                final String queueName = "webcrawler_00";
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
        return allCrawlstarts;
    }
}
