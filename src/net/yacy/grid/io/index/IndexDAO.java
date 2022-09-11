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

import eu.searchlab.Searchlab;
import eu.searchlab.storage.table.TimeSeriesTable;

public class IndexDAO {

    public static long getIndexDocumentCount(final String user_id) {
        final String index_name = System.getProperties().getProperty("grid.elasticsearch.indexName.web", ElasticsearchClient.DEFAULT_INDEXNAME_WEB);
        final long documents = Searchlab.ec.count(index_name, WebMapping.user_id_sxt.getMapping().name(), user_id);
        return documents;
    }

    public static TimeSeriesTable getIndexDocumentCountHistorgramPerMinute(final String user_id) {
        final TimeSeriesTable tst = new TimeSeriesTable(new String[] {}, new String[] {}, new String[] {"data.documents"}, false);
        final String index_name = System.getProperties().getProperty("grid.elasticsearch.indexName.web", ElasticsearchClient.DEFAULT_INDEXNAME_WEB);
        final Date fromDate = new Date(System.currentTimeMillis() - 600000);
        final List<Map<String, Object>> documents = Searchlab.ec.queryWithCompare(index_name, WebMapping.user_id_sxt.getMapping().name(), user_id, WebMapping.load_date_dt.getMapping().name(), fromDate);

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
