/**
 *  ElasticsearchClient
 *  Copyright 18.02.2016 by Michael Peter Christen, @orbiterlab
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

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.lucene.search.Explanation;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.action.admin.cluster.stats.ClusterStatsAction;
import org.elasticsearch.action.admin.cluster.stats.ClusterStatsNodes;
import org.elasticsearch.action.admin.cluster.stats.ClusterStatsRequest;
import org.elasticsearch.action.admin.cluster.stats.ClusterStatsRequestBuilder;
import org.elasticsearch.action.admin.cluster.stats.ClusterStatsResponse;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.admin.indices.settings.put.UpdateSettingsRequest;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.get.MultiGetItemResponse;
import org.elasticsearch.action.get.MultiGetRequestBuilder;
import org.elasticsearch.action.get.MultiGetResponse;
import org.elasticsearch.action.search.SearchPhaseExecutionException;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.NoNodeAvailableException;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.cluster.block.ClusterBlockException;
import org.elasticsearch.cluster.health.ClusterHealthStatus;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.VersionType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms.Bucket;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import eu.searchlab.tools.DateParser;
import eu.searchlab.tools.Logger;

/**
 * To get data out of the elasticsearch index which is written with this client, try:
 * http://localhost:9200/web/_search?q=*:*
 * http://localhost:9200/crawler/_search?q=*:*
 *
 */
public class ElasticsearchClient implements FulltextIndex {


    public final static String DEFAULT_INDEXNAME_CRAWLSTART = "crawlstart";
    public final static String DEFAULT_INDEXNAME_CRAWLER    = "crawler";
    public final static String DEFAULT_INDEXNAME_QUERY      = "query";
    public final static String DEFAULT_INDEXNAME_WEB        = "web";
    public final static String DEFAULT_TYPENAME             = "web";

    private static final TimeValue scrollKeepAlive = TimeValue.timeValueSeconds(60);
    private static long throttling_time_threshold = 2000L; // update time high limit
    private static long throttling_ops_threshold = 1000L; // messages per second low limit
    private static double throttling_factor = 1.0d; // factor applied on update duration if both thresholds are passed

    private final String[] addresses;
    private final String clusterName;
    private Client elasticsearchClient;

    /**
     * create a elasticsearch transport client (remote elasticsearch)
     * @param addresses an array of host:port addresses
     * @param clusterName
     */
    public ElasticsearchClient(final String[] addresses, final String clusterName) throws IOException {
        Logger.info("ElasticsearchClient initiated client, " + addresses.length + " address: " + addresses[0] + ", clusterName: " + clusterName);
        this.addresses = addresses;
        this.clusterName = clusterName;
        final boolean ready = connect();
        if (!ready) throw new IOException("elastic not ready");
    }

