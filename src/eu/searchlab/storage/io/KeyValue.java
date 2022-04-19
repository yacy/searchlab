/**
 *  KeyValue
 *  Copyright 19.04.2022 by Michael Peter Christen, @orbiterlab
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

package eu.searchlab.storage.io;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.Snapshot;

public class KeyValue {

    private Map<String, byte[]> ram;
    private RocksDB db;
    private FileIO io;
    private final IOPath iop;

    public KeyValue() {
        this.ram = new ConcurrentHashMap<>();
        this.db = null;
        this.io = null;
        this.iop = null;
    }

    public KeyValue(final String path) throws IOException {
        this.ram = null;
        try {
            this.db = RocksDB.open(path);
        } catch (final RocksDBException e) {
            throw new IOException(e.getMessage());
        }
        this.io = null;
        this.iop = null;
    }

    public KeyValue(final FileIO io, final IOPath iop) throws IOException {
        this.ram = null;
        this.db = null;
        this.io = io;
        if (!iop.isFolder()) throw new IOException("IOPath must be a folder to append a path: " + iop.toString());
        this.iop = iop;
    }

    public KeyValue put(final String key, final byte[] value) throws IOException {
        if (this.ram != null) {
            this.ram.put(key, value);
        }
        if (this.db != null) {
            try {
                this.db.put(key.getBytes(), value);
            } catch (final RocksDBException e) {
                throw new IOException(e.getMessage());
            }
        }
        if (this.io != null) {
            final IOPath keypath = this.iop.append(key);
            this.io.write(keypath, value);
        }
        return this;
    }

    public KeyValue put(final String key, final String value) throws IOException {
        return put(key, value.getBytes(StandardCharsets.UTF_8));
    }

    public KeyValue put(final String key, final JSONObject value) throws IOException {
        try {
            return put(key, value.toString(0));
        } catch (final JSONException e) {
            throw new IOException(e.getMessage());
        }
    }

    public KeyValue put(final String key, final JSONArray value) throws IOException {
        try {
            return put(key, value.toString(0));
        } catch (final JSONException e) {
            throw new IOException(e.getMessage());
        }
    }

    public byte[] get(final String key) throws IOException {
        if (this.ram != null) {
            return this.ram.get(key);
        }
        if (this.db != null) {
            try {
                return this.db.get(key.getBytes());
            } catch (final RocksDBException e) {
                throw new IOException(e.getMessage());
            }
        }
        if (this.io != null) {
            final IOPath keypath = this.iop.append(key);
            if (!this.io.exists(keypath)) return null;
            return this.io.readAll(keypath);
        }
        return null;
    }

    public String getString(final String key) throws IOException {
        final byte[] value = get(key);
        if (value == null) return null;
        return new String(value, StandardCharsets.UTF_8);
    }

    public String getString(final String key, final String dflt) throws IOException {
        final byte[] value = get(key);
        if (value == null) return dflt;
        return new String(value, StandardCharsets.UTF_8);
    }

    public int getInt(final String key, final int dflt) throws IOException {
        final String value = getString(key);
        if (value == null) return dflt;
        return Integer.parseInt(value);
    }

    public long getLong(final String key, final long dflt) throws IOException {
        final String value = getString(key);
        if (value == null) return dflt;
        return Long.parseLong(value);
    }

    public double getDouble(final String key, final double dflt) throws IOException {
        final String value = getString(key);
        if (value == null) return dflt;
        return Double.parseDouble(value);
    }

    public JSONObject getObject(final String key) throws IOException {
        final String value = getString(key);
        if (value == null) throw new IOException("object not found for key: " + key);
        try {
            return new JSONObject(new JSONTokener(value));
        } catch (final JSONException e) {
            throw new IOException(e.getMessage());
        }
    }

    public JSONArray getArray(final String key) throws IOException {
        final String value = getString(key);
        if (value == null) throw new IOException("object not found for key: " + key);
        try {
            return new JSONArray(new JSONTokener(value));
        } catch (final JSONException e) {
            throw new IOException(e.getMessage());
        }
    }

    public KeyValue delete(final String key) throws IOException {
        if (this.ram != null) {
            this.ram.remove(key);
        }
        if (this.db != null) {
            try {
                this.db.delete(key.getBytes());
            } catch (final RocksDBException e) {
                throw new IOException(e.getMessage());
            }
        }
        if (this.io != null) {
            final IOPath keypath = this.iop.append(key);
            if (this.io.exists(keypath)) this.io.remove(keypath);
        }
        return this;
    }

    public void close() {
        if (this.ram != null) this.ram = null;
        if (this.db != null) {
            this.db.close();
            this.db = null;
        }
        if (this.io != null) {
            this.io = null;
        }
    }


    public static void main(final String[] args) {
        final File f = new File("data");
        f.mkdirs();
        new File(f, "kv").delete();
        try {
            RocksDB db = RocksDB.open("data/kv");
            db.put("hello".getBytes(), "world".getBytes());
            final Snapshot snapshot = db.getSnapshot();
            db.close();

            db = RocksDB.openReadOnly("data/kv");
            final byte[] value = db.get("hello".getBytes());
            System.out.println(new String(value));
        } catch (final RocksDBException e) {
            e.printStackTrace();
        }
    }

}
