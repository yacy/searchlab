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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import eu.searchlab.Searchlab;
import eu.searchlab.storage.table.MinuteSeriesTable;
import eu.searchlab.tools.DateParser;
import eu.searchlab.tools.Logger;

public class IndexDAO {


    public final static class TimeCount {long time; long count; public TimeCount(final long time, final long count) {this.time = time; this.count = count;}}
    private final static Map<String, TimeCount> knownDocumentCount = new ConcurrentHashMap<>();

    public static long getIndexDocumentCount(String user_id) {
        if (user_id == null || user_id.length() == 0) user_id = "en";
        final long now = System.currentTimeMillis();
        final String index_name = System.getProperties().getProperty("grid.elasticsearch.indexName.web", ElasticsearchClient.DEFAULT_INDEXNAME_WEB);
        final long documents = user_id.equals("en") ? Searchlab.ec.count(index_name) : Searchlab.ec.count(index_name, WebMapping.user_id_sxt.getMapping().name(), user_id);
        knownDocumentCount.put(user_id, new TimeCount(now, documents));
        return documents;
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

        // get a total index count for user
        TimeCount tc = knownDocumentCount.get(user_id);
        if (tc == null || tc.time <= now - timeframe.framelength) {
            getIndexDocumentCount(user_id);
            tc = knownDocumentCount.get(user_id);
        }
        final int indexTimeForDocumentCount = (int) (Math.max(0, now - tc.time) / timeframe.steplength); // there is a slight chance that tc.time is a bit (just milliseconds) larger than 'now'
        assert indexTimeForDocumentCount >= 0;
        assert indexTimeForDocumentCount < timeframe.stepcount;

        // get list of all documents that have been created in the last 10 minutes
        final String index_name = System.getProperties().getProperty("grid.elasticsearch.indexName.web", ElasticsearchClient.DEFAULT_INDEXNAME_WEB);
        final Date fromDate = new Date(now - timeframe.framelength);
        final String dateField = WebMapping.load_date_dt.getMapping().name(); // like "load_date_dt": "2022-03-30T02:03:03.214Z",
        final String[] dataFields = new String[] {WebMapping.load_date_dt.getMapping().name()};
        //final String[] dataFields = new String[] {WebMapping.fresh_date_dt.getMapping().name(), WebMapping.last_modified.getMapping().name(), WebMapping.load_date_dt.getMapping().name()};

        final List<Map<String, Object>> documents = user_id.equals("en") ? Searchlab.ec.queryWithCompare(index_name, dateField, fromDate, dataFields) : Searchlab.ec.queryWithCompare(index_name, WebMapping.user_id_sxt.getMapping().name(), user_id, dateField, fromDate, dataFields);

        Logger.info("CountHistogram for " + timeframe.name() + " from " + fromDate.toString() + " to " + (new Date(now)).toString() + ": " + documents.size() + " documents.");

        // aggregate the documents to counts per second
        final long[] counts = new long[timeframe.stepcount];
        for (int i = 0; i < timeframe.stepcount; i++) counts[i] = 0L;

        final SimpleDateFormat iso8601MillisParser = DateParser.iso8601MillisParser();
        for (final Map<String, Object> map: documents) {
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
                    final int indexTime = Math.min(timeframe.stepcount - 1, Math.max(0, (int) ((now - date.getTime()) / timeframe.steplength)));
                    // we increment the count at the incrementTime to reflect
                    counts[indexTime]++;
                    assert counts[indexTime] >= 0;
                }
            } catch (final Exception e) {
                Logger.warn("Date parsing error with " + dates, e);
            }
        }

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

    public static MinuteSeriesTable getCrawlstartHistorgramAggregation() {
        // get list of all documents that have been created in the last 10 minutes
        final String index_name = System.getProperties().getProperty("grid.elasticsearch.indexName.crawlstart", ElasticsearchClient.DEFAULT_INDEXNAME_CRAWLSTART);
        final List<Map<String, Object>> documents = Searchlab.ec.queryAll(index_name);
        final String dateField = CrawlstartMapping.init_date_dt.getMapping().name(); // like "init_date_dt": "2022-08-18T21:58:28.918Z",
        MinuteSeriesTable tst = new MinuteSeriesTable(new String[] {}, new String[] {}, new String[] {"data.crawlstarts"}, false);
        for (final Map<String, Object> map: documents) {
            final String dates = (String) map.get(dateField);
            try {
                final Date date =DateParser.iso8601MillisParser().parse(dates);
                tst.addValues(date.getTime(), new String[0], new String[0], new long[] {1});
            } catch (final Exception e) {
                Logger.warn(e);
            }
        }
        tst.sort();
        tst = tst.aggregation();
        return tst;
    }

    public static long getIndexDocumentCollectionCount(final String user_id) {
        final String index_name = System.getProperties().getProperty("grid.elasticsearch.indexName.web", ElasticsearchClient.DEFAULT_INDEXNAME_WEB);
        final long collections = Searchlab.ec.aggregationCount(index_name, WebMapping.user_id_sxt.getMapping().name(), user_id, WebMapping.collection_sxt.getMapping().name());
        return collections;
    }

    public static long deleteIndexDocumentsByDomainName(final String user_id, final String domain_name) {
        final String index_name = System.getProperties().getProperty("grid.elasticsearch.indexName.web", ElasticsearchClient.DEFAULT_INDEXNAME_WEB);
        final long deleted = Searchlab.ec.delete(index_name, WebMapping.user_id_sxt.getMapping().name(), user_id, WebMapping.host_s.getMapping().name(), domain_name.trim());
        return deleted;
    }

}
