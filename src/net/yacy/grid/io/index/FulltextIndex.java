/**
 *  FulltextIndex
 *  Copyright 22.11.2021 by Michael Peter Christen, @orbiterlab
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General private
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General private License for more details.
 *
 *  You should have received a copy of the GNU Lesser General private License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.grid.io.index;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.elasticsearch.client.transport.NoNodeAvailableException;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

/**
 * To get data out of the elasticsearch index which is written with this client, try:
 * http://localhost:9200/web/_search?q=*:*
 * http://localhost:9200/crawler/_search?q=*:*
 *
 */
public interface FulltextIndex {


    /**
     * A refresh request making all operations performed since the last refresh available for search. The (near) real-time
     * capabilities depends on the index engine used. For example, the internal one requires refresh to be called, but by
     * default a refresh is scheduled periodically.
     * If previous indexing steps had been done, it is required to call this method to get most recent documents into the search results.
     */
    public void refresh(String indexName);

    /**
     * create a new index. This method must be called to ensure that an elasticsearch index is available and can be used.
     * @param indexName
     * @param shards
     * @param replicas
     * @throws NoNodeAvailableException | IllegalStateException in case that no elasticsearch server can be contacted.
     */
    public void createIndexIfNotExists(String indexName, final int shards, final int replicas);

    public void setMapping(String indexName, String mapping);

    /**
     * Close the connection to the remote elasticsearch client. This should only be called when the application is
     * terminated.
     * Please avoid to open and close the ElasticsearchClient for the same cluster and index more than once.
     * To avoid that this method is called more than once, the elasticsearch_client object is set to null
     * as soon this was called the first time. This is needed because the finalize method calls this
     * method as well.
     */
    public void close();

    /**
     * Get the number of documents in the search index for a given search query
     *
     * @param q
     *            the query
     * @return the count of all documents in the index which matches with the query
     */
    public long count(final String indexName, final String user_id, final YaCyQuery yq);


    /**
     * Get the document for a given id.
     * @param indexName the name of the index
     * @param id the unique identifier of a document
     * @return the document, if it exists or null otherwise;
     */
    public boolean exist(String indexName, final String id);

    public Set<String> existBulk(String indexName, final Collection<String> ids);

    /**
     * Delete a document for a given id.
     * ATTENTION: deleted documents cannot be re-inserted again if version number
     * checking is used and the new document does not comply to the version number
     * rule. The information which document was deleted persists for one minute and
     * then inserting documents with the same version number as before is possible.
     * To modify this behavior, change the configuration setting index.gc_deletes
     *
     * @param id
     *            the unique identifier of a document
     * @return true if the document existed and was deleted, false otherwise
     */
    public boolean deleteByID(String indexName, String typeName, final String id);

    /**
     * Delete documents using a query. Check what would be deleted first with a normal search query!
     * Elasticsearch once provided a native prepareDeleteByQuery method, but this was removed
     * in later versions. Instead, there is a plugin which iterates over search results,
     * see https://www.elastic.co/guide/en/elasticsearch/plugins/current/plugins-delete-by-query.html
     * We simulate the same behaviour here without the need of that plugin.
     *
     * @param q
     * @return delete document count
     */
    public long deleteByQuery(String indexName, final String user_id, final YaCyQuery yq);


    /**
     * Read a json document from the search index for a given id.
     * Elasticsearch reads the '_source' field and parses the content as json.
     *
     * @param id
     *            the unique identifier of a document
     * @return the document as json, matched on a Map<String, Object> object instance
     */
    public Map<String, Object> readMap(final String indexName, final String id);

    public Map<String, Map<String, Object>> readMapBulk(final String indexName, final Collection<String> ids);

    /**
     * Write a json document into the search index. The id must be calculated by the calling environment.
     * This id should be unique for the json. The best way to calculate this id is, to use an existing
     * field from the jsonMap which contains a unique identifier for the jsonMap.
     *
     * @param indexName the name of the index
     * @param typeName the type of the index
     * @param id the unique identifier of a document
     * @param jsonMap the json document to be indexed in elasticsearch
     * @return true if the document with given id did not exist before, false if it existed and was overwritten
     */
    public boolean writeMap(String indexName, String typeName, String id, final Map<String, Object> jsonMap);

    /**
     * bulk message write
     * @param jsonMapList
     *            a list of json documents to be indexed
     * @param indexName
     *            the name of the index
     * @param typeName
     *            the type of the index
     * @return a list with error messages.
     *            The key is the id of the document, the value is an error string.
     *            The method was only successful if this list is empty.
     *            This must be a list, because keys may appear several times.
     */
    public BulkWriteResult writeMapBulk(final String indexName, final List<BulkEntry> jsonMapList);

    public static class BulkWriteResult {
        public final Map<String, String> errors;
        public final Set<String> created;
        public BulkWriteResult() {
            this.errors = new LinkedHashMap<>();
            this.created = new LinkedHashSet<>();
        }
        public Map<String, String> getErrors() {
            return this.errors;
        }
        public Set<String> getCreated() {
            return this.created;
        }
    }

    public final static DateTimeFormatter utcFormatter = ISODateTimeFormat.dateTime().withZoneUTC();

    public static class BulkEntry {
        public final String id;
        public final String type;
        //private Long version;
        public final  Map<String, Object> jsonMap;

        /**
         * initialize entry for bulk writes
         * @param id the id of the entry
         * @param type the type name
         * @param timestamp_fieldname the name of the timestamp field, null for unused. If a name is given here, then this field is filled with the current time
         * @param version the version number >= 0 for external versioning or null for forced updates without versioning
         * @param jsonMap the payload object
         */
        public BulkEntry(final String id, final String type, final String timestamp_fieldname, final Map<String, Object> jsonMap) {
            this.id = id;
            this.type = type;
            //this.version = version;
            this.jsonMap = jsonMap;
            if (timestamp_fieldname != null && !this.jsonMap.containsKey(timestamp_fieldname)) this.jsonMap.put(timestamp_fieldname, utcFormatter.print(System.currentTimeMillis()));
        }
    }

    public Query query(final String indexName, final QueryBuilder queryBuilder, final YaCyQuery postFilter, final Sort sort, final WebMapping highlightField, final int timezoneOffset, final int from, final int resultCount, final int aggregationLimit, final boolean explain, final WebMapping... aggregationFields);

    public static class Query {
        public int hitCount;
        public List<Map<String, Object>> results;
        public List<String> explanations;
        public List<Map<String, HighlightField>> highlights;
        public Map<String, List<Map.Entry<String, Long>>> aggregations;

        public Query() {
            this.hitCount = 0;
            this.results = new ArrayList<>(this.hitCount);
            this.explanations = new ArrayList<>(this.hitCount);
            this.highlights = new ArrayList<>(this.hitCount);
            this.aggregations = new HashMap<>();
        }
    }

}
