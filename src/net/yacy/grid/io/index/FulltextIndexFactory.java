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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.searchlab.tools.Classification;
import eu.searchlab.tools.JSONList;

public class FulltextIndexFactory implements IndexFactory {

    private static final Logger log = LoggerFactory.getLogger(FulltextIndexFactory.class);

    public final static String PROTOCOL_PREFIX = "elastic://";

    private ElasticsearchClient elasticsearchClient = null;
    private String elasticsearchAddress;
    private String elasticsearchClusterName;
    private Index index;

    public FulltextIndexFactory(String elasticsearchAddress, String elasticsearchClusterName) throws IOException {
        if (elasticsearchAddress == null || elasticsearchAddress.length() == 0) throw new IOException("the elasticsearch Address must be given");

        this.elasticsearchAddress = elasticsearchAddress;
        this.elasticsearchClusterName = elasticsearchClusterName;

        // create elasticsearch connection
        this.elasticsearchClient = new ElasticsearchClient(new String[]{this.elasticsearchAddress}, this.elasticsearchClusterName.length() == 0 ? null : this.elasticsearchClusterName);
        log.info("Connected elasticsearch at " + this.elasticsearchAddress);

        Path mappingsPath = Paths.get("conf","mappings");
        if (mappingsPath.toFile().exists()) {
            for (File f: mappingsPath.toFile().listFiles()) {
                if (f.getName().endsWith(".json")) {
                    String indexName = f.getName();
                    indexName = indexName.substring(0, indexName.length() - 5); // cut off ".json"
                    try {
                        this.elasticsearchClient.createIndexIfNotExists(indexName, 1 /*shards*/, 1 /*replicas*/);
                        JSONObject mo = new JSONObject(new JSONTokener(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)));
                        mo = mo.getJSONObject("mappings").getJSONObject("_default_");
                        this.elasticsearchClient.setMapping(indexName, mo.toString());
                        log.info("initiated mapping for index " + indexName);
                    } catch (IOException | NoNodeAvailableException | JSONException e) {
                        this.elasticsearchClient = null; // index not available
                        log.info("Failed creating mapping for index " + indexName, e);
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
            public IndexFactory addBulk(String indexName, String typeName, final Map<String, JSONObject> objects) throws IOException {
                if (objects.size() > 0) {
                    List<FulltextIndex.BulkEntry> entries = new ArrayList<>();
                    objects.forEach((id, obj) -> {
                        entries.add(new FulltextIndex.BulkEntry(id, typeName, null, obj.toMap()));
                    });
                    FulltextIndexFactory.this.elasticsearchClient.writeMapBulk(indexName, entries);
                }
                return FulltextIndexFactory.this;
            }

            @Override
            public IndexFactory add(String indexName, String typeName, String id, JSONObject object) throws IOException {
                FulltextIndexFactory.this.elasticsearchClient.writeMap(indexName, typeName, id, object.toMap());
                return FulltextIndexFactory.this;
            }

            @Override
            public boolean exist(String indexName, String id) throws IOException {
                return FulltextIndexFactory.this.elasticsearchClient.exist(indexName, id);
            }

            @Override
            public Set<String> existBulk(String indexName, Collection<String> ids) throws IOException {
                return FulltextIndexFactory.this.elasticsearchClient.existBulk(indexName, ids);
            }

            @Override
            public long count(String indexName, QueryLanguage language, String query) throws IOException {
                YaCyQuery yq = getQuery(language, query);
                return FulltextIndexFactory.this.elasticsearchClient.count(indexName, yq);
            }

            @Override
            public JSONObject query(String indexName, String id) throws IOException {
                Map<String, Object> map = FulltextIndexFactory.this.elasticsearchClient.readMap(indexName, id);
                if (map == null) return null;
                return new JSONObject(map);
            }

            @Override
            public Map<String, JSONObject> queryBulk(String indexName, Collection<String> ids) throws IOException {
                Map<String, Map<String, Object>> bulkresponse = FulltextIndexFactory.this.elasticsearchClient.readMapBulk(indexName, ids);
                Map<String, JSONObject> response = new HashMap<>();
                bulkresponse.forEach((id, obj) -> response.put(id, new JSONObject(obj)));
                return response;
            }

            @Override
            public JSONList query(String indexName, QueryLanguage language, String query, int start, int count) throws IOException {
                YaCyQuery yq = getQuery(language, query);
                ElasticsearchClient.Query q = FulltextIndexFactory.this.elasticsearchClient.query(indexName, yq, null, Sort.DEFAULT, null, 0, start, count, 0, false);
                List<Map<String, Object>> results = q.results;
                JSONList list = new JSONList();
                for (int hitc = 0; hitc < results.size(); hitc++) {
                    Map<String, Object> map = results.get(hitc);
                    list.add(new JSONObject(map));
                }
                return list;
            }

            @Override
            public JSONObject query(final String indexName, final YaCyQuery yq, final YaCyQuery postFilter, final Sort sort, final WebMapping highlightField, int timezoneOffset, int from, int resultCount, int aggregationLimit, boolean explain, WebMapping... aggregationFields) throws IOException {
                ElasticsearchClient.Query q = FulltextIndexFactory.this.elasticsearchClient.query(indexName, yq, postFilter, sort, highlightField, timezoneOffset, from, resultCount, aggregationLimit, explain, aggregationFields);
                JSONObject queryResult = new JSONObject(true);

                int hitCount = q.hitCount;
                try {
                        queryResult.put("hitCount", hitCount);

                        List<Map<String, Object>> results = q.results;
                        JSONList list = new JSONList();
                        for (int hitc = 0; hitc < results.size(); hitc++) {
                            Map<String, Object> map = results.get(hitc);
                            list.add(new JSONObject(map));
                        }
                        queryResult.put("results", list);

                        List<String> explanations = q.explanations;
                        queryResult.put("explanations", explanations);
                } catch (JSONException e) {
                    log.debug("json exception", e);
                }
                return queryResult;
            }

            @Override
            public boolean delete(String indexName, String typeName, String id) throws IOException {
                return FulltextIndexFactory.this.elasticsearchClient.delete(indexName, typeName, id);
            }

            @Override
            public long delete(String indexName, QueryLanguage language, String query) throws IOException {
                YaCyQuery yq = getQuery(language, query);
                return FulltextIndexFactory.this.elasticsearchClient.deleteByQuery(indexName, yq);
            }

            @Override
            public void refresh(String indexName) {
                FulltextIndexFactory.this.elasticsearchClient.refresh(indexName);
            }

            @Override
            public void close() {
            }

            private YaCyQuery getQuery(QueryLanguage language, String query) {
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
