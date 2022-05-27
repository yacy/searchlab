/**
 *  IndexStatusService
 *  Copyright 27.05.2022 by Michael Peter Christen, @orbiterlab
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

import org.json.JSONException;
import org.json.JSONObject;

import eu.searchlab.Searchlab;
import eu.searchlab.http.Service;
import eu.searchlab.tools.Logger;

/**
 * http://127.0.0.1:8400/en/api/indexstatus.json?query=where
 */
public class IndexStatusService extends AbstractService implements Service {

    private static String[] indexNames = new String[] {"web", "query", "crawlstart", "crawler"};

    @Override
    public String[] getPaths() {
        return new String[] {"/api/indexstatus.json"};
    }

    @Override
    public Type getType() {
        return Service.Type.OBJECT;
    }

    @Override
    public JSONObject serveObject(final JSONObject call) {

        // evaluate request parameter
        //final String indexName = call.optString("index", GridIndex.DEFAULT_INDEXNAME_WEB);
        final JSONObject json = new JSONObject(true);

        try {
            for (final String indexName: indexNames) {
                json.put(indexName, Searchlab.ec.count(indexName));
            }
        } catch (final JSONException e) {Logger.warn(e);}
        return json;
    }

}
