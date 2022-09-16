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

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import eu.searchlab.Searchlab;
import eu.searchlab.storage.table.TimeSeriesTable;
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
        per10minutes(1000L, 600), // 600 seconds / 10 minutes, 1 second per step, 600 steps
        per1hour(6L * 1000L, 600), // 6 * 10 minutes
        per1day(24L * 6000L, 600), // 24 * 1 hour
        per1month(30L * 24L * 6000L, 600), // 30 * 1 day
        per1year(365L * 24L * 6000L, 600); // 365 * 1 day
        long steplength;
        int stepcount;
        long framelength;
        Timeframe(final long steplength, final int stepcount) {
            this.steplength = steplength;
            this.stepcount = stepcount;
            this.framelength = this.steplength * this.stepcount;
        }
    }

    public static TimeSeriesTable getIndexDocumentCountHistorgramPerTimeframe(String user_id, final Timeframe timeframe) {
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
        final long[] counts = new long[timeframe.stepcount]; for (int i = 0; i < timeframe.stepcount; i++) counts[i] = 0L;

        for (final Map<String, Object> map: documents) {
            /*
            for (final Map.Entry<String, Object> entry: map.entrySet()) {
                if (entry.getKey().endsWith("_dt") || entry.getKey().equals("last_modified")) {
                    Logger.info(entry.getKey() + ": " + entry.getValue().toString());
                }
            }
            */
            final String dates = (String) map.get(dateField);
            final Date date = DateParser.iso8601MillisParser(dates);

            // aggregate statistics about number of indexed documents by one for that indexTime
            if (date != null) {
                // the increment time is a number from 0..599 which represents the time 0:=now/last minute; 599:=oldest == x minutes/days/years in the past
                final int indexTime = Math.min(timeframe.stepcount - 1, Math.max(0, (int) ((now - date.getTime()) / timeframe.steplength)));
                // we increment the count at the incrementTime to reflect
                counts[indexTime]++;
            }
        }

        // find the count offset for the indexTimeForDocumentCount position and correct the counts using the time count info
        counts[indexTimeForDocumentCount] += tc.count;
        for (int i = indexTimeForDocumentCount - 1; i >= 0; i--) {
            // go forward (SIC!) in time -> index increases
            counts[i] = counts[i + 1] + counts[i];
        }
        for (int i = indexTimeForDocumentCount + 1; i < timeframe.stepcount; i++) {
            // go backward (SIC!) in time -> index decreases
            counts[i] = counts[i - 1] - counts[i];
        }

        // make a time series
        final TimeSeriesTable tst = new TimeSeriesTable(new String[] {}, new String[] {}, new String[] {"data.documents"}, false);
        // go forward in time by counting backwards
        for (int i = timeframe.stepcount - 1; i >= 0; i--) {
            final long c = counts[i];
            tst.addValues(now - i * timeframe.steplength, new String[0], new String[0], new long[] {c});
        }
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
