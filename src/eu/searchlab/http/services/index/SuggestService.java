/**
 *  SuggestService
 *  Copyright 23.02.2022 by Michael Peter Christen, @orbiterlab
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

import java.util.Collection;

import org.json.JSONArray;

import eu.searchlab.aaaaa.Authentication;
import eu.searchlab.http.AbstractService;
import eu.searchlab.http.Service;
import eu.searchlab.http.ServiceRequest;
import eu.searchlab.http.ServiceResponse;
import net.yacy.grid.io.index.Typeahead;

// http://localhost:8400/en/api/suggest.json?q=ne
public class SuggestService extends AbstractService implements Service {

    private static final int meanMax = 30;

    /**
     * for json format:
     * implementation of the opensearch suggestion extension, see
     * http://www.opensearch.org/Specifications/OpenSearch/Extensions/Suggestions/1.1
     * or
     * https://wiki.mozilla.org/Search_Service/Suggestions
     */

    // request:
    // /suggest.json?q=eins%20zwei%20drei
    //
    // should return a json array like
    // [
    //   "query", ["text0", "text1", "text2"]
    // ]
    // #[jsonp-start]#["#[query]#",[#{suggestions}#"#[text]#"#(eol)#,::#(/eol)##{/suggestions}#]]#[jsonp-end]#

    @Override
    public String[] getPaths() {
        return new String[] {"/api/suggest.json"};
    }

    @Override
    public ServiceResponse serve(final ServiceRequest serviceRequest) {

        // evaluate request parameter
        final String originalquerystring = serviceRequest.get("query", serviceRequest.get("q", ""));
        final String querystring =  originalquerystring.trim();
        final int timeout = serviceRequest.get("timeout", 300);
        final int count = Math.min(30, serviceRequest.get("count", 20));

        final Authentication authentication = serviceRequest.getAuthentication();
        final boolean self = authentication == null ? false : authentication.getSelf();
        final String user_id = self ? authentication.getID() : null;

        // find answer
        final Typeahead typeahead = new Typeahead(querystring);
        final Collection<String> suggestions = typeahead.getTypeahead(timeout, count, user_id);

        final JSONArray json = new JSONArray();
        json.put(originalquerystring);
        final JSONArray a = new JSONArray();
        for (final String suggestion: suggestions) {
            if (a.length() >= meanMax) break;
            final String s = suggestion;
            a.put(s);
        }
        json.put(a);
        return new ServiceResponse(json);
    }

}
