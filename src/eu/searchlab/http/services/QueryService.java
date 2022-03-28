/**
 *  QueryService
 *  Copyright 27.03.2022 by Michael Peter Christen, @orbiterlab
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


package eu.searchlab.http.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

import eu.searchlab.Searchlab;
import eu.searchlab.http.Service;
import eu.searchlab.tools.Classification;
import eu.searchlab.tools.JSONList;
import net.yacy.grid.io.index.ElasticsearchClient;
import net.yacy.grid.io.index.GridIndex;
import net.yacy.grid.io.index.Sort;
import net.yacy.grid.io.index.WebDocument;
import net.yacy.grid.io.index.WebMapping;
import net.yacy.grid.io.index.YaCyQuery;

/**
 * QueryService
 * returns a list of JSON objects that are present in the index.
 *
 * http://127.0.0.1:8400/en/api/query.json?query=where
 */
public class QueryService extends AbstractService implements Service {

    @Override
    public String[] getPaths() {
        return new String[] {"/api/query.json"};
    }

    @Override
    public Type getType() {
        return Service.Type.OBJECT;
    }

    @Override
    public JSONObject serveObject(final JSONObject call) {

        // evaluate request parameter
        final String indexName = call.optString("index", GridIndex.DEFAULT_INDEXNAME_WEB);
        final String id = call.optString("id", "");
        final String query = call.optString("query", "");
        final JSONObject json = new JSONObject(true);

        try {
            if (indexName.length() > 0 && id.length() > 0) {
                final Map<String, Object> map = Searchlab.ec.readMap(indexName, id);

                final JSONList list = new JSONList();
                if (map == null) {
                    json.put("count", 0);
                    json.put("list", list.toArray());
                } else {
                    final WebDocument doc = new WebDocument(map); // this is an instance of JSONObject
                    list.add(doc);
                    json.put("count", 1);
                    json.put("list", list.toArray());
                }
            } else if (indexName.length() > 0 && query.length() > 0) {
                final int startRecord = call.optInt("startRecord", call.optInt("start", 0));
                final Classification.ContentDomain contentdom = Classification.ContentDomain.contentdomParser(call.optString("contentdom", "all"));
                String collection = call.optString("collection", ""); // important: call arguments may overrule parsed collection values if not empty. This can be used for authentified indexes!
                collection = collection.replace(',', '|'); // to be compatible with the site-operator of GSA, we use a vertical pipe symbol here to divide collections.
                final String[] collections = collection.length() == 0 ? new String[0] : collection.split("\\|");
                final int timezoneOffset = call.optInt("timezoneOffset", -1);
                final Sort sort = new Sort(call.optString("sort", ""));
                final int itemsPerPage = call.optInt("itemsPerPage", call.optInt("maximumRecords", call.optInt("rows", call.optInt("num", 10))));
                final int facetLimit = call.optInt("facetLimit", 10);
                final String facetFields = call.optString("facetFields", YaCyQuery.FACET_DEFAULT_PARAMETER);
                final List<WebMapping> facetFieldMapping = new ArrayList<>();
                for (final String s: facetFields.split(",")) facetFieldMapping.add(WebMapping.valueOf(s));

                final YaCyQuery yq = new YaCyQuery(query, collections, contentdom, timezoneOffset);
                final ElasticsearchClient.Query query1 = Searchlab.ec.query(
                        System.getProperties().getProperty("grid.elasticsearch.indexName.web", GridIndex.DEFAULT_INDEXNAME_WEB),
                        yq, null, sort, WebMapping.text_t, timezoneOffset, startRecord, itemsPerPage, facetLimit, false,
                        facetFieldMapping.toArray(new WebMapping[facetFieldMapping.size()]));

                final JSONList list = new JSONList();
                final List<Map<String, Object>> qr = query1.results;
                for (int hitc = 0; hitc < qr.size(); hitc++) {
                    final WebDocument doc = new WebDocument(qr.get(hitc)); // this is an instance of JSONObject
                    list.add(doc);
                }

                json.put("count", list.length());
                json.put("list", list.toArray());
            }
        } catch (final JSONException e) {e.printStackTrace();}
        return json;
    }

}