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

import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import eu.searchlab.TabelPanel;
import eu.searchlab.http.Service;
import eu.searchlab.storage.table.PersistentTables;
import tech.tablesaw.api.Table;
import tech.tablesaw.columns.Column;

public class TableGetService extends AbstractService implements Service {

    @Override
    public boolean supportsPath(String path) {
        if (!path.startsWith("api/get/")) return false;
        path = path.substring(8);
        final int p = path.indexOf('.');
        if (p < 0) return false;
        final String tablename = path.substring(0, p);
        if (TabelPanel.tables.getTable(tablename) == null) return false;
        final String ext = path.substring(p + 1);
        return ext.equals("json") || ext.equals("csv") || ext.equals("table") || ext.equals("tablei");
    }

    @Override
    public Type getType() {
        return Service.Type.ARRAY;
    }

    @Override
    public JSONArray serveArray(JSONObject post) {
        JSONArray array = new JSONArray();
        String path = post.optString("PATH", "");
        int p = path.lastIndexOf("/get/");
        if (p < 0) return array;
        int q = path.indexOf(".", p);
        String tablename = path.substring(p + 5, q);
        final boolean asObjects = post.optBoolean("asObjects", true);
        final String where = post.optString("where"); // where=col0:val0,col1:val1,...
        final String select = post.optString("select"); // get(column, value), pivot(column, op)
        final int count = post.optInt("count", -1);

        Table table = null;
        // where
        if (where.length() > 0) {
            table = TabelPanel.tables.where(tablename, where.split(",")).table();
        } else {
            table = TabelPanel.tables.getTable(tablename).table();
        }

        // sort
        // segment
        if ("head".equals(select) && count > 0) {
            table = PersistentTables.head(table, count);
        }
        if (table.rowCount() > 100000) table = PersistentTables.head(table, 100000);

        if (asObjects) {
            for (int i = 0; i < table.rowCount(); i++) {
                JSONObject json = new JSONObject();
                for (int j = 0; j < table.columnCount(); j++) {
                    Column<?> c = table.column(j);
                    try {json.put(c.name(), c.get(i));} catch (JSONException e) {}
                }
                array.put(json);
            }
        } else {
            List<String> colnames = table.columnNames();
            JSONArray a = new JSONArray();
            for (String name: colnames) a.put(name);
            array.put(a);

            for (int i = 0; i < table.rowCount(); i++) {
                a = new JSONArray();
                for (int j = 0; j < colnames.size(); j++) {
                    Column<?> c = table.column(colnames.get(j));
                    a.put(c.get(i));
                }
                array.put(a);
            }
        }
        return array;
    }

}