    private boolean connect() {
        // create default settings and add cluster name
        final Settings.Builder settings = Settings.builder()
                .put("cluster.routing.allocation.enable", "all")
                .put("cluster.routing.allocation.allow_rebalance", "always");
        if (this.clusterName != null && this.clusterName.length() > 0) settings.put("cluster.name", this.clusterName);

        // create a client
        // newClient = new RestHighLevelClient(RestClient.builder(new HttpHost(host, port, "http"))); // future initialization method
        System.setProperty("es.set.netty.runtime.available.processors", "false"); // patch which prevents io.netty.util.NettyRuntime$AvailableProcessorsHolder.setAvailableProcessors from failing
        TransportClient newClient = null;
        while (true) try {
            newClient = new PreBuiltTransportClient(settings.build());
            break;
        } catch (final Exception e) {
            Logger.warn("failed to create an elastic client, retrying...", e);
            try { Thread.sleep(10000); } catch (final InterruptedException e1) {}
        }

        for (final String address: this.addresses) {
            final String a = address.trim();
            final int p = a.indexOf(':');
            if (p >= 0) try {
                final InetAddress i = InetAddress.getByName(a.substring(0, p));
                final int port = Integer.parseInt(a.substring(p + 1));
                //tc.addTransportAddress(new InetSocketTransportAddress(i, port));
                final TransportAddress ta = new TransportAddress(i, port);
                Logger.info("Elasticsearch: added TransportAddress " + ta.toString());
                newClient.addTransportAddress(ta);
            } catch (final UnknownHostException e) {
                Logger.warn("", e);
            }
        }

        // replace old client with new client
        final Client oldClient = this.elasticsearchClient;
        this.elasticsearchClient = newClient; // just switch out without closeing the old one first
        // because closing may cause blocking, we close this concurrently
        if (oldClient != null) new Thread() {
            @Override
            public void run() {
                this.setName("temporary client close job " + ElasticsearchClient.this.clusterName);
                try {
                    oldClient.close();
                } catch (NoNodeAvailableException | IllegalStateException | ClusterBlockException | SearchPhaseExecutionException e) {}
            }
        }.start();

        // check if client is ready
        final long timeout = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < timeout) {
            final boolean ready = clusterReady();
            Logger.info("Elasticsearch: node is " + (ready ? "ready" : "not ready"));
            if (ready) break;
            try {Thread.sleep(1000);} catch (final InterruptedException e) {}
        }
        return clusterReady();
    }

    @SuppressWarnings("unused")
    private ClusterStatsNodes getClusterStatsNodes() {
        final ClusterStatsRequest clusterStatsRequest =
                new ClusterStatsRequestBuilder(this.elasticsearchClient.admin().cluster(), ClusterStatsAction.INSTANCE).request();
        final ClusterStatsResponse clusterStatsResponse =
                this.elasticsearchClient.admin().cluster().clusterStats(clusterStatsRequest).actionGet();
        final ClusterStatsNodes clusterStatsNodes = clusterStatsResponse.getNodesStats();
        return clusterStatsNodes;
    }

    private boolean clusterReadyCache = false;

    public boolean clusterReady() {
        if (this.clusterReadyCache) return true;
        try {
            final ClusterHealthResponse chr = this.elasticsearchClient.admin().cluster().prepareHealth().get();
            this.clusterReadyCache = chr.getStatus() != ClusterHealthStatus.RED;
            return this.clusterReadyCache;
        } catch (final Exception e) {
            Logger.warn("", e);
            return false;
        }
    }

    @SuppressWarnings("unused")
    private boolean wait_ready(final long maxtimemillis, final ClusterHealthStatus status) {
        // wait for yellow status
        final long start = System.currentTimeMillis();
        boolean is_ready;
        do {
            // wait for yellow status
            final ClusterHealthResponse health = this.elasticsearchClient.admin().cluster().prepareHealth().setWaitForStatus(status).execute().actionGet();
            is_ready = !health.isTimedOut();
            if (!is_ready && System.currentTimeMillis() - start > maxtimemillis) return false;
        } while (!is_ready);
        return is_ready;
    }

    /**
     * A refresh request making all operations performed since the last refresh available for search. The (near) real-time
     * capabilities depends on the index engine used. For example, the internal one requires refresh to be called, but by
     * default a refresh is scheduled periodically.
     * If previous indexing steps had been done, it is required to call this method to get most recent documents into the search results.
     */
    @Override
    public void refresh(final String indexName) {
        new RefreshRequest(indexName);
    }

    public void settings(final String indexName) {
        final UpdateSettingsRequest request = new UpdateSettingsRequest(indexName);
        final String settingKey = "index.mapping.total_fields.limit";
        final int settingValue = 10000;
        final Settings.Builder settingsBuilder =
                Settings.builder()
                .put(settingKey, settingValue);
        request.settings(settingsBuilder);
        final CreateIndexRequest updateSettingsResponse =
                this.elasticsearchClient.admin().indices().prepareCreate(indexName).setSettings(settingsBuilder).request();
    }

    /**
     * create a new index. This method must be called to ensure that an elasticsearch index is available and can be used.
     * @param indexName
     * @param shards
     * @param replicas
     * @throws NoNodeAvailableException | IllegalStateException in case that no elasticsearch server can be contacted.
     */
    @Override
    public void createIndexIfNotExists(final String indexName, final int shards, final int replicas) {
        // create an index if not existent
        if (!this.elasticsearchClient.admin().indices().prepareExists(indexName).execute().actionGet().isExists()) {
            final Settings.Builder settings = Settings.builder()
                    .put("number_of_shards", shards)
                    .put("number_of_replicas", replicas);
            this.elasticsearchClient.admin().indices().prepareCreate(indexName)
            .setSettings(settings)
            .execute().actionGet();
        } else {
            //LOGGER.debug("Index with name {} already exists", indexName);
        }
    }

    @Override
    public void setMapping(final String indexName, final String mappings_properties) {
        try {
            this.elasticsearchClient.admin().indices().preparePutMapping(indexName)
            .setSource(mappings_properties, XContentType.JSON)
            .setIndicesOptions(IndicesOptions.LENIENT_EXPAND_OPEN)
            .setType("_default_").execute().actionGet();
        } catch (NoNodeAvailableException | IllegalStateException | ClusterBlockException | SearchPhaseExecutionException e) {
            Logger.warn("", e);
        };
    }

    /**
     * Close the connection to the remote elasticsearch client. This should only be called when the application is
     * terminated.
     * Please avoid to open and close the ElasticsearchClient for the same cluster and index more than once.
     * To avoid that this method is called more than once, the elasticsearch_client object is set to null
     * as soon this was called the first time. This is needed because the finalize method calls this
     * method as well.
     */
    @Override
    public void close() {
        if (this.elasticsearchClient != null) {
            this.elasticsearchClient.close();
            this.elasticsearchClient = null;
        }
    }

    /**
     * A finalize method is added to ensure that close() is always called.
     */
    @Override
    public void finalize() {
        this.close(); // will not cause harm if this is the second call to close()
    }

    /**
     * Retrieve a statistic object from the connected elasticsearch cluster
     *
     * @return cluster stats from connected cluster
     */
    public ClusterStatsNodes getStats() {
        final ClusterStatsRequest clusterStatsRequest =
                new ClusterStatsRequestBuilder(this.elasticsearchClient.admin().cluster(), ClusterStatsAction.INSTANCE).request();
        final ClusterStatsResponse clusterStatsResponse =
                this.elasticsearchClient.admin().cluster().clusterStats(clusterStatsRequest).actionGet();
        final ClusterStatsNodes clusterStatsNodes = clusterStatsResponse.getNodesStats();
        return clusterStatsNodes;
    }

    /**
     * Get the number of documents in the search index
     *
     * @return the count of all documents in the index
     */
    public long count(final String indexName) {
        final QueryBuilder q = QueryBuilders.constantScoreQuery(QueryBuilders.matchAllQuery());
        while (true) try {
            return countInternal(q, indexName);
        } catch (NoNodeAvailableException | IllegalStateException | ClusterBlockException | SearchPhaseExecutionException e) {
            Logger.info("ElasticsearchClient count failed with " + e.getMessage() + ", retrying to connect node...");
            try {Thread.sleep(1000);} catch (final InterruptedException ee) {}
            connect();
        }
    }

    public long count(final String indexName, final String key, final String value) {
        final TermQueryBuilder q = QueryBuilders.termQuery(key, value);
        while (true) try {
            return countInternal(q, indexName);
        } catch (NoNodeAvailableException | IllegalStateException | ClusterBlockException | SearchPhaseExecutionException e) {
            Logger.info("ElasticsearchClient count failed with " + e.getMessage() + ", retrying to connect node...");
            try {Thread.sleep(1000);} catch (final InterruptedException ee) {}
            connect();
        }
    }

    public long count(final String indexName, final String key0, final String value0, final String key1, final String value1) {
        final TermQueryBuilder q0 = QueryBuilders.termQuery(key0, value0);
        final TermQueryBuilder q1 = QueryBuilders.termQuery(key1, value1);
        final BoolQueryBuilder bFilter = QueryBuilders.boolQuery();
        bFilter.must(QueryBuilders.constantScoreQuery(q0));
        bFilter.must(QueryBuilders.constantScoreQuery(q1));

        while (true) try {
            return countInternal(bFilter, indexName);
        } catch (NoNodeAvailableException | IllegalStateException | ClusterBlockException | SearchPhaseExecutionException e) {
            Logger.info("ElasticsearchClient count failed with " + e.getMessage() + ", retrying to connect node...");
            try {Thread.sleep(1000);} catch (final InterruptedException ee) {}
            connect();
        }
    }

    @Override
    public long count(final String indexName, final String user_id, final YaCyQuery yq) {
        final QueryBuilder q = constraint(yq.getQueryBuilder(), WebMapping.user_id_sxt, user_id);
        while (true) try {
            return countInternal(q, indexName);
        } catch (NoNodeAvailableException | IllegalStateException | ClusterBlockException | SearchPhaseExecutionException e) {
            Logger.info("ElasticsearchClient count failed with " + e.getMessage() + ", retrying to connect node...");
            try {Thread.sleep(1000);} catch (final InterruptedException ee) {}
            connect();
        }
    }

    private long countInternal(final QueryBuilder q, final String indexName) {
        final SearchResponse response = this.elasticsearchClient.prepareSearch(indexName).setQuery(q).setSize(0).execute().actionGet();
        return response.getHits().getTotalHits();
    }

    private QueryBuilder constraint(final QueryBuilder qb, final WebMapping field, final String value) {
        if (value == null) return qb;
        final TermQueryBuilder c = QueryBuilders.termQuery(field.getMapping().name(), value);
        return QueryBuilders.boolQuery().must(qb).must(c);
    }

    /**
     * Get the document for a given id.
     * @param indexName the name of the index
     * @param id the unique identifier of a document
     * @return the document, if it exists or null otherwise;
     */
    @Override
    public boolean exist(final String indexName, final String id) {
        while (true) try {
            return existInternal(indexName, id);
        } catch (NoNodeAvailableException | IllegalStateException | ClusterBlockException | SearchPhaseExecutionException e) {
            Logger.info("ElasticsearchClient exist failed with " + e.getMessage() + ", retrying to connect node...");
            try {Thread.sleep(1000);} catch (final InterruptedException ee) {}
            connect();
        }
    }

    private boolean existInternal(final String indexName, final String id) {
        final GetResponse getResponse = this.elasticsearchClient
                .prepareGet(indexName, null, id)
                .setFetchSource(false)
                //.setOperationThreaded(false)
                .execute()
                .actionGet();
        return getResponse.isExists();
    }

    @Override
    public Set<String> existBulk(final String indexName, final Collection<String> ids) {
        while (true) try {
            return existBulkInternal(indexName, ids);
        } catch (NoNodeAvailableException | IllegalStateException | ClusterBlockException | SearchPhaseExecutionException e) {
            Logger.info("ElasticsearchClient existBulk failed with " + e.getMessage() + ", retrying to connect node...");
            try {Thread.sleep(1000);} catch (final InterruptedException ee) {}
            connect();
            continue;
        }
    }

    private Set<String> existBulkInternal(final String indexName, final Collection<String> ids) {
        if (ids == null || ids.size() == 0) return new HashSet<>();
        final MultiGetResponse multiGetItemResponses = this.elasticsearchClient.prepareMultiGet()
                .add(indexName, null, ids)
                .get();
        final Set<String> er = new HashSet<>();
        for (final MultiGetItemResponse itemResponse : multiGetItemResponses) {
            final GetResponse response = itemResponse.getResponse();
            if (response.isExists()) {
                er.add(response.getId());
            }
        }
        return er;
    }

    /**
     * Get the type name of a document or null if the document does not exist.
     * This is a replacement of the exist() method which does exactly the same as exist()
     * but is able to return the type name in case that exist is successful.
     * Please read the comment to exist() for details.
     * @param indexName
     *            the name of the index
     * @param id
     *            the unique identifier of a document
     * @return the type name of the document if it exists, null otherwise
     */
    @SuppressWarnings("unused")
    private String getType(final String indexName, final String id) {
        final GetResponse getResponse = this.elasticsearchClient.prepareGet(indexName, null, id).execute().actionGet();
        return getResponse.isExists() ? getResponse.getType() : null;
    }

    public long delete(final String indexName, final String key0, final String value0, final String key1, final String value1) {
        final TermQueryBuilder q0 = QueryBuilders.termQuery(key0, value0);
        final TermQueryBuilder q1 = QueryBuilders.termQuery(key1, value1);
        final BoolQueryBuilder bFilter = QueryBuilders.boolQuery();
        bFilter.must(QueryBuilders.constantScoreQuery(q0));
        bFilter.must(QueryBuilders.constantScoreQuery(q1));

        while (true) try {
            return deleteByQuery(indexName, bFilter);
        } catch (NoNodeAvailableException | IllegalStateException | ClusterBlockException | SearchPhaseExecutionException e) {
            Logger.info("ElasticsearchClient deleteByQuery failed with " + e.getMessage() + ", retrying to connect node...");
            try {Thread.sleep(1000);} catch (final InterruptedException ee) {}
            connect();
            continue;
        }
    }

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
    @Override
    public boolean delete(final String indexName, final String typeName, final String id) {
        while (true) try {
            return deleteInternal(indexName, typeName, id);
        } catch (NoNodeAvailableException | IllegalStateException | ClusterBlockException | SearchPhaseExecutionException e) {
            Logger.info("ElasticsearchClient delete failed with " + e.getMessage() + ", retrying to connect node...");
            try {Thread.sleep(1000);} catch (final InterruptedException ee) {}
            connect();
            continue;
        }
    }

    private boolean deleteInternal(final String indexName, final String typeName, final String id) {
        final DeleteResponse response = this.elasticsearchClient.prepareDelete(indexName, typeName, id).get();
        return response.getResult() == DocWriteResponse.Result.DELETED;
    }

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
    @Override
    public long deleteByQuery(final String indexName, final String user_id, final YaCyQuery yq) {
        final QueryBuilder q = constraint(yq.getQueryBuilder(), WebMapping.user_id_sxt, user_id);
        while (true) try {
            return deleteByQuery(indexName, q);
        } catch (NoNodeAvailableException | IllegalStateException | ClusterBlockException | SearchPhaseExecutionException e) {
            Logger.info("ElasticsearchClient deleteByQuery failed with " + e.getMessage() + ", retrying to connect node...");
            try {Thread.sleep(1000);} catch (final InterruptedException ee) {}
            connect();
            continue;
        }
    }

    public long deleteByQuery(final String indexName, final QueryBuilder q) {
        final Map<String, String> ids = new TreeMap<>();
        final SearchRequestBuilder request = this.elasticsearchClient.prepareSearch(indexName);
        request
        .setSearchType(SearchType.QUERY_THEN_FETCH)
        .setScroll(scrollKeepAlive)
        .setQuery(q)
        .setSize(100);
        SearchResponse response = null;
        try {
            response = request.execute().actionGet();
        } catch (final Exception e) {
            // catch IndexNotFoundException
            Logger.warn(e);
        }
        while (response != null) {
            // accumulate the ids here, don't delete them right now to prevent an interference of the delete with the
            // scroll
            for (final SearchHit hit : response.getHits().getHits()) {
                ids.put(hit.getId(), hit.getType());
            }
            // termination
            if (response.getHits().getHits().length == 0) break;
            // scroll
            response = this.elasticsearchClient.prepareSearchScroll(response.getScrollId()).setScroll(scrollKeepAlive).execute().actionGet();
        }
        return deleteBulk(indexName, ids);
    }

    /**
     * Delete a list of documents for a given set of ids
     * ATTENTION: read about the time-out of version number checking in the method above.
     *
     * @param ids
     *            a map from the unique identifier of a document to the document type
     * @return the number of deleted documents
     */
    private long deleteBulk(final String indexName, final Map<String, String> ids) {
        // bulk-delete the ids
        if (ids == null || ids.size() == 0) return 0;
        final BulkRequestBuilder bulkRequest = this.elasticsearchClient.prepareBulk();
        for (final Map.Entry<String, String> id : ids.entrySet()) {
            bulkRequest.add(new DeleteRequest().id(id.getKey()).index(indexName).type(id.getValue()));
        }
        bulkRequest.execute().actionGet();
        return ids.size();
    }

    /**
     * Read a document from the search index for a given id.
     * This is the cheapest document retrieval from the '_source' field because
     * elasticsearch does not do any json transformation or parsing. We
     * get simply the text from the '_source' field. This might be useful to
     * make a dump from the index content.
     *
     * @param id
     *            the unique identifier of a document
     * @return the document as source text
     */
    @SuppressWarnings("unused")
    private byte[] readSource(final String indexName, final String id) {
        final GetResponse response = this.elasticsearchClient.prepareGet(indexName, null, id).execute().actionGet();
        return response.getSourceAsBytes();
    }

    /**
     * Read a json document from the search index for a given id.
     * Elasticsearch reads the '_source' field and parses the content as json.
     *
     * @param id
     *            the unique identifier of a document
     * @return the document as json, matched on a Map<String, Object> object instance
     */
    @Override
    public Map<String, Object> readMap(final String indexName, final String id) {
        while (true) try {
            return readMapInternal(indexName, id);
        } catch (NoNodeAvailableException | IllegalStateException | ClusterBlockException | SearchPhaseExecutionException e) {
            Logger.info("ElasticsearchClient readMap failed with " + e.getMessage() + ", retrying to connect node...");
            try {Thread.sleep(1000);} catch (final InterruptedException ee) {}
            connect();
            continue;
        }
    }

    private Map<String, Object> readMapInternal(final String indexName, final String id) {
        final GetResponse response = this.elasticsearchClient.prepareGet(indexName, null, id).execute().actionGet();
        final Map<String, Object> map = getMap(response);
        return map;
    }

    @Override
    public Map<String, Map<String, Object>> readMapBulk(final String indexName, final Collection<String> ids) {
        while (true) try {
            return readMapBulkInternal(indexName, ids);
        } catch (NoNodeAvailableException | IllegalStateException | ClusterBlockException | SearchPhaseExecutionException e) {
            Logger.info("ElasticsearchClient readMapBulk failed with " + e.getMessage() + ", retrying to connect node...");
            try {Thread.sleep(1000);} catch (final InterruptedException ee) {}
            connect();
            continue;
        }
    }

    private Map<String, Map<String, Object>> readMapBulkInternal(final String indexName, final Collection<String> ids) {
        final MultiGetRequestBuilder mgrb = this.elasticsearchClient.prepareMultiGet();
        ids.forEach(id -> mgrb.add(indexName, null, id).execute().actionGet());
        final MultiGetResponse response = mgrb.execute().actionGet();
        final Map<String, Map<String, Object>> bulkresponse = new HashMap<>();
        for (final MultiGetItemResponse r: response.getResponses()) {
            final GetResponse gr = r.getResponse();
            if (gr != null) {
                final Map<String, Object> map = getMap(gr);
                bulkresponse.put(r.getId(), map);
            }
        }
        return bulkresponse;
    }

    protected static Map<String, Object> getMap(final GetResponse response) {
        Map<String, Object> map = null;
        if (response.isExists() && (map = response.getSourceAsMap()) != null) {
            if (!map.containsKey("id")) map.put("id", response.getId());
            if (!map.containsKey("type")) map.put("type", response.getType());
        }
        return map;
    }

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
    @Override
    public boolean writeMap(final String indexName, final String typeName, final String id, final Map<String, Object> jsonMap) {
        while (true) try {
            return writeMapInternal(indexName, typeName, id, jsonMap);
        } catch (final ClusterBlockException e) {
            Logger.info("ElasticsearchClient writeMap failed with " + e.getMessage());
            return false;
        } catch (NoNodeAvailableException | IllegalStateException | SearchPhaseExecutionException e) {
            Logger.info("ElasticsearchClient writeMap failed with " + e.getMessage() + ", retrying to connect node...");
            try {Thread.sleep(1000);} catch (final InterruptedException ee) {}
            connect();
            continue;
        }
    }

    // internal method used for a re-try after NoNodeAvailableException | IllegalStateException
    private boolean writeMapInternal(final String indexName, final String typeName, final String id, final Map<String, Object> jsonMap) {
        final long start = System.currentTimeMillis();
        // get the version number out of the json, if any is given
        final Long version = (Long) jsonMap.remove("_version");
        // put this to the index
        final UpdateResponse r = this.elasticsearchClient
                .prepareUpdate(indexName, typeName, id)
                .setDoc(jsonMap)
                .setUpsert(jsonMap)
                //.setVersion(version == null ? 1 : version.longValue())
                //.setVersionType(VersionType.EXTERNAL_GTE)
                .execute()
                .actionGet();
        if (version != null) jsonMap.put("_version", version); // to prevent side effects
        // documentation about the versioning is available at
        // https://www.elastic.co/blog/elasticsearch-versioning-support
        // TODO: error handling
        final boolean created = r != null && r.status() == RestStatus.CREATED; // true means created, false means updated
        final long duration = Math.max(1, System.currentTimeMillis() - start);
        Logger.info("ElasticsearchClient write entry to index " + indexName + ": " + (created ? "created":"updated") + ", " + duration + " ms");
        return created;
    }

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
    @Override
    public BulkWriteResult writeMapBulk(final String indexName, final List<BulkEntry> jsonMapList) {
        while (true) try {
            return writeMapBulkInternal(indexName, jsonMapList);
        } catch (NoNodeAvailableException | IllegalStateException | ClusterBlockException | SearchPhaseExecutionException e) {
            Logger.info("ElasticsearchClient writeMapBulk failed with " + e.getMessage() + ", retrying to connect node...");
            try {Thread.sleep(1000);} catch (final InterruptedException ee) {}
            connect();
            continue;
        }
    }

    private BulkWriteResult writeMapBulkInternal(final String indexName, final List<BulkEntry> jsonMapList) {
        final long start = System.currentTimeMillis();
        final BulkRequestBuilder bulkRequest = this.elasticsearchClient.prepareBulk();
        for (final BulkEntry be: jsonMapList) {
            if (be.id == null) continue;
            bulkRequest.add(
                    this.elasticsearchClient.prepareIndex(indexName, be.type, be.id).setSource(be.jsonMap)
                    .setCreate(false) // enforces OpType.INDEX
                    .setVersionType(VersionType.INTERNAL));
        }
        final BulkResponse bulkResponse = bulkRequest.get();
        final BulkWriteResult result = new BulkWriteResult();
        for (final BulkItemResponse r: bulkResponse.getItems()) {
            final String id = r.getId();
            final DocWriteResponse response = r.getResponse();
            if (response == null) {
                final String err = r.getFailureMessage();
                if (err != null) {
                    result.errors.put(id, err);
                }
            } else {
                if (response.getResult() == DocWriteResponse.Result.CREATED) result.created.add(id);
            }
        }
        final long duration = Math.max(1, System.currentTimeMillis() - start);
        long regulator = 0;
        final int created = result.created.size();
        final long ops = created * 1000 / duration;
        if (duration > throttling_time_threshold && ops < throttling_ops_threshold) {
            regulator = (long) (throttling_factor * duration);
            try {Thread.sleep(regulator);} catch (final InterruptedException e) {}
        }
        Logger.info("ElasticsearchClient write bulk to index " + indexName + ": " + jsonMapList.size() + " entries, " + result.created.size() + " created, " + result.errors.size() + " errors, " + duration + " ms" + (regulator == 0 ? "" : ", throttled with " + regulator + " ms") + ", " + ops + " objects/second");
        return result;
    }

    private final static DateTimeFormatter utcFormatter = ISODateTimeFormat.dateTime().withZoneUTC();

    public FulltextIndex.Query query(final String indexName, final YaCyQuery yq, final Sort sort, final int from, final int resultCount) {
        return query(indexName, yq.getQueryBuilder(), sort, from, resultCount);
    }

    public FulltextIndex.Query query(final String indexName, final QueryBuilder queryBuilder, final Sort sort, final int from, final int resultCount) {
        final FulltextIndex.Query query = new FulltextIndex.Query();
        for (int t = 0; t < 10; t++) try {

            // prepare request
            SearchRequestBuilder request = ElasticsearchClient.this.elasticsearchClient.prepareSearch(indexName);
            request
            .setExplain(false)
            .setSearchType(SearchType.QUERY_THEN_FETCH)
            .setQuery(queryBuilder)
            .setSearchType(SearchType.DFS_QUERY_THEN_FETCH) // DFS_QUERY_THEN_FETCH is slower but provides stability of search results
            .setFrom(from)
            .setSize(resultCount);

            request.clearRescorers();

            // apply sort
            request = sort.sort(request);

            // get response
            final SearchResponse response = request.execute().actionGet();
            final SearchHits searchHits = response.getHits();
            query.hitCount = (int) searchHits.getTotalHits();

            // evaluate search result
            //long totalHitCount = response.getHits().getTotalHits();
            final SearchHit[] hits = searchHits.getHits();
            query.results = new ArrayList<>(query.hitCount);
            query.explanations = new ArrayList<>(query.hitCount);
            query.highlights = new ArrayList<>(query.hitCount);
            for (final SearchHit hit: hits) {
                final Map<String, Object> map = hit.getSourceAsMap();
                if (!map.containsKey("id")) map.put("id", hit.getId());
                if (!map.containsKey("type")) map.put("type", hit.getType());
                query.results.add(map);
                query.highlights.add(hit.getHighlightFields());
                query.explanations.add("");
            }

            // evaluate aggregation
            // collect results: fields
            query.aggregations = new HashMap<>();
            return query;
        } catch (NoNodeAvailableException | IllegalStateException | ClusterBlockException | SearchPhaseExecutionException e) {
            Logger.warn("ElasticsearchClient query failed with " + e.getMessage() + ", retrying attempt " + t + " ...", e);
            try {Thread.sleep(1000);} catch (final InterruptedException eee) {}
            connect();
            continue;
        }
        return query;
    }

    /**
     * Searches using a elasticsearch query.
     * @param indexName the name of the search index
     * @param queryBuilder a query for the search
     * @param postFilter a filter that does not affect aggregations
     * @param timezoneOffset - an offset in minutes that is applied on dates given in the query of the form since:date until:date
     * @param from - from index to start the search from, 1st entry has from-index 0.
     * @param resultCount - the number of messages in the result; can be zero if only aggregations are wanted
     * @param aggregationLimit - the maximum count of facet entities, not search results
     * @param aggregationFields - names of the aggregation fields. If no aggregation is wanted, pass no (zero) field(s)
     */
    @Override
    public FulltextIndex.Query query(final String indexName, final String user_id, final YaCyQuery yq, final YaCyQuery postFilter, final Sort sort, final WebMapping highlightField, final int timezoneOffset, final int from, final int resultCount, final int aggregationLimit, final boolean explain, final WebMapping... aggregationFields) {
        final QueryBuilder q = user_id == null || "en".equals(user_id) ? yq.getQueryBuilder() : constraint(yq.getQueryBuilder(), WebMapping.user_id_sxt, user_id);
        return query(indexName, q, postFilter, sort, highlightField, timezoneOffset, from, resultCount, aggregationLimit, explain, aggregationFields);
    }

    public FulltextIndex.Query query(final String indexName, final QueryBuilder queryBuilder, final YaCyQuery postFilter, final Sort sort, final WebMapping highlightField, final int timezoneOffset, final int from, final int resultCount, final int aggregationLimit, final boolean explain, final WebMapping... aggregationFields) {
        final FulltextIndex.Query query = new FulltextIndex.Query();
        for (int t = 0; t < 10; t++) try {

            // prepare request
            SearchRequestBuilder request = ElasticsearchClient.this.elasticsearchClient.prepareSearch(indexName);
            request
            .setExplain(explain)
            .setSearchType(SearchType.QUERY_THEN_FETCH)
            .setQuery(queryBuilder)
            .setSearchType(SearchType.DFS_QUERY_THEN_FETCH) // DFS_QUERY_THEN_FETCH is slower but provides stability of search results
            .setFrom(from)
            .setSize(resultCount);
            if (highlightField != null) {
                final HighlightBuilder hb = new HighlightBuilder().field(highlightField.getMapping().name()).preTags("").postTags("").fragmentSize(140);
                request.highlighter(hb);
            }
            //HighlightBuilder hb = new HighlightBuilder().field("message").preTags("<foo>").postTags("<bar>");
            if (postFilter != null) request.setPostFilter(postFilter.getQueryBuilder());
            request.clearRescorers();
            for (final WebMapping field: aggregationFields) {
                final String name = field.getMapping().name();
                request.addAggregation(AggregationBuilders.terms(name).field(name).minDocCount(1).size(aggregationLimit));
            }
            // apply sort
            request = sort.sort(request);
            // get response
            final SearchResponse response = request.execute().actionGet();
            final SearchHits searchHits = response.getHits();
            query.hitCount = (int) searchHits.getTotalHits();

            // evaluate search result
            //long totalHitCount = response.getHits().getTotalHits();
            final SearchHit[] hits = searchHits.getHits();
            query.results = new ArrayList<>(query.hitCount);
            query.explanations = new ArrayList<>(query.hitCount);
            query.highlights = new ArrayList<>(query.hitCount);
            for (final SearchHit hit: hits) {
                final Map<String, Object> map = hit.getSourceAsMap();
                if (!map.containsKey("id")) map.put("id", hit.getId());
                if (!map.containsKey("type")) map.put("type", hit.getType());
                query.results.add(WebMapping.sortMapKeys(map));
                query.highlights.add(hit.getHighlightFields());
                if (explain) {
                    final Explanation explanation = hit.getExplanation();
                    query.explanations.add(explanation.toString());
                } else {
                    query.explanations.add("");
                }
            }

            // evaluate aggregation
            // collect results: fields
            query.aggregations = new HashMap<>();
            for (final WebMapping field: aggregationFields) {
                final Terms fieldCounts = response.getAggregations().get(field.getMapping().name());
                final List<? extends Bucket> buckets = fieldCounts.getBuckets();
                // aggregate double-tokens (matching lowercase)
                final Map<String, Long> checkMap = new HashMap<>();
                for (final Bucket bucket: buckets) {
                    final String key = bucket.getKeyAsString().trim();
                    if (key.length() > 0) {
                        final String k = key.toLowerCase();
                        final Long v = checkMap.get(k);
                        checkMap.put(k, v == null ? bucket.getDocCount() : v + bucket.getDocCount());
                    }
                }
                final ArrayList<Map.Entry<String, Long>> list = new ArrayList<>(buckets.size());
                for (final Bucket bucket: buckets) {
                    final String key = bucket.getKeyAsString().trim();
                    if (key.length() > 0) {
                        final Long v = checkMap.remove(key.toLowerCase());
                        if (v == null) continue;
                        list.add(new AbstractMap.SimpleEntry<>(key, v));
                    }
                }
                query.aggregations.put(field.getMapping().name(), list);
                //if (field.equals("place_country")) {
                // special handling of country aggregation: add the country center as well
                //}
            }
            return query;

        } catch (NoNodeAvailableException | IllegalStateException | ClusterBlockException | SearchPhaseExecutionException e) {
            Logger.warn("ElasticsearchClient query failed with " + e.getMessage() + ", retrying attempt " + t + " ...", e);
            try {Thread.sleep(1000);} catch (final InterruptedException eee) {}
            connect();
            continue;
        }
        return query;
    }

    public int aggregationCount(final String indexName, final String fieldName, final String fieldValue, final String aggregationField) {
        return aggregation(indexName, fieldName, fieldValue, aggregationField).size();
    }

    public Map<String, Long> aggregation(final String indexName, final String fieldName, final String fieldValue, final String aggregationField) {

        final Map<String, Long> a = new HashMap<>();

        try {

            final SearchRequestBuilder request = this.elasticsearchClient.prepareSearch(indexName)
                    .setSearchType(SearchType.QUERY_THEN_FETCH)
                    .setFrom(0);

            final BoolQueryBuilder bFilter = QueryBuilders.boolQuery();
            bFilter.must(QueryBuilders.constantScoreQuery(QueryBuilders.constantScoreQuery(QueryBuilders.termQuery(fieldName, fieldValue))));
            request.setQuery(bFilter);
            request.addAggregation(AggregationBuilders.terms(aggregationField).field(aggregationField).minDocCount(1).size(1000));


            // Fielddata is disabled on text fields by default.
            // Set fielddata=true on [user_id_s] in order to load fielddata in memory by uninverting the inverted index.
            // Note that this can however use significant memory. Alternatively use a keyword field instead.


            // get response
            final SearchResponse response = request.execute().actionGet();
            final Aggregations agg = response.getAggregations();
            final Terms fieldCounts = agg.get(aggregationField);
            final List<? extends Bucket> buckets = fieldCounts.getBuckets();
            for (final Bucket bucket: buckets) {
                final String key = bucket.getKeyAsString().trim();
                if (key.length() > 0) {
                    final String k = key.toLowerCase();
                    final Long v = agg.get(k);
                    a.put(k, v == null ? bucket.getDocCount() : v + bucket.getDocCount());
                }
            }

        } catch (final Exception e) {
            Logger.error(e);
        }

        return a;
    }

    public List<Map<String, Object>> queryWithConstraints(final String indexName, final Map<String, String> constraints, final boolean latest) {
        final SearchRequestBuilder request = this.elasticsearchClient.prepareSearch(indexName)
                .setSearchType(SearchType.QUERY_THEN_FETCH)
                .setFrom(0);

        final BoolQueryBuilder bFilter = QueryBuilders.boolQuery();
        for (final Map.Entry<?,?> entry : constraints.entrySet()) {
            bFilter.must(QueryBuilders.constantScoreQuery(QueryBuilders.termQuery((String) entry.getKey(), ((String) entry.getValue()).toLowerCase())));
        }
        request.setQuery(bFilter);

        // get response
        final SearchResponse response = request.execute().actionGet();

        // evaluate search result
        final ArrayList<Map<String, Object>> result = new ArrayList<>();
        final SearchHit[] hits = response.getHits().getHits();
        for (final SearchHit hit: hits) {
            final Map<String, Object> map = hit.getSourceAsMap();
            result.add(map);
        }

        return result;
    }

    public List<Map<String, Object>> queryWithCompare(final String indexName, final String compvName, final Date compvValue) {
        final SearchRequestBuilder request = this.elasticsearchClient.prepareSearch(indexName)
                .setSearchType(SearchType.QUERY_THEN_FETCH)
                .setFrom(0);

        final BoolQueryBuilder bFilter = QueryBuilders.boolQuery();
        bFilter.must(QueryBuilders.constantScoreQuery(QueryBuilders.rangeQuery(compvName).gte(DateParser.iso8601MillisFormat.format(compvValue)).includeLower(true))); // value like "2014-10-21T20:03:12.963" "2022-03-30T02:03:03.214Z"
        request.setQuery(bFilter);

        // get response
        final SearchResponse response = request.execute().actionGet();

        // evaluate search result
        final ArrayList<Map<String, Object>> result = new ArrayList<>();
        final SearchHit[] hits = response.getHits().getHits();
        for (final SearchHit hit: hits) {
            final Map<String, Object> map = hit.getSourceAsMap();
            result.add(map);
        }

        return result;
    }

    public List<Map<String, Object>> queryWithCompare(final String indexName, final String facetName, final String facetValue, final String compvName, final Date compvValue) {
        final SearchRequestBuilder request = this.elasticsearchClient.prepareSearch(indexName)
                .setSearchType(SearchType.QUERY_THEN_FETCH)
                .setFrom(0);

        final BoolQueryBuilder bFilter = QueryBuilders.boolQuery();
        bFilter.must(QueryBuilders.constantScoreQuery(QueryBuilders.termQuery(facetName, facetValue)));
        bFilter.must(QueryBuilders.constantScoreQuery(QueryBuilders.rangeQuery(compvName).gt(DateParser.iso8601MillisFormat.format(compvValue)).includeLower(true)));
        request.setQuery(bFilter);

        // get response
        final SearchResponse response = request.execute().actionGet();

        // evaluate search result
        final ArrayList<Map<String, Object>> result = new ArrayList<>();
        final SearchHit[] hits = response.getHits().getHits();
        for (final SearchHit hit: hits) {
            final Map<String, Object> map = hit.getSourceAsMap();
            result.add(map);
        }

        return result;
    }

    public static void main(final String[] args) {
        try {
            final ElasticsearchClient client = new ElasticsearchClient(new String[]{"localhost:9300"}, "");

            // check access
            client.createIndexIfNotExists("test", 1, 0);
            System.out.println(client.count("test"));

            // upload a schema
            final String mapping = new String(Files.readAllBytes(Paths.get("conf/mappings/web.json")));
            client.setMapping("test", mapping);
            client.close();
        } catch (final IOException e) {
            Logger.warn("", e);
        }
    }

}
