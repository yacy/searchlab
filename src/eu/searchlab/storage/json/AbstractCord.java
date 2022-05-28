/**
 *  AbstractCord
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

package eu.searchlab.storage.json;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import eu.searchlab.storage.io.ConcurrentIO;
import eu.searchlab.storage.io.IOObject;
import eu.searchlab.storage.io.IOPath;

public abstract class AbstractCord implements Cord {

    protected final Object mutex; // Object on which to synchronize
    protected final ConcurrentIO io;
    protected final IOPath iop;
    protected JSONArray array;

    /**
     * AbstractCord
     * @param io
     * @param iop
     */
    protected AbstractCord(final ConcurrentIO io, final IOPath iop) {
        this.io = io;
        this.iop = iop;
        this.array = null;
        this.mutex = this;
    }

    protected Cord commitInternal() throws IOException {
        // now write our data back
        this.io.writeForced(new IOObject(this.iop, this.array));
        this.lastLoadTime = System.currentTimeMillis();
        return this;
    }

    private long lastLoadTime = 0;

    protected void ensureLoaded() throws IOException {
        if (this.array == null) {
            this.array = load();
        } else {
            final long lastModified = this.io.getIO().lastModified(this.iop);
            if (lastModified > this.lastLoadTime) {
                this.array = load();
                this.lastLoadTime = System.currentTimeMillis();
            }
        }
    }

    private JSONArray load() throws IOException {
        final IOObject[] o = this.io.readForced(this.iop);
        final JSONArray json = o[0].getJSONArray();
        return json;
    }

    @Override
    public IOPath getObject() {
        return this.iop;
    }

    @Override
    public int size() {
        synchronized (this.mutex) {
            try {
                this.ensureLoaded();
            } catch (final IOException e) {
                return 0;
            }
            return this.array.length();
        }
    }

    @Override
    public boolean isEmpty() {
        return this.size() > 0;
    }

    @Override
    public JSONObject get(final int p) throws IOException {
        synchronized (this.mutex) {
            this.ensureLoaded();
            Object o;
            try {
                o = this.array.get(p);
                assert o instanceof JSONObject;
                return (JSONObject) o;
            } catch (final JSONException e) {
                throw new IOException(e.getMessage());
            }
        }
    }

    @Override
    public JSONObject getFirst() throws IOException {
        return this.get(0);
    }

    @Override
    public JSONObject getLast() throws IOException {
        synchronized (this.mutex) {
            this.ensureLoaded();
            Object o;
            try {
                o = this.array.get(this.array.length() - 1);
                assert o instanceof JSONObject;
                return (JSONObject) o;
            } catch (final JSONException e) {
                throw new IOException(e.getMessage());
            }
        }
    }

    @Override
    public List<JSONObject> getAllWhere(final String key, final String value) throws IOException{
        final List<JSONObject> list = new ArrayList<>();
        synchronized (this.mutex) {
            this.ensureLoaded();
            final Iterator<Object> i = this.array.iterator();
            while (i.hasNext()) {
                final Object o = i.next();
                if (!(o instanceof JSONObject)) continue;
                final Object v = ((JSONObject) o).opt(key);
                if (!(v instanceof String)) continue;
                if (((String) v).equals(value)) {
                    list.add((JSONObject) o);
                }
            }
            return list;
        }
    }

    @Override
    public List<JSONObject> getAllWhere(final String key, final long value) throws IOException {
        final List<JSONObject> list = new ArrayList<>();
        synchronized (this.mutex) {
            this.ensureLoaded();
            final Iterator<Object> i = this.array.iterator();
            while (i.hasNext()) {
                final Object o = i.next();
                if (!(o instanceof JSONObject)) continue;
                final Object v = ((JSONObject) o).opt(key);
                if (!(v instanceof Long) && !(v instanceof Integer)) continue;
                if (((Long) v).longValue() == value) {
                    list.add((JSONObject) o);
                }
            }
            return list;
        }
    }

    @Override
    public JSONObject getOneWhere(final String key, final String value) throws IOException {
        synchronized (this.mutex) {
            this.ensureLoaded();
            final Iterator<Object> i = this.array.iterator();
            while (i.hasNext()) {
                final Object o = i.next();
                if (!(o instanceof JSONObject)) continue;
                final Object v = ((JSONObject) o).opt(key);
                if (!(v instanceof String)) continue;
                if (((String) v).equals(value)) {
                    return (JSONObject) o;
                }
            }
            return null;
        }
    }

    @Override
    public JSONObject getOneWhere(final String key, final long value) throws IOException {
        synchronized (this.mutex) {
            this.ensureLoaded();
            final Iterator<Object> i = this.array.iterator();
            while (i.hasNext()) {
                final Object o = i.next();
                if (!(o instanceof JSONObject)) continue;
                final Object v = ((JSONObject) o).opt(key);
                if (!(v instanceof Long) && !(v instanceof Integer)) continue;
                if (((Long) v).longValue() == value) {
                    return (JSONObject) o;
                }
            }
            return null;
        }
    }

    @Override
    public JSONArray toJSON() throws IOException {
        synchronized (this.mutex) {
            this.ensureLoaded();
            return this.array;
        }
    }

    @Override
    public int hashCode() {
        return this.iop.hashCode();
    }

}
