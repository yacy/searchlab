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

    LinkedHashSet<String> keys;
    JSONArray table;

    public TableGenerator(JSONObject json) throws JSONException {
        this(firstArrayToFind(json));
    }

    private static JSONArray firstArrayToFind(JSONObject json) {
        for (String key: json.keySet()) {
            Object o = json.opt(key);
            if (o != null && o instanceof JSONArray) return (JSONArray) o;
        }
        return null;
    }

    public TableGenerator(JSONArray array) throws JSONException {
        this.keys = new LinkedHashSet<>();
        this.table = new JSONArray();
        if (array == null) return; // well thats then an empty table
        if (array.get(0) instanceof JSONObject) {
            // that table already has the correct shape. we now take care that it is sound and complete.
            for (int i = 0; i < array.length(); i++) {
                JSONObject j = array.getJSONObject(i);
                j.keySet().forEach(key -> this.keys.add(key));
            }
            for (int i = 0; i < array.length(); i++) {
                final JSONObject j = array.getJSONObject(i);
                final JSONObject k = new JSONObject(true);
                this.keys.forEach(key -> {
                    Object o = j.opt(key);
                    try {k.put(key, o == null ? "" : o);} catch (JSONException e) {}
                });
                this.table.put(k);
            }
        } else {
            // transform table-of-table into table-of-objects
            JSONArray h = array.getJSONArray(0);
            h.forEach(key -> this.keys.add(key.toString()));
            for (int i = 1; i < array.length(); i++) {
                final JSONArray a = array.getJSONArray(i);
                final JSONObject k = new JSONObject(true);
                int j = 0;
                for (String key: this.keys) k.put(key, a.get(j++));
                this.table.put(k);
            }
        }
    }

    public String getTableI() throws JSONException {
        StringBuilder sb = new StringBuilder();
        this.keys.forEach(key -> sb.append("<th data-field=\""+ key + "\">" + key + "</th>\n"));
        return  "<table id=\"table\">\n" +
                "  <thead>\n" +
                "    <tr>\n" +
                sb.toString() +
                "    </tr>\n" +
                "  </thead>\n" +
                "</table>\n" +
                "\n" +
                "<script>\n" +
                "  var $table = $('#table')\n" +
                "\n" +
                "  $(function() {\n" +
                "    var data = \n" +
                this.table.toString(2) +
                "    $table.bootstrapTable({data: data})\n" +
                "  })\n" +
                "</script>\n";
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