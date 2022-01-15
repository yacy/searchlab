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

        // evaluate request parameter
        final String callback = call.optString("callback", "");
        final boolean minified = call.optBoolean("minified", false);
        final boolean explain = call.optBoolean("explain", false);
        final String q = call.optString("query", "");
        final Classification.ContentDomain contentdom =  Classification.ContentDomain.contentdomParser(call.optString("contentdom", "all"));
        String collection = call.optString("collection", ""); // important: call arguments may overrule parsed collection values if not empty. This can be used for authentified indexes!
        collection = collection.replace(',', '|'); // to be compatible with the site-operator of GSA, we use a vertical pipe symbol here to divide collections.
        final String[] collections = collection.length() == 0 ? new String[0] : collection.split("\\|");
        final int itemsPerPage = call.optInt("itemsPerPage", call.optInt("maximumRecords", call.optInt("rows", call.optInt("num", 10))));
        final int startRecord = call.optInt("startRecord", call.optInt("start", 0));
        //int meanCount = call.opt("meanCount", 5);
        final int timezoneOffset = call.optInt("timezoneOffset", -1);
        //String nav = call.opt("nav", "");
        //String prefermaskfilter = call.opt("prefermaskfilter", "");
        //String constraint = call.opt("constraint", "");
        final int facetLimit = call.optInt("facetLimit", 10);
        final String facetFields = call.optString("facetFields", YaCyQuery.FACET_DEFAULT_PARAMETER);
        final List<WebMapping> facetFieldMapping = new ArrayList<>();
        for (final String s: facetFields.split(",")) facetFieldMapping.add(WebMapping.valueOf(s));
        final Sort sort = new Sort(call.optString("sort", ""));

        // run query against search index
        final YaCyQuery yq = new YaCyQuery(q, collections, contentdom, timezoneOffset);
        final ElasticsearchClient.Query query = Searchlab.ec.query(
                System.getProperties().getProperty("grid.elasticsearch.indexName.web", GridIndex.DEFAULT_INDEXNAME_WEB),
                yq, null, sort, WebMapping.text_t, timezoneOffset, startRecord, itemsPerPage, facetLimit, explain,
                facetFieldMapping.toArray(new WebMapping[facetFieldMapping.size()]));

        // prepare result object
        final JSONObject json = new JSONObject(true);
        try {
            final JSONArray channels = new JSONArray();
            json.put("channels", channels);
            final JSONObject channel = new JSONObject(true);
            channels.put(channel);
            final JSONArray items = new JSONArray();

            // search metadata
            channel.put("title", "Search for " + q);
            channel.put("description", "Search for " + q);
            channel.put("startIndex", "" + startRecord);
            channel.put("searchTerms", q);
            channel.put("totalResults", Integer.toString(query.hitCount));

            // create result list
            final List<Map<String, Object>> result = query.results;
            final List<String> explanations = query.explanations;
            for (int hitc = 0; hitc < result.size(); hitc++) {
                final WebDocument doc = new WebDocument(result.get(hitc));
                final JSONObject hit = new JSONObject(true);
                final String titleString = doc.getTitle();
                String link = doc.getLink();
                if (Classification.ContentDomain.IMAGE == contentdom) {
                    hit.put("url", link); // the url before we extract the link
                    link = doc.pickImage(link);
                    hit.put("icon", link);
                    hit.put("image", link);
                }
                final String snippet = doc.getSnippet(query.highlights.get(hitc), yq);
                final Date last_modified_date = doc.getDate();
                final int size = doc.getSize();
                final int sizekb = size / 1024;
                final int sizemb = sizekb / 1024;
                final String size_string = sizemb > 0 ? (Integer.toString(sizemb) + " mbyte") : sizekb > 0 ? (Integer.toString(sizekb) + " kbyte") : (Integer.toString(size) + " byte");
                final String host = doc.getHost();
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
            }
            channel.put("itemsPerPage", "" + itemsPerPage);
            channel.put("items", items);

            // create facet navigation
            final JSONArray navigation = new JSONArray();
            channel.put("navigation", navigation);
            final Map<String, List<Map.Entry<String, Long>>> aggregations = query.aggregations;
            for (final Map.Entry<String, List<Map.Entry<String, Long>>> fe: aggregations.entrySet()) {
                final String facetname = fe.getKey();
                final WebMapping mapping = WebMapping.valueOf(facetname);
                final JSONObject facetobject = new JSONObject(true);
                facetobject.put("facetname", mapping.getMapping().getFacetname());
                facetobject.put("displayname", mapping.getMapping().getDisplayname());
                facetobject.put("type", mapping.getMapping().getFacettype());
                facetobject.put("min", "0");
                facetobject.put("max", "0");
                facetobject.put("mean", "0");
                facetobject.put("count", fe.getValue().size());
                final JSONArray elements = new JSONArray();
                facetobject.put("elements", elements);
                for (final Map.Entry<String, Long> element: fe.getValue()) {
                    final JSONObject elementEntry = new JSONObject(true);
                    elementEntry.put("name", element.getKey());
                    elementEntry.put("count", element.getValue().toString());
                    elementEntry.put("modifier", mapping.getMapping().getFacetmodifier() + ":" + element.getKey());
                    elements.put(elementEntry);
                }
                navigation.put(facetobject);
            }

            // create page navigation
            final JSONArray pagenav = new JSONArray();
            JSONObject nave = new JSONObject(true);
            nave.put("startRecord", startRecord < itemsPerPage ? 0 : startRecord - itemsPerPage);
            nave.put("page", "&lt;");
            nave.put("style", "default");
            nave.put("same", false);
            pagenav.put(nave);
            final int pages = query.hitCount / itemsPerPage + 1;
            for (int p = 0; p < Math.min(pages, 20); p++) {
                nave = new JSONObject(true);
                nave.put("startRecord", p * itemsPerPage);
                nave.put("page", "" + (p + 1));
                nave.put("style", p * itemsPerPage == startRecord ? "success" : "default");
                nave.put("same", p * itemsPerPage == startRecord);
                pagenav.put(nave);
            }
            nave = new JSONObject(true);
            nave.put("startRecord", (startRecord + itemsPerPage > query.hitCount) ? startRecord : startRecord + itemsPerPage);
            nave.put("page", "&gt;");
            nave.put("style", "default");
            nave.put("same", false);
            pagenav.put(nave);
            json.put("pagenav", pagenav);
        } catch (final JSONException e) {e.printStackTrace();}
        return json;
    }

}
