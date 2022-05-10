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

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import eu.searchlab.storage.io.AbstractIO;
import eu.searchlab.storage.io.ConcurrentIO;
import eu.searchlab.storage.io.IOPath;

public abstract class AbstractTray implements Tray {

    private static final char LF = (char) 10; // we don't use '\n' or System.getProperty("line.separator"); here to be consistent over all systems.

    protected final Object mutex; // Object on which to synchronize
    protected final ConcurrentIO io;
    protected final IOPath iop;
    protected JSONObject object;

    /**
     *
     * @param io
     * @param iop
     * @param lineByLineStorage if true, each property in the object is written to a single line. If false, the file is pretty-printed
     */
    protected AbstractTray(final ConcurrentIO io, final IOPath iop) {
        this.io = io;
        this.iop = iop;
        this.object = null;
        this.mutex = this;
    }

    public static void write(final OutputStream os, final JSONObject json) throws IOException {
        if (json == null) throw new IOException("json must not be null");
        final OutputStreamWriter writer = new OutputStreamWriter(os, StandardCharsets.UTF_8);
        writer.write('{');
        writer.write(LF);
        final String[] keys = new String[json.length()];
        int p = 0;
        for (final String key: json.keySet()) keys[p++] = key; // we do this only to get a hint which key is the last one
        for (int i = 0; i < keys.length; i++) {
            final String key = keys[i];
            final Object obj = json.opt(key);
            if (obj == null) continue;
            writer.write('"'); writer.write(key); writer.write('"'); writer.write(':');
            if (obj instanceof JSONObject) {
                writer.write(((JSONObject) obj).toString());
            } else if (obj instanceof Map) {
                writer.write(new JSONObject((Map<?,?>) obj).toString());
            } else if (obj instanceof JSONArray) {
                writer.write(((JSONArray) obj).toString());
            } else if (obj instanceof Collection) {
                writer.write(new JSONArray((Collection<?>) obj).toString());
            } else if (obj instanceof String) {
                writer.write('"'); writer.write(((String) obj)); writer.write('"');
            } else {
                writer.write(obj.toString());
            }
            if (i < keys.length - 1) writer.write(',');
            writer.write(LF);
        }
        writer.write('}');
        writer.close();
    }

    public static JSONObject read(final File f) throws IOException {
        final InputStream bis = new BufferedInputStream(new FileInputStream(f));
        final JSONObject j = read(bis);
        bis.close();
        return j;
    }

    public static JSONObject read(final InputStream is) throws IOException {
        JSONObject json = new JSONObject(true);
        // The file can be written in either of two ways:
        // - as a simple toString() or toString(2) from a JSONObject
        // - as a list of properties, one in each line with one line in the front starting with "{" and one in the end, starting with "}"
        // If the file was written in the first way, all property keys must be unique because we must use the JSONTokener to parse it.
        // if the file was written in the second way, we can apply a reader which reads the file line by line, overwriting a property
        // if it appears a second (third..) time. This has a big advantage: we can append new properties just at the end of the file.
        final byte[] b = AbstractIO.readAll(is, -1);
        if (b.length == 0) return json;
        // check which variant is in b[]:
        // in a toString() output, there is no line break
        // in a toString(2) output, there is a line break but also two spaces in front
        // in a line-by-line output, there is a line break at b[1] and no spaces after that because the first char is a '"' and the second a letter
        final boolean lineByLine = (b.length == 3 && b[0] == '{' && b[1] == LF && b[2] == '}') || (b[1] < ' ' && b[2] != ' ' && b[3] != ' ');
        final ByteArrayInputStream bais = new ByteArrayInputStream(b);
        if (lineByLine) {
            final int a = bais.read();
            assert (a == '{');
            final BufferedReader reader = new BufferedReader(new InputStreamReader(bais, "UTF-8"));
            String line;
            try {
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.equals("}")) break;
                    if (line.length() == 0) continue;
                    final int p = line.indexOf("\":");
                    if (p < 0) continue;
                    final String key = line.substring(1, p).trim();
                    String value = line.substring(p + 2).trim();
                    if (value.endsWith(",")) value = value.substring(0, value.length() - 1);
                    if (value.charAt(0) == '{') {
                        json.put(key, new JSONObject(new JSONTokener(value)));
                    } else if (value.charAt(0) == '[') {
                        json.put(key, new JSONArray(new JSONTokener(value)));
                    } else if (value.charAt(0) == '"') {
                        json.put(key, value.substring(1, value.length() - 1));
                    } else if (value.indexOf('.') > 0) {
                        try {
                            json.put(key, Double.parseDouble(value));
                        } catch (final NumberFormatException e) {
                            json.put(key, value);
                        }
                    } else {
                        try {
                            json.put(key, Long.parseLong(value));
                        } catch (final NumberFormatException e) {
                            json.put(key, value);
                        }
                    }
                }
            } catch (final JSONException e) {
                throw new IOException(e);
            }
        } else {
            try {
                json = new JSONObject(new JSONTokener(new InputStreamReader(bais, StandardCharsets.UTF_8)));
            } catch (final JSONException e) {
                // could be a double key problem. In that case we should repeat the process with another approach
                throw new IOException(e);
            }
        }
        return json;
    }

    protected final static long TIMEOUT = 10000;

    protected Tray commitInternal() throws IOException {
        // now write our data back
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        write(baos, this.object);
        final byte[] a = baos.toByteArray();
        baos.close();
        this.io.writeForced(this.iop, a, TIMEOUT);
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
    	final byte[] a = this.io.readForced(this.iop, TIMEOUT);
    	final ByteArrayInputStream bais = new ByteArrayInputStream(a);
    	final JSONObject json = read(bais);
        bais.close();
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
    public JSONObject getObject(final String key) throws IOException {
        synchronized (this.mutex) {
            ensureLoaded();
            return this.object.optJSONObject(key);
        }
    }

    @Override
    public JSONArray getArray(final String key) throws IOException {
        synchronized (this.mutex) {
            ensureLoaded();
            return this.object.optJSONArray(key);
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
