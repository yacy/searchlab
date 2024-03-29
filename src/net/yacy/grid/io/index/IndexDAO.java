/**
 *  IndexDAO
 *  Copyright 11.09.2022 by Michael Peter Christen, @orbiterlab
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


package net.yacy.grid.io.index;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.zip.GZIPOutputStream;

import org.elasticsearch.index.query.QueryBuilder;

import eu.searchlab.Searchlab;
import eu.searchlab.storage.io.IOPath;
import eu.searchlab.storage.table.MinuteSeriesTable;
import eu.searchlab.tools.AbstractCountingConsumer;
import eu.searchlab.tools.Cons;
import eu.searchlab.tools.CountingConsumer;
import eu.searchlab.tools.DateParser;
import eu.searchlab.tools.Logger;
import eu.searchlab.tools.Progress;

public class IndexDAO {

    /**
     * TimeCount is an object which gives a count to a given time
     */
    public final static class TimeCount {

        public final long time, count;

        public TimeCount(final long time, final long count) {
            this.time = time;
            this.count = count;
        }
    }

    // the knownDocumentCount is a map from a given user id to the TimeCount of documents of a given time
    private final static Map<String, TimeCount> knownDocumentCount = new ConcurrentHashMap<>();

    /**
     * get a document count together with the time when that count was retrieved
     * @param user_id the user id of the document count
     * @param maxtime the time (millis since epoch) as upper border for the age of the time within the TimeCount
     * @return
     */
    public static TimeCount getIndexDocumentTimeCount(String user_id, final double maxtime) {
        TimeCount tc = knownDocumentCount.get(user_id);
        if (tc == null || tc.time <= maxtime) {
            if (user_id == null || user_id.length() == 0) user_id = "en";
            final long now = System.currentTimeMillis();
            final String index_name = System.getProperties().getProperty("grid.elasticsearch.indexName.web", ElasticsearchClient.DEFAULT_INDEXNAME_WEB);
            final long documents = user_id.equals("en") ? Searchlab.ec.count(index_name) : Searchlab.ec.count(index_name, WebMapping.user_id_sxt.getMapping().name(), user_id);
            knownDocumentCount.put(user_id, new TimeCount(now, documents));
            tc = knownDocumentCount.get(user_id);
        }
        return tc;
    }

    public static enum Timeframe {
        per10hour(   60L * 1000L, 600, "Ten Hours"), // 600 minutes, smallest steplength! - otherwise this does not fit within the time resolution of one minute of this time series.
        per1day(         144000L, 600, "One Day"),   // 24 * 1 hour
        per1week(   7L * 144000L, 600, "One Week"),  // 24 * 1 hour
        per1month( 30L * 144000L, 600, "One Month"), // 30 * 1 day
        per1year( 365L * 144000L, 600, "One Year");  // 365 * 1 day
        public long steplength;
        public int stepcount;
        public long framelength;
        public String name;
        Timeframe(final long steplength, final int stepcount, final String name) {
            this.steplength = steplength;
            this.stepcount = stepcount;
            this.framelength = this.steplength * this.stepcount;
            this.name = name;
        }
    }

    public static MinuteSeriesTable getIndexDocumentCountHistorgramPerTimeframe(String user_id, final Timeframe timeframe) {
        if (user_id == null || user_id.length() == 0) user_id = "en";
        final long now = System.currentTimeMillis();
        final long afterTime = now - timeframe.framelength;

        // get list of all documents that have been created in the last 10 minutes
        final String index_name = System.getProperties().getProperty("grid.elasticsearch.indexName.web", ElasticsearchClient.DEFAULT_INDEXNAME_WEB);
        final Date fromDate = new Date(afterTime);
        final String dateField = WebMapping.load_date_dt.getMapping().name(); // like "load_date_dt": "2022-03-30T02:03:03.214Z",
        final String[] dataFields = new String[] {WebMapping.load_date_dt.getMapping().name()};
        //final String[] dataFields = new String[] {WebMapping.fresh_date_dt.getMapping().name(), WebMapping.last_modified.getMapping().name(), WebMapping.load_date_dt.getMapping().name()};

        final SimpleDateFormat iso8601MillisParser = DateParser.iso8601MillisParser();
        final long[] counts = new long[timeframe.stepcount];
        for (int i = 0; i < timeframe.stepcount; i++) counts[i] = 0L;
        final AtomicLong count = new AtomicLong(0);
        final Consumer<Map<String, Object>> consumer = (map) -> {
            /*
            for (final Map.Entry<String, Object> entry: map.entrySet()) {
                if (entry.getKey().endsWith("_dt") || entry.getKey().equals("last_modified")) {
                    Logger.info(entry.getKey() + ": " + entry.getValue().toString());
                }
            }
             */
            final String dates = (String) map.get(dateField);
            try {
                final Date date = iso8601MillisParser.parse(dates);

                // aggregate statistics about number of indexed documents by one for that indexTime
                if (date != null) {
                    // the increment time is a number from 0..599 which represents the time 0:=now/last minute; 599:=oldest == x minutes/days/years in the past
                    final long datetime = date.getTime(); // milliseconds since epoch
                    if (datetime >= afterTime && now >= datetime) {
                        // valid for statistical collection
                        assert datetime >= afterTime : "date is before required period by " + (afterTime - datetime) + " milliseconds";
                        assert now > datetime: "datetime is " + (datetime - now) + " milliseconds too large: " + dates;  // the document time must be in the past always
                        final long pasttime = now - datetime;
                        final int indexTime = Math.min(timeframe.stepcount - 1, Math.max(0, (int) (pasttime / timeframe.steplength)));
                        // we increment the count at the incrementTime to reflect
                        counts[indexTime]++;
                        assert counts[indexTime] >= 0;
                    }
                }
                count.incrementAndGet();
            } catch (final Exception e) {
                Logger.warn("Date parsing error with " + dates, e);
            }
        };

        // aggregate the documents to counts per second
        try {
            if (user_id.equals("en")) {
                Searchlab.ec.consumeAllWithCompare(0, consumer, index_name, dateField, fromDate, null,  dataFields).call();
            } else {
                Searchlab.ec.consumeAllWithCompare(0, consumer, index_name, WebMapping.user_id_sxt.getMapping().name(), user_id, dateField, fromDate, null, dataFields).call();
            }
        } catch (final Exception e) {Logger.warn(e);}
        Logger.info("CountHistogram for " + timeframe.name() + " from " + fromDate.toString() + " to " + (new Date(now)).toString() + ": " + count.get() + " documents.");


        // get a total index count for user: that is used to reconstruct the actual number of index entries from the aggregated number
        final TimeCount tc = getIndexDocumentTimeCount(user_id, now - timeframe.framelength);
        final long pt = now - tc.time;
        //assert pt >= 0;
        final int indexTimeForDocumentCount = (int) (Math.max(0, pt) / timeframe.steplength); // there is a slight chance that tc.time is a bit (just milliseconds) larger than 'now'
        assert indexTimeForDocumentCount >= 0;
        assert indexTimeForDocumentCount < timeframe.stepcount;

        // find the count offset for the indexTimeForDocumentCount position and correct the counts using the time count info
        counts[indexTimeForDocumentCount] += tc.count;
        for (int i = indexTimeForDocumentCount - 1; i >= 0; i--) {
            // go forward (SIC!) in time -> index increases
            counts[i] = counts[i + 1] + counts[i];
            assert counts[i] >= 0;
        }
        for (int i = indexTimeForDocumentCount + 1; i < timeframe.stepcount; i++) {
            // go backward (SIC!) in time -> index decreases
            counts[i] = counts[i - 1] - counts[i];
            assert counts[i] >= 0;
        }

        // make a time series
        final MinuteSeriesTable tst = new MinuteSeriesTable(new String[] {}, new String[] {}, new String[] {"data.documents"}, false);
        // go forward in time by counting backwards
        for (int i = timeframe.stepcount - 1; i >= 0; i--) {
            final long c = counts[i];
            assert c >= 0;
            assert now > i * timeframe.steplength : "now = " + now + ", i = " + i + ", timeframe.steplength = " + timeframe.steplength;
            tst.addValues(now - i * timeframe.steplength, new String[0], new String[0], new long[] {c});
        }
        tst.sort();
        return tst;
    }

    public static MinuteSeriesTable getCrawlstartHistogramAggregation() {
        // get list of all documents that have been created in the last 10 minutes
        final String index_name = System.getProperties().getProperty("grid.elasticsearch.indexName.crawlstart", ElasticsearchClient.DEFAULT_INDEXNAME_CRAWLSTART);
        final String dateField = CrawlstartMapping.init_date_dt.getMapping().name(); // like "init_date_dt": "2022-08-18T21:58:28.918Z",
        final MinuteSeriesTable tst = new MinuteSeriesTable(new String[] {}, new String[] {}, new String[] {"data.crawlstarts"}, false);
        final CountingConsumer<Map<String, Object>> consumer = new AbstractCountingConsumer<Map<String, Object>>() {
            @Override
            public void accept(final Map<String, Object> document) {
                final String dates = (String) document.get(dateField);
                try {
                    final Date date = DateParser.iso8601MillisParser().parse(dates);
                    tst.addValues(date.getTime(), new String[0], new String[0], new long[] {1});
                } catch (final Exception e) {
                    Logger.warn(e);
                }
            }
        };
        try {
            Searchlab.ec.consumeAllWithConstraints(0, consumer, index_name, null).call();
            tst.sort();
            return tst.aggregation();
        } catch (final Exception e) {
            Logger.warn(e.getMessage());
        }
        return tst;
    }

    // no constraints - all docs for a user

    public final static long deleteIndexDocumentsByUserID(final String user_id) {
        final String index_name = System.getProperties().getProperty("grid.elasticsearch.indexName.web", ElasticsearchClient.DEFAULT_INDEXNAME_WEB);
        final long deleted = Searchlab.ec.delete(index_name, Cons.of(WebMapping.user_id_sxt.getMapping().name(), user_id));
        return deleted;
    }

    public final static long getIndexDocumentsByUserID(final String user_id) {
        return getIndexDocumentTimeCount(user_id, System.currentTimeMillis() - 10000).count;
    }

    public final static Progress<Long> exportIndexDocumentsByUserID(final long expected, final String user_id, final File tempFile, IOPath targetPath) throws IOException {
        final OutputStream os = new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(tempFile), 8192), 8192);
        final String index_name = System.getProperties().getProperty("grid.elasticsearch.indexName.web", ElasticsearchClient.DEFAULT_INDEXNAME_WEB);
        final CountingConsumer<Map<String, Object>> consumer = outputStreamWriterConsumer(os);
        final Runnable finalizer = new Runnable() {@Override public void run() {try {
        	os.close();
        	Searchlab.io.write(targetPath, tempFile);
        	tempFile.delete();
        } catch (IOException e) {}}};
        return Searchlab.ec.consumeAllWithConstraints(expected, consumer, index_name, finalizer, Cons.of(WebMapping.user_id_sxt.getMapping().name(), user_id));
    }


    // domains

    public final static long deleteIndexDocumentsByDomainName(final String user_id, final String domain_name) {
        final String index_name = System.getProperties().getProperty("grid.elasticsearch.indexName.web", ElasticsearchClient.DEFAULT_INDEXNAME_WEB);
        final long deleted = Searchlab.ec.delete(index_name, Cons.of(WebMapping.user_id_sxt.getMapping().name(), user_id), Cons.of(WebMapping.host_s.getMapping().name(), domain_name.trim()));
        return deleted;
    }

    public final static long getIndexDocumentsByDomainNameCount(final String user_id, final String domain_name) {
        final String index_name = System.getProperties().getProperty("grid.elasticsearch.indexName.web", ElasticsearchClient.DEFAULT_INDEXNAME_WEB);
        final long count = Searchlab.ec.count(index_name, Cons.of(WebMapping.user_id_sxt.getMapping().name(), user_id), Cons.of(WebMapping.host_s.getMapping().name(), domain_name.trim()));
        return count;
    }

    public final static Progress<Long> exportIndexDocumentsByDomainName(final long expected, final String user_id, final String domain_name, final OutputStream os) throws IOException {
        final String index_name = System.getProperties().getProperty("grid.elasticsearch.indexName.web", ElasticsearchClient.DEFAULT_INDEXNAME_WEB);
        final CountingConsumer<Map<String, Object>> consumer = outputStreamWriterConsumer(os);
        Runnable finalizer = new Runnable() {@Override public void run() {try {os.close();} catch (IOException e) {}}};
        return Searchlab.ec.consumeAllWithConstraints(expected, consumer, index_name, finalizer, Cons.of(WebMapping.user_id_sxt.getMapping().name(), user_id), Cons.of(WebMapping.host_s.getMapping().name(), domain_name.trim()));
    }

    public final static Progress<Long> exportIndexDocumentsByDomainName(final long expected, final String user_id, final String domain_name, final File tempFile, IOPath targetPath) throws IOException {
    	final OutputStream os = new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(tempFile), 8192), 8192);
        final String index_name = System.getProperties().getProperty("grid.elasticsearch.indexName.web", ElasticsearchClient.DEFAULT_INDEXNAME_WEB);
        final CountingConsumer<Map<String, Object>> consumer = outputStreamWriterConsumer(os);
        final Runnable finalizer = new Runnable() {@Override public void run() {try {
        	os.close();
        	Searchlab.io.write(targetPath, tempFile);
        	tempFile.delete();
        } catch (IOException e) {}}};
        return Searchlab.ec.consumeAllWithConstraints(expected, consumer, index_name, finalizer, Cons.of(WebMapping.user_id_sxt.getMapping().name(), user_id), Cons.of(WebMapping.host_s.getMapping().name(), domain_name.trim()));
    }


    // collections

    public final static long getIndexDocumentCollectionCount(final String user_id) {
        final String index_name = System.getProperties().getProperty("grid.elasticsearch.indexName.web", ElasticsearchClient.DEFAULT_INDEXNAME_WEB);
        final long collections = Searchlab.ec.aggregationCount(index_name, WebMapping.collection_sxt.getMapping().name(), Cons.of(WebMapping.user_id_sxt.getMapping().name(), user_id));
        return collections;
    }

    public final static long getIndexDocumentByCollectionCount(final String user_id, final String collection_name) {
        final String index_name = System.getProperties().getProperty("grid.elasticsearch.indexName.web", ElasticsearchClient.DEFAULT_INDEXNAME_WEB);
        final long collections = Searchlab.ec.count(index_name, Cons.of(WebMapping.user_id_sxt.getMapping().name(), user_id), Cons.of(WebMapping.collection_sxt.getMapping().name(), collection_name.trim()));
        return collections;
    }

    public final static long deleteIndexDocumentsByCollectionName(final String user_id, final String collection_name) {
        final String index_name = System.getProperties().getProperty("grid.elasticsearch.indexName.web", ElasticsearchClient.DEFAULT_INDEXNAME_WEB);
        final long deleted = Searchlab.ec.delete(index_name, Cons.of(WebMapping.user_id_sxt.getMapping().name(), user_id), Cons.of(WebMapping.collection_sxt.getMapping().name(), collection_name.trim()));
        return deleted;
    }

    public final static Progress<Long> exportIndexDocumentsByCollectionName(final long expected, final String user_id, final String collection_name, final OutputStream os) throws IOException {
        final String index_name = System.getProperties().getProperty("grid.elasticsearch.indexName.web", ElasticsearchClient.DEFAULT_INDEXNAME_WEB);
        final CountingConsumer<Map<String, Object>> consumer = outputStreamWriterConsumer(os);
        Runnable finalizer = new Runnable() {@Override public void run() {try {os.close();} catch (IOException e) {}}};
        return Searchlab.ec.consumeAllWithConstraints(expected, consumer, index_name, finalizer, Cons.of(WebMapping.user_id_sxt.getMapping().name(), user_id), Cons.of(WebMapping.collection_sxt.getMapping().name(), collection_name.trim()));
    }

    public final static Progress<Long> exportIndexDocumentsByCollectionName(final long expected, final String user_id, final String collection_name, final File tempFile, IOPath targetPath) throws IOException {
    	final OutputStream os = new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(tempFile), 8192), 8192);
        final String index_name = System.getProperties().getProperty("grid.elasticsearch.indexName.web", ElasticsearchClient.DEFAULT_INDEXNAME_WEB);
        final CountingConsumer<Map<String, Object>> consumer = outputStreamWriterConsumer(os);
        final Runnable finalizer = new Runnable() {@Override public void run() {try {
        	os.close();
        	Searchlab.io.write(targetPath, tempFile);
        	tempFile.delete();
        } catch (IOException e) {}}};
        return Searchlab.ec.consumeAllWithConstraints(expected, consumer, index_name, finalizer, Cons.of(WebMapping.user_id_sxt.getMapping().name(), user_id), Cons.of(WebMapping.collection_sxt.getMapping().name(), collection_name.trim()));
    }

    // queries

    public final static Map<String, Object> readDocument(final String id) {
        final String index_name = System.getProperties().getProperty("grid.elasticsearch.indexName.web", ElasticsearchClient.DEFAULT_INDEXNAME_WEB);
        return Searchlab.ec.readDocument(index_name, id);
    }

    public final static long getIndexDocumentsByQueryCount(final String user_id, final String queryString) {
        final String index_name = System.getProperties().getProperty("grid.elasticsearch.indexName.web", ElasticsearchClient.DEFAULT_INDEXNAME_WEB);
        final YaCyQuery yq = new YaCyQuery(queryString);
        return Searchlab.ec.count(index_name, user_id, yq.getQueryBuilder());
    }

    public final static long deleteIndexDocumentsByQuery(final String user_id, final String query) {
        final YaCyQuery yq = new YaCyQuery(query);
        final String index_name = System.getProperties().getProperty("grid.elasticsearch.indexName.web", ElasticsearchClient.DEFAULT_INDEXNAME_WEB);
        return Searchlab.ec.deleteByQuery(index_name, user_id, yq);
    }

    public final static FulltextIndex.Query query(final String user_id, final YaCyQuery yq, final YaCyQuery postFilter, final Sort sort, final WebMapping highlightField, final int timezoneOffset, final int from, final int resultCount, final int aggregationLimit, final boolean explain, final WebMapping... aggregationFields) {
        final String index_name = System.getProperties().getProperty("grid.elasticsearch.indexName.web", ElasticsearchClient.DEFAULT_INDEXNAME_WEB);
        final QueryBuilder q = user_id == null || "en".equals(user_id) ? yq.getQueryBuilder() : Searchlab.ec.constraintQuery(yq.getQueryBuilder(), Cons.of(WebMapping.user_id_sxt.getMapping().name(), user_id));
        return Searchlab.ec.query(index_name, q, postFilter, sort, highlightField, timezoneOffset, from, resultCount, aggregationLimit, explain, aggregationFields);
    }

    public final static FulltextIndex.Query query(final String user_id, final YaCyQuery yq, final Sort sort, final WebMapping highlightField, final int from, final int resultCount, final boolean explain) {
        final String index_name = System.getProperties().getProperty("grid.elasticsearch.indexName.web", ElasticsearchClient.DEFAULT_INDEXNAME_WEB);
        final QueryBuilder q = user_id == null || "en".equals(user_id) ? yq.getQueryBuilder() : Searchlab.ec.constraintQuery(yq.getQueryBuilder(), Cons.of(WebMapping.user_id_sxt.getMapping().name(), user_id));
        return Searchlab.ec.query(index_name, q, sort, highlightField, explain, from, resultCount);
    }
    
    public final static Progress<Long> exportIndexDocumentsByQuery(final long expected, final String user_id, final String queryString, final File tempFile, IOPath targetPath) throws IOException {
    	final OutputStream os = new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(tempFile), 8192), 8192);
        final YaCyQuery yq = new YaCyQuery(queryString);
        final String index_name = System.getProperties().getProperty("grid.elasticsearch.indexName.web", ElasticsearchClient.DEFAULT_INDEXNAME_WEB);
        final QueryBuilder q = user_id == null || "en".equals(user_id) ? yq.getQueryBuilder() : Searchlab.ec.constraintQuery(yq.getQueryBuilder(), Cons.of(WebMapping.user_id_sxt.getMapping().name(), user_id));
        final CountingConsumer<Map<String, Object>> consumer = outputStreamWriterConsumer(os);
        final Runnable finalizer = new Runnable() {@Override public void run() {try {
        	os.close();
        	Searchlab.io.write(targetPath, tempFile);
        	tempFile.delete();
        } catch (IOException e) {}}};
        return Searchlab.ec.consumeAllWithQuery(expected, consumer, index_name, q, finalizer);
    }

    private final static CountingConsumer<Map<String, Object>> outputStreamWriterConsumer(final OutputStream os) {
        final CountingConsumer<Map<String, Object>> consumer = new AbstractCountingConsumer<Map<String, Object>>() {
            @Override
            public void accept(final Map<String, Object> document) {
                try {
                    os.write((new WebDocument(document)).toString().getBytes(StandardCharsets.UTF_8));
                    os.write('\n');
                    this.incCount();
                } catch (final IOException e) {}
            }
        };
        return consumer;
    }

}
