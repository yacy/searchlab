/**
 *  Tray
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
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import eu.searchlab.storage.io.IOPath;

/**
 * A tray is a storage location for JSON objects within a single file which
 * can be stored with GenericIO and thus must be identified with an IOPath.
 * Implementations can be
 * - a persisten Tray (write operations into the tray cause write operations to IO)
 * - a volatile Tray (write operations are buffered and are only written with a commit or close)
 * - on-demand Trays (both options above, but only instantiated if a request is required)
 */
public interface Tray {

    public IOPath getObject();

    /**
     * get the number of objects within this tray
     * @return
     */
    public int count();

    /**
     * return true if the tray is empty
     * @return
     */
    public boolean isEmpty();

    /**
     * get a set of all keys in the tray
     * @return
     * @throws IOException
     */
    public Set<String> keys() throws IOException;

    /**
     * put an object to the tray
     * @param key
     * @param value
     * @return
     * @throws IOException
     */
    public Tray put(String key, JSONObject value) throws IOException;

    /**
     * put an array to the tray
     * @param key
     * @param value
     * @return
     * @throws IOException
     */
    public Tray put(String key, JSONArray value) throws IOException;

    /**
     * remove the array/object denoted by the key from the tray.
     * @param key
     * @return
     * @throws IOException
     */
    public Tray remove(String key) throws IOException;

    /**
     * Read one object from the tray. If the key denotes not an object,
     * an exception is thrown.
     * If no object with the name is known, null is returned;
     * @param key
     * @return
     * @throws IOException
     */
    public JSONObject getObject(String key) throws JSONException, IOException;

    /**
     * Read one object from the tray. If the key denotes not an array,
     * an exception is thrown.
     * If no array with the name is known, null is returned;
     * @param key
     * @return
     * @throws IOException
     */
    public JSONArray getArray(String key) throws JSONException, IOException;

    /**
     * Translate the whole tray into JSONObject.
     * For most trays this is equivalent to return the buffered storage object.
     * Note that such objects must not be altered to prevent side-effects to the
     * implementing tray class.
     * @return
     * @throws IOException
     */
    public JSONObject toJSON() throws IOException;

    /**
     * hash codes of trays must be identical with the hash code of the object path hash
     * @return
     */
    @Override
    public int hashCode();

    /**
     * a commit must be done for all trays which buffer write operations in RAM
     * @return
     * @throws IOException
     */
    public Tray commit() throws IOException;

    /**
     * a close must be called to free resources and to flush unwritten buffered data
     * @throws IOException
     */
    public void close() throws IOException;

}
