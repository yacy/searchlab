/**
 *  JSONList
 *  (C) 3.7.2017 by Michael Peter Christen; mc@yacy.net, @orbiterlab
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

package eu.searchlab.tools;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

/**
 * A JSONList is an object which represents a list of json objects: that is
 * a popular file format which is used as elasticsearch bulk index format.
 * A jsonlist file is a text file where every line is a json object.
 * To bring a JSONArray into jsonlist format you have to do
 * - make sure that every element in the JSONArray is JSONObject
 * - print out every json in the list without indentation
 */
public class JSONList implements Iterable<Object> {

    private final JSONArray array;

    public JSONList() {
        this.array = new JSONArray();
    }

    public JSONList(final InputStream sourceStream) throws IOException {
        this();
        final BufferedReader br = new BufferedReader(new InputStreamReader(sourceStream, StandardCharsets.UTF_8));
        String line;
        try {
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.length() == 0) continue;
                final JSONObject json = new JSONObject(new JSONTokener(line));
                this.add(json);
            }
        } catch (final JSONException e) {
            throw new IOException(e);
        }
    }

    public JSONList(final JSONArray a) throws IOException {
        for (int i = 0; i < a.length(); i++) {
            try {
                if (!(a.get(i) instanceof JSONObject)) throw new IOException("all objects in JSONArray must be JSONObject");
            } catch (final JSONException e) {
                throw new IOException(e.getMessage());
            }
        };
        this.array = a;
    }

    public JSONList(final byte[] b) throws IOException {
        this(new ByteArrayInputStream(b));
    }

    public JSONList(final String jsonlist) throws IOException {
        this(jsonlist.getBytes(StandardCharsets.UTF_8));
    }

    public JSONList add(final JSONObject object) {
        this.array.put(object);
        return this;
    }

    public JSONObject get(final int i) throws JSONException {
        return this.array.getJSONObject(i);
    }

    public int length() {
        return this.array.length();
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer();
        this.array.forEach(entry -> sb.append(entry.toString()).append("\n"));
        return sb.toString();
    }

    public void write(final OutputStream os) throws IOException {
        OutputStreamWriter osw = new OutputStreamWriter(os, StandardCharsets.UTF_8);
        write(osw);
        osw.close();
    }

    public void write(final Writer writer) throws IOException {
        for (final Object entry: this.array) {
            writer.write(entry.toString());
            writer.write("\n");
        }
    }

    public void write(final File file) throws UnsupportedEncodingException, FileNotFoundException, IOException {
        final FileOutputStream fos = new FileOutputStream(file);
        final OutputStreamWriter osw = new OutputStreamWriter(fos, "UTF-8");
        write(osw);
        osw.close();
        fos.close();
    }

    public JSONArray toArray() {
        return this.array;
    }

    @Override
    public Iterator<Object> iterator() {
        return this.array.iterator();
    }
}
