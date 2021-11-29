/**
 *  YaCySearchService
 *  Copyright 08.11.2021 by Michael Peter Christen, @orbiterlab
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
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import eu.searchlab.Searchlab;
import eu.searchlab.http.Service;
import eu.searchlab.tools.Classification;
import eu.searchlab.tools.DateParser;
import net.yacy.grid.io.index.ElasticsearchClient;
import net.yacy.grid.io.index.GridIndex;
import net.yacy.grid.io.index.Sort;
import net.yacy.grid.io.index.WebDocument;
import net.yacy.grid.io.index.WebMapping;
import net.yacy.grid.io.index.YaCyQuery;

// http://localhost:8400/api/yacysearch.json?query=help
public class YaCySearchService extends AbstractService implements Service {

    @Override
    public String[] getPaths() {
        return new String[] {"/api/yacysearch.json", ""};
    }

    @Override
    public Type getType() {
        return Service.Type.OBJECT;
    }

    @Override
    public JSONObject serveObject(JSONObject call) {

        final String path = call.optString("PATH", "");
        final int p = path.lastIndexOf("/graph/");

        String callback = call.optString("callback", "");
        boolean jsonp = callback != null && callback.length() > 0;
        boolean minified = call.optBoolean("minified", false);
        boolean explain = call.optBoolean("explain", false);
        String q = call.optString("query", "");
        Classification.ContentDomain contentdom =  Classification.ContentDomain.contentdomParser(call.optString("contentdom", "all"));
        String collection = call.optString("collection", ""); // important: call arguments may overrule parsed collection values if not empty. This can be used for authentified indexes!
        collection = collection.replace(',', '|'); // to be compatible with the site-operator of GSA, we use a vertical pipe symbol here to divide collections.
        String[] collections = collection.length() == 0 ? new String[0] : collection.split("\\|");
        int maximumRecords = call.optInt("maximumRecords", call.optInt("rows", call.optInt("num", 10)));
        int startRecord = call.optInt("startRecord", call.optInt("start", 0));
        //int meanCount = call.opt("meanCount", 5);
        int timezoneOffset = call.optInt("timezoneOffset", -1);
        //String nav = call.opt("nav", "");
        //String prefermaskfilter = call.opt("prefermaskfilter", "");
        //String constraint = call.opt("constraint", "");
        int facetLimit = call.optInt("facetLimit", 10);
        String facetFields = call.optString("facetFields", YaCyQuery.FACET_DEFAULT_PARAMETER);
        List<WebMapping> facetFieldMapping = new ArrayList<>();
        for (String s: facetFields.split(",")) facetFieldMapping.add(WebMapping.valueOf(s));
        Sort sort = new Sort(call.optString("sort", ""));

        YaCyQuery yq = new YaCyQuery(q, collections, contentdom, timezoneOffset);
        ElasticsearchClient.Query query = Searchlab.ec.query(
                System.getProperties().getProperty("grid.elasticsearch.indexName.web", GridIndex.DEFAULT_INDEXNAME_WEB),
                yq, null, sort, WebMapping.text_t, timezoneOffset, startRecord, maximumRecords, facetLimit, explain,
                facetFieldMapping.toArray(new WebMapping[facetFieldMapping.size()]));

        JSONObject json = new JSONObject(true);
        try {
            JSONArray channels = new JSONArray();
            json.put("channels", channels);
            JSONObject channel = new JSONObject(true);
            channels.put(channel);
            JSONArray items = new JSONArray();
            channel.put("title", "Search for " + q);
            channel.put("description", "Search for " + q);
            channel.put("startIndex", "" + startRecord);
            channel.put("itemsPerPage", "" + items.length());
            channel.put("searchTerms", q);
            channel.put("totalResults", Integer.toString(query.hitCount));
            channel.put("items", items);

            List<Map<String, Object>> result = query.results;
            List<String> explanations = query.explanations;
            for (int hitc = 0; hitc < result.size(); hitc++) {
                WebDocument doc = new WebDocument(result.get(hitc));
                JSONObject hit = new JSONObject(true);
                String titleString = doc.getTitle();
                String link = doc.getLink();
                if (Classification.ContentDomain.IMAGE == contentdom) {
                    hit.put("url", link); // the url before we extract the link
                    link = doc.pickImage((String) link);
                    hit.put("icon", link);
                    hit.put("image", link);
                }
                String snippet = doc.getSnippet(query.highlights.get(hitc), yq);
                Date last_modified_date = doc.getDate();
                int size = doc.getSize();
                int sizekb = size / 1024;
                int sizemb = sizekb / 1024;
                String size_string = sizemb > 0 ? (Integer.toString(sizemb) + " mbyte") : sizekb > 0 ? (Integer.toString(sizekb) + " kbyte") : (Integer.toString(size) + " byte");
                String host = doc.getHost();
                hit.put("title", titleString);
                hit.put("link", link.toString());
                hit.put("description", snippet);
                hit.put("pubDate", DateParser.formatRFC1123(last_modified_date));
                hit.put("size", Integer.toString(size));
                hit.put("sizename", size_string);
                hit.put("host", host);
                if (explain) {
                    hit.put("explanation", explanations.get(hitc));
                }
                items.put(hit);
            };
            JSONArray navigation = new JSONArray();
            channel.put("navigation", navigation);

            Map<String, List<Map.Entry<String, Long>>> aggregations = query.aggregations;
            for (Map.Entry<String, List<Map.Entry<String, Long>>> fe: aggregations.entrySet()) {
                String facetname = fe.getKey();
                WebMapping mapping = WebMapping.valueOf(facetname);
                JSONObject facetobject = new JSONObject(true);
                facetobject.put("facetname", mapping.getMapping().getFacetname());
                facetobject.put("displayname", mapping.getMapping().getDisplayname());
                facetobject.put("type", mapping.getMapping().getFacettype());
                facetobject.put("min", "0");
                facetobject.put("max", "0");
                facetobject.put("mean", "0");
                facetobject.put("count", fe.getValue().size());
                JSONArray elements = new JSONArray();
                facetobject.put("elements", elements);
                for (Map.Entry<String, Long> element: fe.getValue()) {
                    JSONObject elementEntry = new JSONObject(true);
                    elementEntry.put("name", element.getKey());
                    elementEntry.put("count", element.getValue().toString());
                    elementEntry.put("modifier", mapping.getMapping().getFacetmodifier() + ":" + element.getKey());
                    elements.put(elementEntry);
                }
                navigation.put(facetobject);
            }
        } catch (JSONException e) {e.printStackTrace();}
        return json;
    }

}
