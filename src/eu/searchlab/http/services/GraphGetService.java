/**
 *  GraphGetService
 *  Copyright 01.10.2021 by Michael Peter Christen, @orbiterlab
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

import org.json.JSONObject;
import org.simpleframework.xml.Path;

import eu.searchlab.HTMLPanel;
import eu.searchlab.http.Service;

@Path("/notifications")
public class GraphGetService extends AbstractService implements Service {

    @Override
    public boolean supportsPath(String path) {
        if (!path.startsWith("api/graph/")) return false;
        path = path.substring(10);
        final int p = path.indexOf('.');
        if (p < 0) return false;
        final String tablename = path.substring(0, p);
        if (!HTMLPanel.htmls.containsKey(tablename)) return false;
        final String ext = path.substring(p + 1);
        return ext.equals("html");
    }

    @Override
    public Type getType() {
        return Service.Type.STRING;
    }

    @Override
    public String serveString(JSONObject post) {

        final String path = post.optString("PATH", "");
        final int p = path.lastIndexOf("/graph/");
        if (p < 0) return "";
        final int q = path.indexOf(".", p);
        final String graphname = path.substring(p + 7, q);
        final String graph = HTMLPanel.htmls.get(graphname);
        return graph;
    }
}
