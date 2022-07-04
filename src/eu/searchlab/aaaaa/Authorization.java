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
import java.util.HashSet;
import java.util.Set;

import org.json.JSONException;
import org.json.JSONObject;

public class Authorization {

    public final static Set<String> maintainers = new HashSet<>();

    static {
        final String authorizationMaintainer = System.getProperty("authorization.maintainer", "");
        for (final String s: authorizationMaintainer.split(",")) {
            final String id = s.trim();
            if (Authentication.isValid(id)) maintainers.add(id);
        }
    }

    public JSONObject json;

    public static enum Grade {
        L00_Everyone(0),
        L01_Anonymous(1),
        L02_Authenticated(2),
        L03_Primary(3),
        L04_Level_One(4),
        L05_Level_Five(5),
        L06_Level_Twentyfive(6),
        L07_Level_Fifty(7),
        L08_Level_Twohundred(8),
        L09_Maintainer(9);

        public int level;

        private Grade(final int level) {
            this.level = level;
        }
    }

    public Authorization(final JSONObject json) {
        this.json = json;
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
