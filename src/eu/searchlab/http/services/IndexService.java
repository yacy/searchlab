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

import java.util.List;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

import eu.searchlab.Searchlab;
import eu.searchlab.http.Service;
import eu.searchlab.http.ServiceRequest;
import eu.searchlab.http.ServiceResponse;
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
 * http://127.0.0.1:8400/en/api/index.json?query=where
 */
public class IndexService extends AbstractService implements Service {

    @Override
    public String[] getPaths() {
        return new String[] {"/api/index.json"};
    }

    @Override
    public ServiceResponse serve(final ServiceRequest serviceRequest) {

        // evaluate request parameter
        final String indexName = serviceRequest.get("index", GridIndex.DEFAULT_INDEXNAME_WEB);
        final String id = serviceRequest.get("id", "");
        final String q = serviceRequest.get("query", "");
        final JSONObject json = new JSONObject(true);

        try {
            if (indexName.length() > 0 && id.length() > 0) {
                json.put("title", "Search for id = " + id);
                json.put("description", "Search for id = " + id);
                json.put("startIndex", "0");
                json.put("searchTerms", id);
                json.put("itemsPerPage", "1");
                json.put("pages", "1");
                json.put("page", "1");

                final Map<String, Object> map = Searchlab.ec.readMap(indexName, id);
                final JSONList list = new JSONList();
                if (map == null) {
                    json.put("totalResults",  "0");
                    json.put("itemsCount", 0);
                    json.put("items", list.toArray());
                } else {
                    final WebDocument doc = new WebDocument(map); // this is an instance of JSONObject
                    list.add(doc);
                    json.put("totalResults",  "1");
                    json.put("itemsCount", 1);
                    json.put("items", list.toArray());
                }
            } else if (indexName.length() > 0 && q.length() > 0) {
                final int startRecord = serviceRequest.get("startRecord", serviceRequest.get("start", 0));
                final Classification.ContentDomain contentdom = Classification.ContentDomain.contentdomParser(serviceRequest.get("contentdom", "all"));
                String collection = serviceRequest.get("collection", ""); // important: call arguments may overrule parsed collection values if not empty. This can be used for authentified indexes!
                collection = collection.replace(',', '|'); // to be compatible with the site-operator of GSA, we use a vertical pipe symbol here to divide collections.
                final String[] collections = collection.length() == 0 ? new String[0] : collection.split("\\|");
                final int timezoneOffset = serviceRequest.get("timezoneOffset", -1);
                final Sort sort = new Sort(serviceRequest.get("sort", ""));
                final int itemsPerPage = serviceRequest.get("itemsPerPage", serviceRequest.get("maximumRecords", serviceRequest.get("rows", serviceRequest.get("num", 10))));

                final YaCyQuery yq = new YaCyQuery(q, collections, contentdom, timezoneOffset);
                final ElasticsearchClient.Query query = Searchlab.ec.query(
                        System.getProperties().getProperty("grid.elasticsearch.indexName.web", GridIndex.DEFAULT_INDEXNAME_WEB),
                        yq, null, sort, WebMapping.text_t, timezoneOffset, startRecord, itemsPerPage, 1, false); // no facet computation here

                final JSONList items = new JSONList();
                final List<Map<String, Object>> qr = query.results;
                for (int hitc = 0; hitc < qr.size(); hitc++) {
                    final WebDocument doc = new WebDocument(qr.get(hitc)); // this is an instance of JSONObject
                    items.add(doc);
                }

                json.put("title", "Search for " + q);
                json.put("description", "Search for " + q);
                json.put("startIndex", "" + startRecord);
                json.put("searchTerms", q);
                json.put("totalResults", Integer.toString(query.hitCount));
                json.put("itemsPerPage", "" + itemsPerPage);
                json.put("pages", "" + ((query.hitCount / itemsPerPage) + 1));
                json.put("page", "" + ((startRecord / itemsPerPage) + 1)); // the current result page, first page has number 1

                json.put("itemsCount", items.length());
                json.put("items", items.toArray());
            }
        } catch (final JSONException e) {e.printStackTrace();}
        return new ServiceResponse(json);
    }

}
