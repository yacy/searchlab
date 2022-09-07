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

package eu.searchlab.http.services.assets;

import eu.searchlab.Searchlab;
import eu.searchlab.http.AbstractService;
import eu.searchlab.http.Service;
import eu.searchlab.http.ServiceRequest;
import eu.searchlab.http.ServiceResponse;

/**
 * test:
 * http://localhost:8400/en/api/graph/requests.html
 * http://localhost:8400/en/api/graph/visitors.html
 */
public class GraphGetService extends AbstractService implements Service {

    @Override
    public boolean supportsPath(String path) {
        if (!path.startsWith("/api/graph/")) return false;
        path = path.substring(11);
        final int p = path.indexOf('.');
        if (p < 0) return false;
        final String tablename = path.substring(0, p);
        if (!Searchlab.htmlPanel.has(tablename)) return false;
        final String ext = path.substring(p + 1);
        return ext.equals("html");
    }

    @Override
    public ServiceResponse serve(final ServiceRequest serviceRequest) {

        final String path = serviceRequest.getPath();
        final int p = path.lastIndexOf("/graph/");
        if (p < 0) return new ServiceResponse("");
        final int q = path.indexOf(".", p);
        final String graphname = path.substring(p + 7, q);

        // Graphs may be computed at start-up time concurrently.
        // Because some graphs may not already exist a the time the server was started,
        // we do some busy waiting here until the graph is available
        final long timeout = System.currentTimeMillis() + 600000L; // 10 minutes
        while (System.currentTimeMillis() < timeout) {
            final String graph = Searchlab.htmlPanel.get(graphname);
            if (graph != null && !graph.isEmpty()) {
                return new ServiceResponse(graph);
            }
            try {Thread.sleep(1000);} catch (final InterruptedException e) {}
        }

        // timeout
        return new ServiceResponse("");
    }
}
