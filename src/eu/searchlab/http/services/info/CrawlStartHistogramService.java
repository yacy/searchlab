/**
 *  CrawlStartHistogramService
 *  Copyright 21.10.2021 by Michael Peter Christen, @orbiterlab
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

package eu.searchlab.http.services.info;

import eu.searchlab.Searchlab;
import eu.searchlab.http.AbstractService;
import eu.searchlab.http.Service;
import eu.searchlab.http.ServiceRequest;
import eu.searchlab.http.ServiceResponse;
import eu.searchlab.storage.table.TableViewer;
import eu.searchlab.storage.table.TimeSeriesTable;
import net.yacy.grid.io.index.IndexDAO;

/**
 * test:
 * http://localhost:8400/754683219/api/graph/crawl_start_histogram.html
 */
public class CrawlStartHistogramService extends AbstractService implements Service {

    @Override
    public boolean supportsPath(final String path) {
        if (!path.startsWith("/api/graph/crawl_start_histogram")) return false;
        final int p = path.indexOf('.');
        if (p < 0) return false;
        final String ext = path.substring(p + 1);
        return ext.equals("html");
    }

    @Override
    public ServiceResponse serve(final ServiceRequest serviceRequest) {
        final TimeSeriesTable tst = IndexDAO.getCrawlstartHistorgramAggregation();
        final TableViewer requestsTableViewer = tst.getGraph("crawl_start", "Crawl Starts", "Date", TimeSeriesTable.TS_DATE, new String[] {"data.crawlstarts SteelBlue"}, new String[] {});
        final String graph = requestsTableViewer.render2html(Searchlab.GRAPH_WIDTH, Searchlab.GRAPH_HEIGHT, true);
        return new ServiceResponse(graph);
    }
}
