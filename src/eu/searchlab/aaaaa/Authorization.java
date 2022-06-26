/**
 *  Authorization
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

package eu.searchlab.aaaaa;

import java.io.IOException;

import org.json.JSONException;
import org.json.JSONObject;

public class Authorization {

    private final JSONObject json;

    public Authorization(final JSONObject json) throws IOException {
        this.json = json;
        if (!isValid()) throw new IOException("cookie is not valid");
    }

    public Authorization(final String id) throws IOException {
        this.json = new JSONObject(true);
        final JSONObject authorization = new JSONObject(true);
        final String session = Authentication.generateRandomID() + Authentication.generateRandomID();

        try {
            authorization.put("session", session);
            authorization.put("id", id);
            this.json.put("authorization", authorization);
            this.json.put("signature", hashgen(authorization));
        } catch (final JSONException e) {
            throw new IOException(e.getMessage());
        }
    }

    private static String hashgen(final JSONObject json) {
        return Long.toHexString(Math.abs(json.toString().hashCode()));
    }

    public boolean isValid() {
        final JSONObject authorization = this.json.optJSONObject("authorization");
        if (authorization == null) return false;
        final String signature = this.json.optString("signature");
        if (signature == null) return false;
        return signature.equals(hashgen(authorization));
    }

    public String getUserID() throws RuntimeException {
        try {
            final JSONObject a = this.json.optJSONObject("authorization");
            return a.getString("id");
        } catch (final JSONException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    public String getSessionID() throws RuntimeException {
        try {
            final JSONObject a = this.json.optJSONObject("authorization");
            return a.getString("session");
        } catch (final JSONException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    public JSONObject getJSON() {
        return this.json;
    }

    @Override
    public String toString() {
        try {
            return this.json.toString(2);
        } catch (final JSONException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

}
