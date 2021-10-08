/**
 *  PersistentTray
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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import eu.searchlab.storage.io.GenericIO;
import eu.searchlab.storage.io.IOPath;

public class PersistentTray extends AbstractTray implements Tray {

    public PersistentTray(final GenericIO io, final IOPath iop) {
        super(io, iop);
    }

    @Override
    public Tray put(final String key, final JSONObject value) throws IOException  {
        synchronized (this.mutex) {
            ensureLoaded();
            try {
                this.object.put(key, value);
            } catch (JSONException e) {
                throw new IOException(e.getMessage());
            }
            this.commitInternal();
            return this;
        }
    }

    @Override
    public Tray put(final String key, final JSONArray value) throws IOException {
        synchronized (this.mutex) {
            ensureLoaded();
            try {
                this.object.put(key, value);
            } catch (JSONException e) {
                throw new IOException(e.getMessage());
            }
            this.commitInternal();
            return this;
        }
    }

    @Override
    public Tray remove(final String key) throws IOException {
        synchronized (this.mutex) {
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
