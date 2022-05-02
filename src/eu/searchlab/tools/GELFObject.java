/**
 *  GELFObject
 *  Copyright 02.05.2022 by Michael Peter Christen, @orbiterlab
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

import java.io.IOException;
import java.util.Iterator;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

public class GELFObject extends JSONObject {

    public final static String VERSION = "version";
    public final static String HOST = "host";
    public final static String SHORT_MESSAGE = "short_message";
    public final static String FULL_MESSAGE = "full_message";
    public final static String TIMESTAMP = "timestamp";
    public final static String LEVEL = "level";

    public GELFObject(final String gelf) {
        super(true);
        try {
            final JSONObject json = new JSONObject(new JSONTokener(gelf));
            this.init(json);
        } catch (final JSONException e) {
            this.init("null", gelf, System.currentTimeMillis());
        }
    }

    public GELFObject(final JSONObject json) throws JSONException {
        super(true);
        this.init(json);
    }

    public GELFObject(final String host, final String short_message) {
        super(true);
        this.init(host, short_message, System.currentTimeMillis());
    }

    public GELFObject(final String host, final String short_message, final long timestamp) {
        super(true);
        this.init(host, short_message, timestamp);
    }

    public GELFObject(final String host, final String short_message, final String full_message) {
        super(true);
        this.init(host, short_message, full_message);
    }

    @SuppressWarnings("deprecation")
    private void init(final JSONObject json) throws JSONException {
        super.putAll(json);
        if (!this.has(VERSION)) throw new JSONException("GELF message not well-formed: missing " + VERSION + " attribute");
        if (!this.has(HOST)) throw new JSONException("GELF message not well-formed: missing " + HOST + " attribute");
        if (!this.has(SHORT_MESSAGE)) throw new JSONException("GELF message not well-formed: missing " + SHORT_MESSAGE + " attribute");

        if (this.has(TIMESTAMP)) {
            final Object t = this.get(TIMESTAMP);
            if (t instanceof Float) this.put(TIMESTAMP, ((Float) t).longValue());
            if (t instanceof Double) this.put(TIMESTAMP, ((Double) t).longValue());
        } else {
            this.put(TIMESTAMP, System.currentTimeMillis());
        }

        // now clean the message: ignore all deprecate field and keep only required, optional and additional keys
        final Iterator<String> keys = this.keys();
        while (keys.hasNext()) {
            final String key = keys.next();
            if (VERSION.equals(key)) continue;
            if (HOST.equals(key)) continue;
            if (SHORT_MESSAGE.equals(key)) continue;
            if (FULL_MESSAGE.equals(key)) continue;
            if (TIMESTAMP.equals(key)) continue;
            if (LEVEL.equals(key)) continue;
            if (key.length() > 0 && key.charAt(0) == '_') continue;
            // we clean up all non-valid fields
            keys.remove();
        }
    }

    private void init(final String host, final String short_message, final long timestamp) {
        try {
            this.put(VERSION, "1.1");
            this.put(HOST, host);
            this.put(TIMESTAMP, timestamp);
            this.put(SHORT_MESSAGE, short_message);
        } catch (final JSONException e) {
            Logger.error(e);
        }
    }

    private void init(final String host, final String short_message, final String full_message) {
        try {
            this.put(VERSION, "1.1");
            this.put(HOST, host);
            this.put(TIMESTAMP, System.currentTimeMillis());
            this.put(SHORT_MESSAGE, short_message);
            this.put(FULL_MESSAGE, full_message);
        } catch (final JSONException e) {
            Logger.error(e);
        }
    }

    public GELFObject setFullMessage(final String full_message) {
        try {
            this.put(FULL_MESSAGE, full_message);
        } catch (final JSONException e) {
            Logger.error(e);
        }
        return this;
    }

    public GELFObject setLevel(final int level) {
        try {
            this.put(LEVEL, level);
        } catch (final JSONException e) {
            Logger.error(e);
        }
        return this;
    }

    public String getVersion() {
        try {
        	return this.getString(VERSION);
        } catch (final JSONException e) {
            Logger.error(e);
            return "";
        }
    }

    public String getHost() {
        try {
            return this.getString(HOST);
        } catch (final JSONException e) {
            Logger.error(e);
            return "";
        }
    }

    public String getShortMessage() {
        try {
            return this.getString(SHORT_MESSAGE);
        } catch (final JSONException e) {
            Logger.error(e);
            return "";
        }
    }

    public String getFullMessage() {
        return this.optString(FULL_MESSAGE, null);
    }

    public long getTimestamp() {
        return this.optLong(TIMESTAMP, System.currentTimeMillis());
    }

    public int getLevel() {
        return this.optInt(LEVEL, 1);
    }

    public String getAdditional(final String key) {
        assert key.length() > 0 && key.charAt(0) == '_';
        return this.optString(key, null);
    }

    public void send() throws JSONException, IOException {
        new UDPClient().send(this.toString(0));
    }

    public void send(final int port) throws JSONException, IOException {
        new UDPClient(port).send(this.toString(0));
    }

    public void send(final String host, final int port) throws JSONException, IOException {
        new UDPClient(host, port).send(this.toString(0));
    }

}
