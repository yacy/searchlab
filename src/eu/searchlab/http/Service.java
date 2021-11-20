/**
 *  Service
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

package eu.searchlab.http;

import java.io.IOException;

import org.json.JSONArray;
import org.json.JSONObject;

import eu.searchlab.storage.table.IndexedTable;

public interface Service {

    public enum Type {
        OBJECT, ARRAY, STRING, TABLE;
    }

    public boolean supportsPath(String path);

    public String[] getPaths();

    public Type getType();

    public JSONObject serveObject(JSONObject post) throws IOException;

    public JSONArray serveArray(JSONObject post) throws IOException;

    public String serveString(JSONObject post) throws IOException;

    public IndexedTable serveTable(JSONObject post) throws IOException;
}
