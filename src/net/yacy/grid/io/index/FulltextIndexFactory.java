/**
 *  ElasticIndexFactory
 *  Copyright 04.03.2018 by Michael Peter Christen, @orbiterlab
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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.elasticsearch.client.transport.NoNodeAvailableException;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import eu.searchlab.tools.Classification;
import eu.searchlab.tools.JSONList;
import eu.searchlab.tools.Logger;

public class FulltextIndexFactory implements IndexFactory {

    public final static String PROTOCOL_PREFIX = "elastic://";

    private ElasticsearchClient elasticsearchClient = null;
    private String elasticsearchAddress;
    private String elasticsearchClusterName;
    private Index index;

    public FulltextIndexFactory(final String elasticsearchAddress, final String elasticsearchClusterName) throws IOException {
        if (elasticsearchAddress == null || elasticsearchAddress.length() == 0) throw new IOException("the elasticsearch Address must be given");

        this.elasticsearchAddress = elasticsearchAddress;
        this.elasticsearchClusterName = elasticsearchClusterName;

        // create elasticsearch connection
        this.elasticsearchClient = new ElasticsearchClient(new String[]{this.elasticsearchAddress}, this.elasticsearchClusterName.length() == 0 ? null : this.elasticsearchClusterName);
        if (this.elasticsearchClient.clusterReady()) {
        	Logger.info("Connected elasticsearch at " + this.elasticsearchAddress);
        } else {
        	Logger.info("no connection to elasticsearch. Attempting to reach a host in the network.");
            this.elasticsearchClient = new ElasticsearchClient(new String[]{"172.17.0.1:9300"}, null);
            if (this.elasticsearchClient.clusterReady()) {
            	Logger.info("Connected elasticsearch at " + this.elasticsearchAddress);
            } else {
            	Logger.info("no connection to elasticsearch.");
            }
        }

        final Path mappingsPath = Paths.get("conf","mappings");
        if (mappingsPath.toFile().exists()) {
            for (final File f: mappingsPath.toFile().listFiles()) {
                if (f.getName().endsWith(".json")) {
                    String indexName = f.getName();
                    indexName = indexName.substring(0, indexName.length() - 5); // cut off ".json"
                    try {
                        this.elasticsearchClient.createIndexIfNotExists(indexName, 1 /*shards*/, 1 /*replicas*/);
                        JSONObject mo = new JSONObject(new JSONTokener(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)));
                        mo = mo.getJSONObject("mappings").getJSONObject("_default_");
                        this.elasticsearchClient.setMapping(indexName, mo.toString());
                        Logger.info("initiated mapping for index " + indexName);
                    } catch (IOException | NoNodeAvailableException | JSONException e) {
                        this.elasticsearchClient = null; // index not available
                        Logger.warn("Failed creating mapping for index " + indexName, e);
                    }
                }
            }
        }

        // create index
        this.index = new Index() {

            @Override
            public IndexFactory checkConnection() throws IOException {
                return FulltextIndexFactory.this;
            }

            @Override
            public IndexFactory addBulk(final String indexName, final String typeName, final Map<String, JSONObject> objects) throws IOException {
                if (objects.size() > 0) {
                    final List<FulltextIndex.BulkEntry> entries = new ArrayList<>();
                    objects.forEach((id, obj) -> {
                        entries.add(new FulltextIndex.BulkEntry(id, typeName, null, obj.toMap()));
                    });
                    FulltextIndexFactory.this.elasticsearchClient.writeMapBulk(indexName, entries);
                }
                return FulltextIndexFactory.this;
            }

            @Override
            public IndexFactory add(final String indexName, final String typeName, final String id, final JSONObject object) throws IOException {
                FulltextIndexFactory.this.elasticsearchClient.writeMap(indexName, typeName, id, object.toMap());
                return FulltextIndexFactory.this;
            }

            @Override
            public boolean exist(final String indexName, final String id) throws IOException {
                return FulltextIndexFactory.this.elasticsearchClient.exist(indexName, id);
            }

            @Override
            public Set<String> existBulk(final String indexName, final Collection<String> ids) throws IOException {
                return FulltextIndexFactory.this.elasticsearchClient.existBulk(indexName, ids);
            }

            @Override
            public long count(final String indexName, final QueryLanguage language, final String query) throws IOException {
                final YaCyQuery yq = getQuery(language, query);
                return FulltextIndexFactory.this.elasticsearchClient.count(indexName, yq);
            }

            @Override
            public JSONObject query(final String indexName, final String id) throws IOException {
                final Map<String, Object> map = FulltextIndexFactory.this.elasticsearchClient.readMap(indexName, id);
                if (map == null) return null;
                return new JSONObject(map);
            }

            @Override
            public Map<String, JSONObject> queryBulk(final String indexName, final Collection<String> ids) throws IOException {
                final Map<String, Map<String, Object>> bulkresponse = FulltextIndexFactory.this.elasticsearchClient.readMapBulk(indexName, ids);
                final Map<String, JSONObject> response = new HashMap<>();
                bulkresponse.forEach((id, obj) -> response.put(id, new JSONObject(obj)));
                return response;
            }

            @Override
            public JSONList query(final String indexName, final QueryLanguage language, final String query, final int start, final int count) throws IOException {
                final YaCyQuery yq = getQuery(language, query);
                final ElasticsearchClient.Query q = FulltextIndexFactory.this.elasticsearchClient.query(indexName, yq, null, Sort.DEFAULT, null, 0, start, count, 0, false);
                final List<Map<String, Object>> results = q.results;
                final JSONList list = new JSONList();
                for (int hitc = 0; hitc < results.size(); hitc++) {
                    final Map<String, Object> map = results.get(hitc);
                    list.add(new JSONObject(map));
                }
                return list;
            }

            @Override
            public JSONObject query(final String indexName, final YaCyQuery yq, final YaCyQuery postFilter, final Sort sort, final WebMapping highlightField, final int timezoneOffset, final int from, final int resultCount, final int aggregationLimit, final boolean explain, final WebMapping... aggregationFields) throws IOException {
                final ElasticsearchClient.Query q = FulltextIndexFactory.this.elasticsearchClient.query(indexName, yq, postFilter, sort, highlightField, timezoneOffset, from, resultCount, aggregationLimit, explain, aggregationFields);
                final JSONObject queryResult = new JSONObject(true);

                final int hitCount = q.hitCount;
                try {
                        queryResult.put("hitCount", hitCount);

                        final List<Map<String, Object>> results = q.results;
                        final JSONList list = new JSONList();
                        for (int hitc = 0; hitc < results.size(); hitc++) {
                            final Map<String, Object> map = results.get(hitc);
                            list.add(new JSONObject(map));
                        }
                        queryResult.put("results", list);

                        final List<String> explanations = q.explanations;
                        queryResult.put("explanations", explanations);
                } catch (final JSONException e) {
                	Logger.debug("json exception", e);
                }
                return queryResult;
            }

            @Override
            public boolean delete(final String indexName, final String typeName, final String id) throws IOException {
                return FulltextIndexFactory.this.elasticsearchClient.delete(indexName, typeName, id);
            }

            @Override
            public long delete(final String indexName, final QueryLanguage language, final String query) throws IOException {
                final YaCyQuery yq = getQuery(language, query);
                return FulltextIndexFactory.this.elasticsearchClient.deleteByQuery(indexName, yq);
            }

            @Override
            public void refresh(final String indexName) {
                FulltextIndexFactory.this.elasticsearchClient.refresh(indexName);
            }

            @Override
            public void close() {
            }

            private YaCyQuery getQuery(final QueryLanguage language, final String query) {
                return new YaCyQuery(query, null, Classification.ContentDomain.ALL, 0);
            }

        };
    }

    public ElasticsearchClient getClient() {
        return this.elasticsearchClient;
    }

    @Override
    public Index getIndex() throws IOException {
        return this.index;
    }

    @Override
    public void close() {
        this.elasticsearchClient.close();
    }


}
