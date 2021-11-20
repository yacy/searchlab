/**
 *  TableGenerator
 *  Copyright 08.10.2021 by Michael Peter Christen, @orbiterlab
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

package eu.searchlab.http;

import java.util.LinkedHashSet;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class TableGenerator {

    private final LinkedHashSet<String> keys;
    private final JSONArray table;
    private final String tablehash, tablename;

    public TableGenerator(String tablename, JSONObject json) throws JSONException {
        this(tablename, firstArrayToFind(json));
    }

    private static JSONArray firstArrayToFind(JSONObject json) {
        for (final String key: json.keySet()) {
            final Object o = json.opt(key);
            if (o != null && o instanceof JSONArray) return (JSONArray) o;
        }
        return null;
    }

    public TableGenerator(String tablename, JSONArray array) throws JSONException {
        this.tablename = tablename;
        this.keys = new LinkedHashSet<>();
        this.table = new JSONArray();
        this.tablehash = "h" + array.hashCode();
        if (array == null || array.length() == 0) return; // well thats then an empty table
        if (array.get(0) instanceof JSONObject) {
            // that table already has the correct shape. we now take care that it is sound and complete.
            for (int i = 0; i < array.length(); i++) {
                final JSONObject j = array.getJSONObject(i);
                j.keySet().forEach(key -> this.keys.add(key));
            }
            for (int i = 0; i < array.length(); i++) {
                final JSONObject j = array.getJSONObject(i);
                final JSONObject k = new JSONObject(true);
                this.keys.forEach(key -> {
                    final Object o = j.opt(key);
                    try {k.put(key, o == null ? "" : o);} catch (final JSONException e) {}
                });
                this.table.put(k);
            }
        } else {
            // transform table-of-table into table-of-objects
            final JSONArray h = array.getJSONArray(0);
            h.forEach(key -> this.keys.add(key.toString()));
            for (int i = 1; i < array.length(); i++) {
                final JSONArray a = array.getJSONArray(i);
                final JSONObject k = new JSONObject(true);
                int j = 0;
                for (final String key: this.keys) k.put(key, a.get(j++));
                this.table.put(k);
            }
        }
    }

    public TableGenerator(String tablename, LinkedHashSet<String> keys) throws JSONException {
        this.tablehash = "h" + tablename.hashCode();
        this.tablename = tablename;
        this.table = null;
        this.keys = keys;
    }

    public String getTableI() throws JSONException {
        if (this.table == null) {
            final StringBuilder thead = new StringBuilder();
            this.keys.forEach(key -> thead.append("<th data-field=\"" + key + "\">" + key + "</th>\n"));
            return
                    "<table id=\"table" + this.tablehash + "\" data-url=\"/api/get/" + this.tablename + ".json\" data-search=\"true\" data-show-columns=\"true\" data-show-pagination-switch=\"true\" data-pagination=\"true\">" +
                    "<thead><tr>" + thead + "</tr></thead>\n" +
                    "</table>\n" +
                    "<script>\n" +
                    "$('#table" + this.tablehash + "').bootstrapTable(" /*{columns: [" + columns.toString() + "], data: " + table.toString(2) + "\n}*/ + ")\n" +
                    "</script>\n";
        } else {
            final StringBuilder columns = new StringBuilder();
            this.keys.forEach(key -> columns.append("{field: '").append(key).append("', title: '").append(key).append("'},"));
            if (columns.length() > 0) columns.setLength(columns.length() - 1); // cut off comma
            return
                    "<table id=\"table" + this.tablehash + "\" data-search=\"true\" data-show-columns=\"true\" data-show-pagination-switch=\"true\" data-pagination=\"true\">" +
                    "</table>\n" +
                    "<script>\n" +
                    "$('#table" + this.tablehash + "').bootstrapTable({columns: [" + columns.toString() + "], data: " + this.table.toString(2) + "\n})\n" +
                    "</script>\n";
        }
    }

    public String getTable() throws JSONException {
        return "<html>\n" +
                "<head>\n" +
                "<link href=\"/css/bootstrap-custom.css\" rel=\"stylesheet\">\n" +
                "<link href=\"/css/base.css\" rel=\"stylesheet\">\n" +
                "<link href=\"/css/cinder.css\" rel=\"stylesheet\">\n" +
                "<link href=\"/css/bootstrap-table.min.css\" rel=\"stylesheet\">\n" +
                "<script src=\"/js/jquery.min.js\"></script>\n" +
                "<script src=\"/js/fontawesome-all.js\"></script>\n" +
                "<script src=\"/js/bootstrap.min.js\"></script>\n" +
                "<script src=\"/js/bootstrap-table.min.js\"></script>\n" +
                "</head><body>\n" + getTableI() + "</body></html>";
    }


}