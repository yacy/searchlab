/**
 *  TableGetService
 *  Copyright 12.10.2021 by Michael Peter Christen, @orbiterlab
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

import java.io.IOException;

import org.json.JSONArray;

import eu.searchlab.TablePanel;
import eu.searchlab.http.Service;
import eu.searchlab.http.ServiceRequest;
import eu.searchlab.http.ServiceResponse;
import eu.searchlab.storage.table.IndexedTable;

public class TableGetService extends AbstractService implements Service {

    @Override
    public boolean supportsPath(String path) {
        if (!path.startsWith("api/get/")) return false;
        path = path.substring(8);
        final int p = path.indexOf('.');
        if (p < 0) return false;
        final String tablename = path.substring(0, p);
        try {
            if (TablePanel.tables.getTable(tablename) == null) return false;
        } catch (final IOException e) {
            e.printStackTrace();
            return false;
        }
        final String ext = path.substring(p + 1);
        return ext.equals("json") || ext.equals("csv") || ext.equals("table") || ext.equals("tablei");
    }

    @Override
    public ServiceResponse serve(final ServiceRequest request) throws IOException {
        final String path = request.get("PATH", "");
        final int p = path.lastIndexOf("/get/");
        if (p < 0) return new ServiceResponse(new JSONArray());
        final int q = path.indexOf(".", p);
        final String tablename = path.substring(p + 5, q);
        final boolean asObjects = request.get("asObjects", true);
        final String where = request.get("where", ""); // where=col0:val0,col1:val1,...
        final String select = request.get("select", ""); // get(column, value), pivot(column, op)
        final int count = request.get("count", -1);

        final IndexedTable table = selectArray(tablename, where, select, count, asObjects);
        final JSONArray array = table.toJSON(asObjects);
        return new ServiceResponse(array);
    }

    public static IndexedTable selectArray(final String tablename, final String where, String select, int count, final boolean asObjects) throws IOException {

        // get table from panel
        IndexedTable it;
        // where
        if (where.length() > 0) {
            it = TablePanel.tables.where(tablename, where.split(","));
        } else {
            it = TablePanel.tables.getTable(tablename);
        }

        // execute constraints
        // sort
        // segment
        if (it.rowCount() > 100000) {count = count <= 0 ? 100000 : Math.min(100000, count); select = "head";}
        if ("head".equals(select) && count > 0) {
            it = it.head(count);
        }

        return it;
    }

}
