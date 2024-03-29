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


package eu.searchlab.http.services.index;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import eu.searchlab.Searchlab;
import eu.searchlab.aaaaa.Authentication;
import eu.searchlab.http.AbstractService;
import eu.searchlab.http.EventCount;
import eu.searchlab.http.Service;
import eu.searchlab.http.ServiceRequest;
import eu.searchlab.http.ServiceResponse;
import eu.searchlab.http.UsageCount;
import eu.searchlab.http.WebServer;
import eu.searchlab.tools.Classification;
import eu.searchlab.tools.DateParser;
import eu.searchlab.tools.Logger;
import net.yacy.grid.io.index.ElasticsearchClient;
import net.yacy.grid.io.index.IndexDAO;
import net.yacy.grid.io.index.Sort;
import net.yacy.grid.io.index.WebDocument;
import net.yacy.grid.io.index.WebMapping;
import net.yacy.grid.io.index.YaCyQuery;

/**
 * yacysearch - the main fulltext search service.
 * This service should be compatible with the YaCy P2P search service, also named yacysearch.
 * The original implementation considered two standards as guide for query and result:
 * - SRU from http://www.loc.gov/standards/sru/sru-1-2.html for queries
 * - Opensearch/XML for XML responses, similar to RSS. The original documentation does not exist any more.
 *   A copy exist in the wayback machine, see:
 *   https://web.archive.org/web/20120112124719/http://www.opensearch.org/Specifications/OpenSearch/1.1#OpenSearch_response_elements
 *   Another copy is here: https://github.com/dewitt/opensearch/blob/master/opensearch-1-1-draft-6.md#opensearch-response-elements
 *
 * However, this original concept was altered later with two influences:
 * - XML results are not state-of-the-art any more. In the searchlab, we drop it completely.
 * - Search queries are also executed for the GSA implementation and accepts those queries as well.
 * - Search results are returned in JSON; we tried to mirror the original Opensearch concept as
 * - we extended search results with search facets to enable faceted search.
 *
 * For query field additions, we still recommend to use SRU, if applicable.
 *
 * Test endpoint: http://localhost:8400/api/yacysearch.json?query=help
 */
public class YaCySearchService extends AbstractService implements Service {

    private final static long[] frequency_time  = {60000, 300000, 3600000}; // 1 minute, 5 minutes, 1 hour
    private final static  int[] frequency_count = {30, 60, 120};

    private final static EventCount badRequests = new EventCount(300000);
    private final static UsageCount badQueries = new UsageCount(5, 300000);

    @Override
    public String[] getPaths() {
        return new String[] {"/api/yacysearch.json", "/search/"};
    }

