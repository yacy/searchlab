/**
 *  AbstractService
 *  Copyright 06.10.2021 by Michael Peter Christen, @orbiterlab
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
import org.json.JSONObject;

import eu.searchlab.http.Service;
import eu.searchlab.storage.table.IndexedTable;
import tech.tablesaw.api.Table;

public abstract class AbstractService implements Service {

    public static String normalizePath(String path) {
        path = path.trim();
        if (path.length() == 0) return path;
        if (path.charAt(0) == '/') path = path.substring(1);
        return path;
    }

    @Override
    public Type getType() {
        return Service.Type.OBJECT;
    }

    @Override
    public String[] getPaths() {
        return new String[] {};
    }

    @Override
    public boolean supportsPath(String path) {
        path = normalizePath(path);
        String[] paths = this.getPaths();
        for (String p: paths) if (normalizePath(p).equals(path)) return true;
        return false;
    }

    @Override
    public JSONObject serveObject(JSONObject post) throws IOException {
        return new JSONObject();
    }

    @Override
    public JSONArray serveArray(JSONObject post) throws IOException {
        return new JSONArray();
    }

    @Override
    public String serveString(JSONObject post) throws IOException {
        return "";
    }

    @Override
    public IndexedTable serveTable(JSONObject post) throws IOException {
        return new IndexedTable(Table.create());
    }

}
