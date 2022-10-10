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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.FileSystems;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import eu.searchlab.tools.Logger;

public class Authorization {

    public final static Set<String> maintainers = ConcurrentHashMap.newKeySet();

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
        L03_Level_One(3),
        L04_Level_Five(4),
        L05_Level_Twentyfive(5),
        L06_Level_Fifty(6),
        L07_Level_Twohundred(7),
        L08_Maintainer(8);

        public int level;

        private Grade(final int level) {
            this.level = level;
        }
    }

    private static JSONObject acl = null;

    public static JSONObject getACL() {
        if (acl != null) return acl;

        // load acl
        final File conf_dir = FileSystems.getDefault().getPath("conf").toFile();
        final File f = new File(conf_dir, "acl.json");
        try {
            final Reader reader = new InputStreamReader(new FileInputStream(f));
            acl = new JSONObject(new JSONTokener(reader));
            return acl;
        } catch (IOException | JSONException e) {
            Logger.error(e);
            return new JSONObject();
        }
    }

    public Authorization(final JSONObject json) {
        this.json = json;
    }

    public Authorization(final String id) throws IOException {
        this.json = new JSONObject(true);
        final JSONObject authorization = new JSONObject(true);
        final String session = Authentication.generateRandomID() + Authentication.generateRandomID();

        authorization.put("session", session);
        authorization.put("id", id);
        this.json.put("authorization", authorization);
        this.json.put("signature", hashgen(authorization));
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
        return this.json.toString(2);
    }

}