    @Override
    public ServiceResponse serve(final ServiceRequest request) {

        // check request frequency
        Searchlab.searchRequestCount.event(request.getIPID());
        final int[] eventcount = Searchlab.searchRequestCount.count(request.getIPID(), frequency_time);
        for (int i = 0; i < eventcount.length; i++) {
            if (eventcount[i] > frequency_count[i]) {
                final long retryAfter = EventCount.retryAfter(eventcount[i], frequency_count[i], frequency_time[i]);
                Logger.info("Sending TOO MANY REQUESTS to " + request.getIP00() + " with retryAfter = " + retryAfter);
                return new ServiceResponse().setTooManyRequests(retryAfter);
            }
        }

        final boolean hasReferer = request.hasReferer(); // if this request comes with no referrer, do not offer more than one response pages
        final boolean allowPaging = true; // hasReferer

        // evaluate query
        final String q = request.get("query", request.get("q", "")).trim();
        if (q.length() == 0) return new ServiceResponse(new JSONObject()); // TODO: fix this. We should return a proper object here
        final int qlen = q.split(" ").length;
        // if (!hasReferer) badRequests.event(request.getIP00()); // sus

        if (qlen >= 3) {
            final boolean approved = badQueries.approve(q, request.getIP00());
            if (!approved) {
                // looks like DDos. Ban all IPs that made that request
                WebServer.ipBanned.add(request.getIP00());
                Logger.info("BAN/queries for IP " + request.getIP00());
                badQueries.getClients(q).forEach(client -> {
                    WebServer.ipBanned.add(client);
                    Logger.info("BAN/queries for IP " + client);
                });
                return new ServiceResponse().setBadRequest();
            }
        }

        // evaluate remaining request parameters
        final boolean explain = request.get("explain", false);
        final Classification.ContentDomain contentdom =  Classification.ContentDomain.contentdomParser(request.get("contentdom", "all"));
        String collection = request.get("collection", ""); // important: call arguments may overrule parsed collection values if not empty. This can be used for authentified indexes!
        collection = collection.replace(',', '|'); // to be compatible with the site-operator of GSA, we use a vertical pipe symbol here to divide collections.
        final String[] collections = collection.length() == 0 ? new String[0] : collection.split("\\|");
        final int itemsPerPage = request.get("itemsPerPage", request.get("maximumRecords", request.get("rows", request.get("num", 10))));
        final int startRecord = request.get("startRecord", request.get("start", 0));
        if (startRecord >= 9990 || (startRecord != 0 && !hasReferer)) {
            badRequests.event(request.getIP00()); // sus
            //return new ServiceResponse().setBadRequest();
        }
        if (badRequests.count(request.getIP00(), 300000)[0] >= 10) {
            Logger.info("BAN/requests for IP " + request.getIP00());
            WebServer.ipBanned.add(request.getIP00());
            return new ServiceResponse().setBadRequest();
        }

        //int meanCount = call.opt("meanCount", 5);
        final int timezoneOffset = request.get("timezoneOffset", -1);
        //String nav = call.opt("nav", "");
        //String prefermaskfilter = call.opt("prefermaskfilter", "");
        //String constraint = call.opt("constraint", "");
        final int facetLimit = request.get("facetLimit", 10);
        final String facetFields = request.get("facetFields", YaCyQuery.FACET_DEFAULT_PARAMETER);
        final List<WebMapping> facetFieldMapping = new ArrayList<>();
        for (final String s: facetFields.split(",")) try {
            facetFieldMapping.add(WebMapping.valueOf(s));
        } catch (final IllegalArgumentException e) {Logger.error(e);} // catch exception in case the facet field name is unknown
        final Sort sort = new Sort(request.get("sort", ""));

        // prepare result object
        final JSONObject json = new JSONObject(true);
        final JSONArray channels = new JSONArray();
        final JSONObject channel = new JSONObject(true);
        final JSONArray items = new JSONArray();
        json.put("channels", channels);
        channels.put(channel);
        channel.put("title", "Search for " + q);
        channel.put("description", "Search for " + q);
        channel.put("startIndex", "" + startRecord);
        channel.put("searchTerms", q);
        channel.put("itemsPerPage", "" + itemsPerPage);
        channel.put("page", "" + ((startRecord / itemsPerPage) + 1)); // the current result page, first page has number 1

        // define the constraint for the user-id:
        // - authenticated users will use their own user-id if the self flag is set.
        // - anonymous users have no constraint at all
        String user_id = null;
        if (!request.isAnonymous()) {
            final Authentication authentication = request.getAuthentication();
            // we know that this is not anonymous, therefore the authentication exists
            assert authentication != null;
            // having an authentication does not mean that there is any authorization as well, but we don't need to know that here.
            // everything that counts is if the user who created the authentication has set the self attribute or not
            final boolean self = authentication.getSelf();
            if (self) user_id = authentication.getID();
        }

        // run query against search index
        try {
            final YaCyQuery yq = new YaCyQuery(q, collections, contentdom, timezoneOffset);
            final ElasticsearchClient.Query query = IndexDAO.query(
                user_id, yq, null, sort, WebMapping.text_t, timezoneOffset,
                startRecord, itemsPerPage, facetLimit, explain,
                facetFieldMapping.toArray(new WebMapping[facetFieldMapping.size()]));

            // create result list
            final List<Map<String, Object>> result = query.results;
            if (result.size() == 0 && startRecord > 0) {
                // if we done everything right, this was probably produced by a bot missing boundaries
                badRequests.event(request.getIP00());
                return new ServiceResponse().setBadRequest();
            }
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

            // search metadata
            channel.put("totalResults", allowPaging ? Integer.toString(query.hitCount) : Integer.toString(items.length()));
            channel.put("pages", "" + (allowPaging ? (query.hitCount / itemsPerPage) + 1 : 1));
            channel.put("itemsCount", items.length());
            channel.put("items", items);

            // create facet navigation
            final JSONArray navigation = new JSONArray();
            channel.put("navigation", navigation);
            final Map<String, List<Map.Entry<String, Long>>> aggregations = query.aggregations;
            for (final Map.Entry<String, List<Map.Entry<String, Long>>> fe: aggregations.entrySet()) {
                final String facetname = fe.getKey();
                try {
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
                   long allcount = 0;
                   for (final Map.Entry<String, Long> element: fe.getValue()) {
                       final JSONObject elementEntry = new JSONObject(true);
                       elementEntry.put("name", element.getKey());
                       elementEntry.put("count", element.getValue().toString());
                       elementEntry.put("modifier", mapping.getMapping().getFacetmodifier() + ":" + element.getKey());
                       allcount += element.getValue();
                       elements.put(elementEntry);
                   }
                   // now go through all elements again and set a percentage
                   for (int i = 0; i < elements.length(); i++) {
                       final JSONObject elementEntry = elements.getJSONObject(i);
                       elementEntry.put("percent", Double.toString(Math.round(10000.0d * Double.parseDouble(elementEntry.getString("count")) / (allcount)) / 100.0d));
                   }
                   // store facet
                   navigation.put(facetobject);
                } catch (final IllegalArgumentException e) {} // catch exception in case the facet field name is unknown
            }

            // create page navigation
            final JSONArray pagenav = new JSONArray();
            if (allowPaging) {
                JSONObject nave = new JSONObject(true);
                nave.put("startRecord", startRecord < itemsPerPage ? 0 : startRecord - itemsPerPage);
                nave.put("page", "&lt;");
                nave.put("same", false);
                pagenav.put(nave);
                final int pages = allowPaging ? (query.hitCount / itemsPerPage) + 1 : 1;
                for (int p = 0; p < Math.min(pages, 20); p++) {
                    nave = new JSONObject(true);
                    nave.put("startRecord", p * itemsPerPage);
                    nave.put("page", "" + (p + 1));
                    nave.put("same", p * itemsPerPage == startRecord);
                    pagenav.put(nave);
                }
                nave = new JSONObject(true);
                nave.put("startRecord", (startRecord + itemsPerPage > query.hitCount) ? startRecord : startRecord + itemsPerPage);
                nave.put("page", "&gt;");
                nave.put("same", false);
                pagenav.put(nave);
            }
            channel.put("pagenav", pagenav);
        } catch (final Exception e) {
            // any kind of exception can happen if the elastic index is not ready or index does not exist
            Logger.error(e);
            channel.put("totalResults", 0);
            channel.put("pages", "0");
        }
        return new ServiceResponse(json);
    }

}
