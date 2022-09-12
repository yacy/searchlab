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

public class IndexDAO {


    public final static class TimeCount {long time; long count; public TimeCount(long time, long count) {this.time = time; this.count = count;}}
    private final static Map<String, TimeCount> knownDocumentCount = new ConcurrentHashMap<>();

    public static long getIndexDocumentCount(final String user_id) {
        final long now = System.currentTimeMillis();
        final String index_name = System.getProperties().getProperty("grid.elasticsearch.indexName.web", ElasticsearchClient.DEFAULT_INDEXNAME_WEB);
        final long documents = Searchlab.ec.count(index_name, WebMapping.user_id_sxt.getMapping().name(), user_id);
        knownDocumentCount.put(user_id, new TimeCount(now, documents));
        return documents;
    }

    public static TimeSeriesTable getIndexDocumentCountHistorgramPerMinute(final String user_id) {
        final long now = System.currentTimeMillis();

        // get a total index count for user
        TimeCount tc = knownDocumentCount.get(user_id);
        if (tc == null || tc.time <= now - 600000) {
            getIndexDocumentCount(user_id);
            tc = knownDocumentCount.get(user_id);
        }
        final int indexTimeForDocumentCount = (int) ((now - tc.time) / 60000L);
        assert indexTimeForDocumentCount >= 0;
        assert indexTimeForDocumentCount < 600;

        // get list of all documents that have been created in the last 10 minutes
        final String index_name = System.getProperties().getProperty("grid.elasticsearch.indexName.web", ElasticsearchClient.DEFAULT_INDEXNAME_WEB);
        final Date fromDate = new Date(now - 600000);
        final String dateField = WebMapping.load_date_dt.getMapping().name();
        final List<Map<String, Object>> documents = Searchlab.ec.queryWithCompare(index_name, WebMapping.user_id_sxt.getMapping().name(), user_id, dateField, fromDate);

        // aggregate the documents to counts per second
        final long[] counts = new long[600]; for (int i = 0; i < 600; i++) counts[i] = 0L;
        for (final Map<String, Object> map: documents) {
            final Date date = (Date) map.get(dateField);

            // aggregate statistics about number of indexed documents by one for that indexTime
            if (date != null) {
                // the increment time is a number from 0..599 which represents the time 0=now/last minute; 599=oldest/10 minutes in the past
                final int indexTime = (int) ((now - date.getTime()) / 60000L);
                // we increment the count at the incrementTime to reflect
                counts[indexTime]++;
            }
        }

        // find the count offset for the indexTimeForDocumentCount position and correct the counts using the time count info
        counts[indexTimeForDocumentCount] += tc.count;
        for (int i = indexTimeForDocumentCount; i >= 0; i--) {
            // go forward in time -> index increases
            counts[i] += counts[i + 1];
        }
        for (int i = indexTimeForDocumentCount + 1; i <= 599; i++) {
            // go backward i time -> index decreases
            counts[i] -= counts[i - 1];
        }

        // make a time series
        final TimeSeriesTable tst = new TimeSeriesTable(new String[] {}, new String[] {}, new String[] {"data.documents"}, false);
        // go forward in time by counting backwards
        for (int i = 599; i >= 0; i--) {
            final long c = counts[i];
            tst.addValues(now - i * 60000L, new String[0], new String[0], new long[] {c});
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
