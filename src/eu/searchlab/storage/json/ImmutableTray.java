/**
 *  ImmutableTray
 *  Copyright 26.06.2022 by Michael Peter Christen, @orbiterlab
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
import java.util.concurrent.ConcurrentHashMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import eu.searchlab.storage.io.ConcurrentIO;
import eu.searchlab.storage.io.IOPath;

/**
 * An ImmutableTray is like a PersistentTray and stores all write operations
 * of new objects immediately to the backend. But unlinke the PersistentTray
 * it does not allow the change of an existing object. That causes that read
 * operations to the tray do not need to check the file storage date.
 * In concurrent situations this applies also: if the object exists, there is
 * no need to check the backend. However, if the object does not exist, a check
 * must be done unless the object is in a volatile delete-list.
 */
public class ImmutableTray extends AbstractTray implements Tray {

    ConcurrentHashMap<String, Object> deleted;

    public ImmutableTray(final ConcurrentIO io, final IOPath iop) {
        super(io, iop);
        this.deleted = new ConcurrentHashMap<>();
    }

    @Override
    public JSONObject getObject(final String key) throws IOException {
    	assert key != null;
        synchronized (this.mutex) {
            if (this.deleted.containsKey(key)) return null;
            final JSONObject json = this.object.optJSONObject(key);
            if (json != null) return json;
            ensureLoaded();
            return this.object.optJSONObject(key);
        }
    }

    @Override
    public JSONArray getArray(final String key) throws IOException {
    	assert key != null;
        synchronized (this.mutex) {
            if (this.deleted.containsKey(key)) return null;
            final JSONArray json = this.object.optJSONArray(key);
            if (json != null) return json;
            ensureLoaded();
            return this.object.optJSONArray(key);
        }
    }

    @Override
    public Tray put(final String key, final JSONObject value) throws IOException  {
    	assert key != null;
        synchronized (this.mutex) {
            ensureLoaded();
            try {
                this.object.put(key, value);
            } catch (final JSONException e) {
                throw new IOException(e.getMessage());
            }
            this.commitInternal();
            return this;
        }
    }

    @Override
    public Tray put(final String key, final JSONArray value) throws IOException {
    	assert key != null;
        synchronized (this.mutex) {
            ensureLoaded();
            try {
                this.object.put(key, value);
            } catch (final JSONException e) {
                throw new IOException(e.getMessage());
            }
            this.commitInternal();
            return this;
        }
    }

    @Override
    public Tray remove(final String key) throws IOException {
    	assert key != null;
        synchronized (this.mutex) {
            if (this.deleted.contains(key)) return this;
            this.deleted.put(key, null);
            ensureLoaded();
            if (!this.object.has(key)) return this;
            this.object.remove(key);
            this.commitInternal();
            return this;
        }
    }

    @Override
    public Tray commit() throws IOException {
        // do nothing because all changes have already been written
        return this;
    }

    @Override
    public void close() throws IOException {
        synchronized (this.mutex) {
            this.object = null;
        }
    }

}
