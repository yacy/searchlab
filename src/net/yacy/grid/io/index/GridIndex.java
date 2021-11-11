/**
 *  GridIndex
 *  Copyright 5.3.2018 by Michael Peter Christen, @orbiterlab
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

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.searchlab.tools.JSONList;

public class GridIndex implements Index {

    private static final Logger log = LoggerFactory.getLogger(GridIndex.class);

    // Default index names that are (possibly) overwritten using the attributes
    //     grid.elasticsearch.indexName.crawlstart,
    //     grid.elasticsearch.indexName.crawler,
    //     grid.elasticsearch.indexName.query,
    //     grid.elasticsearch.indexName.web
    // in conf/config.properties. Please ensure that the configuration is present in all grid components.

    public final static String DEFAULT_INDEXNAME_CRAWLSTART = "crawlstart";
    public final static String DEFAULT_INDEXNAME_CRAWLER    = "crawler";
    public final static String DEFAULT_INDEXNAME_QUERY      = "query";
    public final static String DEFAULT_INDEXNAME_WEB        = "web";

    // Default index names that are (possibly) overwritten using the attribute
    //     grid.elasticsearch.typeName
    // in conf/config.properties.
    // THIS IS A TEMPORARY ATTRIBUTE AND WILL BE REPLACED WITH "_doc" IN FUTURE ELASTIC VERSIONS
    // STARTING WITH ELASTICSEARCH 8.x
    public final static String DEFAULT_TYPENAME             = "web";

    private ElasticIndexFactory elasticIndexFactory;

    private String elastic_address;
    private boolean shallRun;

    public GridIndex() {
        this.elastic_address = null;
        this.elasticIndexFactory = null;
        this.shallRun = true;
    }

    public boolean isConnected() {
        return this.elastic_address != null && this.elasticIndexFactory != null;
    }

    public boolean connectElasticsearch(String address) {
        if (!address.startsWith(ElasticIndexFactory.PROTOCOL_PREFIX)) return false;
        address = address.substring(ElasticIndexFactory.PROTOCOL_PREFIX.length());
        int p = address.indexOf('/');
        String cluster = "";
        if (p >= 0) {
            cluster = address.substring(p + 1);
            address = address.substring(0, p);
        }
        if (address.length() == 0) return false;

        // if we reach this point, we try (forever) until we get a connection to elasticsearch
        loop: while (this.shallRun) {
            try {
                this.elasticIndexFactory = new ElasticIndexFactory(address, cluster);
                log.info("Index/Client: connected to elasticsearch at " + address);
                this.elastic_address = address;
                return true;
            } catch (IOException e) {
                log.info("Index/Client: trying to connect to elasticsearch at " + address + " failed", e);
                try {Thread.sleep(5000);} catch (InterruptedException e1) {}
                continue loop;
            }
        }
        return false;
    }

    /**
     * Getting the elastic client:
     * this should considered as a low-level function that only MCP-internal classes may call.
     * All other packages must use a "Index" object.
     * @return
     */
    public ElasticsearchClient getElasticClient() {
        if (this.elasticIndexFactory == null && this.elastic_address != null) {
            connectElasticsearch(this.elastic_address); // try to connect again..
        }
        return this.elasticIndexFactory.getClient();
    }

    public Index getElasticIndex() throws IOException {
        if (this.elasticIndexFactory == null && this.elastic_address != null) {
            connectElasticsearch(this.elastic_address); // try to connect again..
        }
        return this.elasticIndexFactory.getIndex();
    }

    @Override
    public IndexFactory checkConnection() throws IOException {
        if (this.elasticIndexFactory == null && this.elastic_address != null) {
            connectElasticsearch(this.elastic_address); // try to connect again..
        }
        if (this.elasticIndexFactory != null) {
            return this.elasticIndexFactory;
        }
        throw new IOException("Index/Client: add mcp service: no factory found!");
    }

    @Override
    public IndexFactory add(String indexName, String typeName, String id, JSONObject object) throws IOException {
        if (this.elasticIndexFactory == null && this.elastic_address != null) {
            connectElasticsearch(this.elastic_address); // try to connect again..
        }
        if (this.elasticIndexFactory != null) try {
            this.elasticIndexFactory.getIndex().add(indexName, typeName, id, object);
            //Data.logger.info("Index/Client: add elastic service '" + this.elasticIndexFactory.getConnectionURL() + "', object with id:" + id);
            return this.elasticIndexFactory;
        } catch (IOException e) {
            log.debug("Index/Client: add elastic service '" + this.elastic_address + "', elastic fail", e);
        }
        throw new IOException("Index/Client: add mcp service: no factory found!");
    }

    @Override
    public IndexFactory addBulk(String indexName, String typeName, final Map<String, JSONObject> objects) throws IOException {
        if (this.elasticIndexFactory == null && this.elastic_address != null) {
            connectElasticsearch(this.elastic_address); // try to connect again..
        }
        if (this.elasticIndexFactory != null) try {
            this.elasticIndexFactory.getIndex().addBulk(indexName, typeName, objects);
            //Data.logger.info("Index/Client: add elastic service '" + this.elasticIndexFactory.getConnectionURL() + "', object with id:" + id);
            return this.elasticIndexFactory;
        } catch (IOException e) {
            log.debug("Index/Client: add elastic service '" + this.elastic_address + "', elastic fail", e);
        }
        throw new IOException("Index/Client: add mcp service: no factory found!");
    }

    @Override
    public boolean exist(String indexName, String id) throws IOException {
        if (this.elasticIndexFactory == null && this.elastic_address != null) {
            connectElasticsearch(this.elastic_address); // try to connect again..
        }
        if (this.elasticIndexFactory != null) try {
            boolean exist = this.elasticIndexFactory.getIndex().exist(indexName, id);
            //Data.logger.info("Index/Client: exist elastic service '" + this.elasticIndexFactory.getConnectionURL() + "', object with id:" + id);
            return exist;
        } catch (IOException e) {
            log.debug("Index/Client: exist elastic service '" + this.elastic_address + "', elastic fail", e);
        }
        throw new IOException("Index/Client: exist mcp service: no factory found!");
    }

    @Override
    public Set<String> existBulk(String indexName, Collection<String> ids) throws IOException {
        if (this.elasticIndexFactory == null && this.elastic_address != null) {
            connectElasticsearch(this.elastic_address); // try to connect again..
        }
        if (this.elasticIndexFactory != null) try {
            Set<String> exist = this.elasticIndexFactory.getIndex().existBulk(indexName, ids);
            //Data.logger.info("Index/Client: exist elastic service '" + this.elasticIndexFactory.getConnectionURL() + "', object with id:" + id);
            return exist;
        } catch (IOException e) {
            log.debug("Index/Client: existBulk elastic service '" + this.elastic_address + "', elastic fail", e);
        }
        throw new IOException("Index/Client: existBulk mcp service: no factory found!");
    }

    @Override
    public long count(String indexName, QueryLanguage language, String query) throws IOException {
        if (this.elasticIndexFactory == null && this.elastic_address != null) {
            connectElasticsearch(this.elastic_address); // try to connect again..
        }
        if (this.elasticIndexFactory != null) try {
            long count = this.elasticIndexFactory.getIndex().count(indexName, language, query);
            //Data.logger.info("Index/Client: count elastic service '" + this.elasticIndexFactory.getConnectionURL() + "', object with query:" + query);
            return count;
        } catch (IOException e) {
            log.debug("Index/Client: count elastic service '" + this.elastic_address + "', elastic fail", e);
        }
        throw new IOException("Index/Client: count mcp service: no factory found!");
    }

    @Override
    public JSONObject query(String indexName, String id) throws IOException {
        if (this.elasticIndexFactory == null && this.elastic_address != null) {
            connectElasticsearch(this.elastic_address); // try to connect again..
        }
        if (this.elasticIndexFactory != null) try {
            JSONObject json = this.elasticIndexFactory.getIndex().query(indexName, id);
            //Data.logger.info("Index/Client: query elastic service '" + this.elasticIndexFactory.getConnectionURL() + "', object with id:" + id);
            return json;
        } catch (IOException e) {
            log.debug("Index/Client: query/3 elastic service '" + this.elastic_address + "', elastic fail", e);
        }
        throw new IOException("Index/Client: query/3 mcp service: no factory found!");
    }

    @Override
    public Map<String, JSONObject> queryBulk(String indexName, Collection<String> ids) throws IOException {
        if (this.elasticIndexFactory == null && this.elastic_address != null) {
            connectElasticsearch(this.elastic_address); // try to connect again..
        }
        if (this.elasticIndexFactory != null) try {
            Map<String, JSONObject> map = this.elasticIndexFactory.getIndex().queryBulk(indexName, ids);
            //Data.logger.info("Index/Client: query elastic service '" + this.elasticIndexFactory.getConnectionURL() + "', object with id:" + id);
            return map;
        } catch (IOException e) {
            log.debug("Index/Client: queryBulk/3 elastic service '" + this.elastic_address + "', elastic fail", e);
        }
        throw new IOException("Index/Client: queryBulk/3 mcp service: no factory found!");
    }

    @Override
    public JSONList query(String indexName, QueryLanguage language, String query, int start, int count) throws IOException {
        if (this.elasticIndexFactory == null && this.elastic_address != null) {
            connectElasticsearch(this.elastic_address); // try to connect again..
        }
        if (this.elasticIndexFactory != null) try {
            JSONList list = this.elasticIndexFactory.getIndex().query(indexName, language, query, start, count);
            //Data.logger.info("Index/Client: query elastic service '" + this.elasticIndexFactory.getConnectionURL() + "', object with query:" + query);
            return list;
        } catch (IOException e) {
            log.debug("Index/Client: query/6 elastic service '" + this.elastic_address + "', elastic fail", e);
        }
        throw new IOException("Index/Client: query/6 mcp service: no factory found!");
    }

    @Override
    public JSONObject query(final String indexName, final QueryBuilder queryBuilder, final QueryBuilder postFilter, final Sort sort, final HighlightBuilder hb, int timezoneOffset, int from, int resultCount, int aggregationLimit, boolean explain, WebMapping... aggregationFields) throws IOException {
        if (this.elasticIndexFactory == null && this.elastic_address != null) {
            connectElasticsearch(this.elastic_address); // try to connect again..
        }
        if (this.elasticIndexFactory != null) try {
        	JSONObject queryResult = this.elasticIndexFactory.getIndex().query(indexName, queryBuilder, postFilter, sort, hb, timezoneOffset, from, resultCount, aggregationLimit, explain, aggregationFields);
            //Data.logger.info("Index/Client: query elastic service '" + this.elasticIndexFactory.getConnectionURL() + "', object with query:" + query);
            return queryResult;
        } catch (IOException e) {
            log.debug("Index/Client: query/12 elastic service '" + this.elastic_address + "', elastic fail", e);
        }
        throw new IOException("Index/Client: query/12 mcp service: no factory found!");
    }

    @Override
    public boolean delete(String indexName, String typeName, String id) throws IOException {
        if (this.elasticIndexFactory == null && this.elastic_address != null) {
            connectElasticsearch(this.elastic_address); // try to connect again..
        }
        if (this.elasticIndexFactory != null) try {
            boolean deleted = this.elasticIndexFactory.getIndex().delete(indexName, typeName, id);
            //Data.logger.info("Index/Client: delete elastic service '" + this.elasticIndexFactory.getConnectionURL() + "', object with id:" + id);
            return deleted;
        } catch (IOException e) {
            log.debug("Index/Client: delete elastic service '" + this.elastic_address + "', elastic fail", e);
        }
        throw new IOException("Index/Client: delete mcp service: no factory found!");
    }

    @Override
    public long delete(String indexName, QueryLanguage language, String query) throws IOException {
        if (this.elasticIndexFactory == null && this.elastic_address != null) {
            connectElasticsearch(this.elastic_address); // try to connect again..
        }
        if (this.elasticIndexFactory != null) try {
            long deleted = this.elasticIndexFactory.getIndex().delete(indexName, language, query);
            //Data.logger.info("Index/Client: delete elastic service '" + this.elasticIndexFactory.getConnectionURL() + "', object with query:" + query);
            return deleted;
        } catch (IOException e) {
            log.debug("Index/Client: delete elastic service '" + this.elastic_address + "', elastic fail", e);
        }
        throw new IOException("Index/Client: delete mcp service: no factory found!");
    }

    @Override
    public void refresh(String indexName) {
        if (this.elasticIndexFactory == null) try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {}
        else try {
            this.elasticIndexFactory.getIndex().refresh(indexName);
        } catch (IOException e) {}
    }

    @Override
    public void close() {
        this.shallRun = false;
        if (this.elasticIndexFactory != null) this.elasticIndexFactory.close();
    }

}
