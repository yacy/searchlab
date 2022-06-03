/**
 *  ServiceResponse
 *  Copyright 16.01.2017 by Michael Peter Christen, @orbiterlab
 *  (copied from yacy_grid_mcp over to searchlab and extended 02.06.2022)
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
import java.nio.charset.StandardCharsets;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import eu.searchlab.http.Service.Type;
import eu.searchlab.storage.table.IndexedTable;
import eu.searchlab.tools.Logger;

public class ServiceResponse {

    private final Object object;
    private final Type type;
    private boolean setCORS;

    public ServiceResponse(final JSONObject json) {
        this.object = json;
        this.type = Type.OBJECT;
        this.setCORS = false;
    }

    public ServiceResponse(final JSONArray json) {
        this.object = json;
        this.type = Type.ARRAY;
        this.setCORS = false;
    }

    public ServiceResponse(final String string) {
        this.object = string;
        this.type = Type.STRING;
        this.setCORS = false;
    }

    public ServiceResponse(final byte[] bytes) {
        this.object = bytes;
        this.type = Type.ARRAY;
        this.setCORS = false;
    }

    public ServiceResponse(final IndexedTable table) {
        this.object = table;
        this.type = Type.TABLE;
        this.setCORS = false;
    }

    public ServiceResponse setCORS() {
        this.setCORS = true;
        return this;
    }

    public Type getType() {
        return this.type;
    }

    public boolean allowCORS() {
        return this.setCORS;
    }

    public boolean isObject() {
        return this.object instanceof JSONObject;
    }

    public boolean isArray() {
        return this.object instanceof JSONArray;
    }

    public boolean isString() {
        return this.object instanceof String;
    }

    public boolean isByteArray() {
        return this.object instanceof byte[];
    }

    public boolean isTable() {
        return this.object instanceof IndexedTable;
    }

    public JSONObject getObject() throws IOException {
        if (!isObject()) throw new IOException("object type is not JSONObject: " + this.object.getClass().getName());
        return (JSONObject) this.object;
    }

    public JSONArray getArray() throws IOException {
        if (!isArray()) throw new IOException("object type is not JSONArray: " + this.object.getClass().getName());
        return (JSONArray) this.object;
    }

    public String getString() throws IOException {
        if (!isString()) throw new IOException("object type is not String: " + this.object.getClass().getName());
        return (String) this.object;
    }

    public byte[] getByteArray() throws IOException {
        if (!isByteArray()) throw new IOException("object type is not ByteArray: " + this.object.getClass().getName());
        return (byte[]) this.object;
    }

    public IndexedTable getTable() throws IOException {
        if (!isTable()) throw new IOException("object type is not Table: " + this.object.getClass().getName());
        return (IndexedTable) this.object;
    }

    public String getMimeType() {
        if (isObject() || isArray()) return "application/javascript";
        if (isString()) {
            try {
                return getString().startsWith("<?xml") ? "application/xml" : "text/plain";
            } catch (final IOException e) {
                return "application/octet-stream";
            }
        }
        return "application/octet-stream";
    }

    public String toString(final boolean minified) throws IOException {
        try {
            if (isObject()) return getObject().toString(minified ? 0 : 2);
            if (isArray()) return getArray().toString(minified ? 0 : 2);
            if (isString()) return getString();
            if (isByteArray()) return new String((byte[]) this.object, StandardCharsets.UTF_8);
        } catch (final JSONException e) {
            Logger.error(e);
        }
        return null;
    }

    public byte[] toByteArray(final boolean minified) throws IOException {
        try {
            if (isObject()) return getObject().toString(minified ? 0 : 2).getBytes(StandardCharsets.UTF_8);
            if (isArray()) return getArray().toString(minified ? 0 : 2).getBytes(StandardCharsets.UTF_8);
            if (isString()) return getString().getBytes(StandardCharsets.UTF_8);
            if (isByteArray()) return (byte[]) this.object;
        } catch (final JSONException e) {
            Logger.error(e);
        }
        return null;
    }
}
