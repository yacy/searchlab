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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import eu.searchlab.storage.io.AbstractIO;
import eu.searchlab.storage.io.GenericIO;
import eu.searchlab.storage.io.IOPath;

public abstract class AbstractCord implements Cord {

    private static final char LF = (char) 10; // we don't use '\n' or System.getProperty("line.separator"); here to be consistent over all systems.

    protected final Object mutex; // Object on which to synchronize
    protected final GenericIO io;
    protected final IOPath iop;
    protected JSONArray array;

    protected AbstractCord(GenericIO io, IOPath iop) {
        this.io = io;
        this.iop = iop;
        this.array = null;
        this.mutex = this;
    }

    public static void write(OutputStream os, JSONArray array) throws IOException {
        if (array == null) throw new IOException("array must not be null");
        OutputStreamWriter writer = new OutputStreamWriter(os, StandardCharsets.UTF_8);
        writer.write('[');
        writer.write(LF);
        int p = 0;
        for (int i = 0; i < array.length(); i++) {
            Object obj;
            try {
                obj = array.get(i);
            } catch (JSONException e) {
                continue;
            }
            if (obj == null) continue;
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
            if (i < array.length() - 1) writer.write(',');
            writer.write(LF);
        }
        writer.write(']');
        writer.close();
    }

    public static JSONArray read(InputStream is) throws IOException {
        JSONArray array = new JSONArray();
        // The file can be written in either of two ways:
        // - as a simple toString() or toString(2) from a JSONObject
        // - as a list of properties, one in each line with one line in the front starting with "{" and one in the end, starting with "}"
        // If the file was written in the first way, all property keys must be unique because we must use the JSONTokener to parse it.
        // if the file was written in the second way, we can apply a reader which reads the file line by line, overwriting a property
        // if it appears a second (third..) time. This has a big advantage: we can append new properties just at the end of the file.
        byte[] b = AbstractIO.readAll(is, -1);
        if (b.length == 0) return array;
        // check which variant is in b[]:
        // in a toString() output, there is no line break
        // in a toString(2) output, there is a line break but also two spaces in front
        // in a line-by-line output, there is a line break at b[1] and no spaces after that because the first char starts the object
        boolean lineByLine = (b.length == 3 && b[0] == '[' && b[1] == LF && b[2] == ']') || (b[1] < ' ' && b[2] != ' ' && b[3] != ' ');
        ByteArrayInputStream bais = new ByteArrayInputStream(b);
        if (lineByLine) {
            int a = bais.read();
            assert (a == '[');
            BufferedReader reader = new BufferedReader(new InputStreamReader(bais, "UTF-8"));
            String line;
            try {
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.equals("]")) break;
                    if (line.length() == 0) continue;
                    if (line.endsWith(",")) line = line.substring(0, line.length() - 1);
                    if (line.charAt(0) == '{') {
                        array.put(new JSONObject(new JSONTokener(line)));
                    } else if (line.charAt(0) == '[') {
                        array.put(new JSONArray(new JSONTokener(line)));
                    } else if (line.charAt(0) == '"') {
                        array.put(line.substring(1, line.length() - 1));
                    } else if (line.indexOf('.') > 0) {
                        try {
                            array.put(Double.parseDouble(line));
                        } catch (NumberFormatException e) {
                            array.put(line);
                        }
                    } else {
                        try {
                            array.put(Long.parseLong(line));
                        } catch (NumberFormatException e) {
                            array.put(line);
                        }
                    }
                }
            } catch (JSONException e) {
                throw new IOException(e);
            }
        } else {
            try {
                array = new JSONArray(new JSONTokener(new InputStreamReader(bais, StandardCharsets.UTF_8)));
            } catch (JSONException e) {
                // could be a double key problem. In that case we should repeat the process with another approach
                throw new IOException(e);
            }
        }
        return array;
    }

    protected Cord commitInternal() throws IOException {
        this.io.write(this.iop, this.array.toString().getBytes(StandardCharsets.UTF_8));
        return this;
    }

    protected void ensureLoaded() throws IOException {
        if (this.array == null) {
            try {
                this.array = new JSONArray(new JSONTokener(new InputStreamReader(this.io.read(this.iop), StandardCharsets.UTF_8)));
            } catch (JSONException e) {
                throw new IOException(e.getMessage());
            }
        }
    }

    @Override
    public IOPath getObject() {
        return this.iop;
    }

    @Override
    public int size() {
        synchronized (this.mutex) {
            try {
                ensureLoaded();
            } catch (IOException e) {
                return 0;
            }
            return this.array.length();
        }
    }

    @Override
    public boolean isEmpty() {
        return size() > 0;
    }

    @Override
    public JSONArray toJSON() throws IOException {
        synchronized (this.mutex) {
            ensureLoaded();
            return this.array;
        }
    }

    @Override
    public int hashCode() {
        return this.iop.hashCode();
    }

}
