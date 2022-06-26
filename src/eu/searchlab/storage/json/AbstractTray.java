/**
 *  AbstractTray
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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import org.json.JSONObject;

import eu.searchlab.storage.io.ConcurrentIO;
import eu.searchlab.storage.io.IOObject;
import eu.searchlab.storage.io.IOPath;
import eu.searchlab.tools.Logger;

public abstract class AbstractTray implements Tray {

    protected final Object mutex; // Object on which to synchronize
    protected final ConcurrentIO io;
    protected final IOPath iop;
    protected JSONObject object;

    /**
     * AbstractTray
     * @param io
     * @param iop
     * @param lineByLineStorage if true, each property in the object is written to a single line. If false, the file is pretty-printed
     */
    protected AbstractTray(final ConcurrentIO io, final IOPath iop) {
        this.io = io;
        this.iop = iop;
        this.object = null;
        this.mutex = this;

        // check if file exists and create it if not
        try {
        	ensureLoaded();
        } catch (final IOException e) {
            if (this.io.exists(iop)) {
                Logger.error(e);
            } else {
                final JSONObject json = new JSONObject();
                try {
                    this.io.writeForced(new IOObject(this.iop, json));
                } catch (final IOException e1) {
                    Logger.error(e1);
                }
            }
        }

    }

    public static JSONObject read(final File f) throws IOException {
        final InputStream fis = new FileInputStream(f);
        final byte[] a = new byte[(int) f.length()];
        fis.read(a);
        fis.close();
        final JSONObject j = IOObject.readJSONObject(a);
        return j;
    }

    protected Tray commitInternal() throws IOException {
        // now write our data back
        this.io.writeForced(new IOObject(this.iop, this.object));
        this.lastLoadTime = System.currentTimeMillis();
        return this;
    }

    private long lastLoadTime = 0;

    protected void ensureLoaded() throws IOException {
        if (this.object == null) {
            this.object = load();
        } else {
            final long lastModified = this.io.getIO().lastModified(this.iop);
            if (lastModified > this.lastLoadTime) {
                this.object = load();
                this.lastLoadTime = System.currentTimeMillis();
            }
        }
    }

    private JSONObject load() throws IOException {
        final IOObject[] o = this.io.readForced(this.iop);
        final JSONObject json = o[0].getJSONObject();
        return json;
    }

    @Override
    public IOPath getObject() {
        return this.iop;
    }

    @Override
    public int count() {
        synchronized (this.mutex) {
            try {
                ensureLoaded();
            } catch (final IOException e) {
                return 0;
            }
            return this.object.length();
        }
    }

    @Override
    public boolean isEmpty() {
        return count() == 0;
    }

    @Override
    public Set<String> keys() throws IOException {
        synchronized (this.mutex) {
            ensureLoaded();
            return this.object.keySet();
        }
    }

    @Override
    public JSONObject toJSON() throws IOException {
        synchronized (this.mutex) {
            ensureLoaded();
            return this.object;
        }
    }

    @Override
    public int hashCode() {
        return this.iop.hashCode();
    }
}
