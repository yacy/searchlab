/**
 *  ServiceRequest
 *  Copyright 03.06.2022 by Michael Peter Christen, @orbiterlab
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

package eu.searchlab.http;

import java.io.IOException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import eu.searchlab.Searchlab;
import eu.searchlab.aaaaa.Authentication;
import eu.searchlab.aaaaa.Authorization;
import eu.searchlab.aaaaa.Authorization.Grade;
import io.undertow.server.handlers.Cookie;
import io.undertow.util.HeaderMap;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;


public class ServiceRequest {

    private final JSONObject post;
    private final String user; // the user_id
    private final String path; // this looks like "/js/jquery.min.js", a root path looks like "/"
    private final String query; // the part after "?"
    private final String ip_id;
    private final String ip_pseudonym;
    private final Cookie cookie;
    private final HeaderMap requestHeaders;
    private Authorization authorization = null;
    private Authentication authentication = null;

    public ServiceRequest(
            final JSONObject post,
            final String user,
            final String path,
            final String query,
            final String ip_id,
            final String ip_pseudonym,
            final Cookie cookie,
            final HeaderMap requestHeaders) {
        this.post = post == null ? new JSONObject() : post;
        this.user = user;
        this.path = path == null ? "" : path;
        this.query = query == null ? "" : query;
        this.ip_id = ip_id;
        this.ip_pseudonym = ip_pseudonym;
        this.cookie = cookie;
        this.requestHeaders = requestHeaders;
    }

    public String getUser() {
        return this.user;
    }

    public String getPath() {
        return this.path;
    }

    public String getQuery() {
        return this.query;
    }

    public String getIPID() {
        return this.ip_id;
    }

    public String getIP00() {
        return this.ip_pseudonym;
    }

    public JSONObject getPost() {
        return this.post;
    }

    public String get(final String key, final String dflt) {
        return this.post.optString(key, dflt);
    }

    public boolean get(final String key, final boolean dflt) {
        return this.post.optBoolean(key, dflt);
    }

    public int get(final String key, final int dflt) {
        return this.post.optInt(key, dflt);
    }

    public long get(final String key, final long dflt) {
        return this.post.optLong(key, dflt);
    }

    public double get(final String key, final double dflt) {
        return this.post.optDouble(key, dflt);
    }

    public String getCookieValue() {
        return this.cookie == null ? "" : this.cookie.getValue();
    }

    public Authorization getAuthorization() {
        if (this.authorization != null) return this.authorization;
        this.authorization = getAuthorizationInternal();
        return getAuthorizationInternal();
    }

    private Authorization getAuthorizationInternal() {
        final String cookie = this.getCookieValue();
        try {
            final JSONObject json = new JSONObject(new JSONTokener(cookie));
            final Authorization authorization = new Authorization(json);
            final String session_id = authorization.getSessionID();
            final Authorization stored_authorization = session_id == null ? null : Searchlab.userDB.getAuthorization(session_id);
            if (stored_authorization != null &&
                authorization.getSessionID().equals(stored_authorization.getSessionID()) &&
                authorization.getUserID().equals(stored_authorization.getUserID())) return authorization;
            return null;
        } catch (JSONException | IOException e) {
            return null; // no authorization
        }
    }

    /**
     * Get a user authentication:
     * A user is authenticated if the request path has an ID assigned.
     * The presence of the id does not give the user any other right than to identify the request with a
     * specific account. The id is a kind of tenant identifier. Everyone is allowed to ise that id,
     * i.e. if a link is shared.
     * @return
     */
    public Authentication getAuthentication() {
        if (this.authentication != null) return this.authentication;
        this.authentication = getAuthenticationInternal();
        return this.authentication;
    }

    private Authentication getAuthenticationInternal() {
        final Authorization authorization = getAuthorization();
        if (authorization == null) {
            if (this.user.equals("en")) return null;
            return new Authentication(this.user);
        } else {
            // the user id must be stored already
            return Searchlab.userDB.getAuthentiationByID(authorization.getUserID());
        }
    }

    public String getHeader(final String name, final String dflt) {
        if (this.requestHeaders == null) return dflt;
        final HeaderValues val = this.requestHeaders.get(name);
        return val == null ? dflt : val.getFirst();
    }

    public boolean hasReferer() {
        final String referer = getHeader(Headers.REFERER_STRING, null);
        if (referer == null) return false;
        return referer.length() > 0;
    }

    public String getReferer() {
        final String referer = getHeader(Headers.REFERER_STRING, null);
        return referer == null ? "" : referer;
    }

    public boolean isAuthorized() {
        return getAuthorization() != null;
    }

    public Grade getAuthorizationGrade() {
        if (this.user == null || this.user.length() <= 2 || !Authentication.isValid(this.user)) return Grade.L00_Everyone;
        if (!isAuthorized()) return Grade.L01_Anonymous;
        if (Authorization.maintainers.contains(getUser())) return Grade.L08_Maintainer;
        return Grade.L02_Authenticated;
    }

    public JSONObject getACL() {
        final Grade grade = getAuthorizationGrade();
        final JSONObject acl = Authorization.getACL();
        final JSONArray levels = acl.optJSONArray("levels");
        for (int i = 0; i < levels.length(); i++) {
            final JSONObject level = levels.optJSONObject(i);
            final String levelname = level.optString("level");
            if (levelname.equals(grade.name())) return level;
        }
        return levels.optJSONObject(0);
    }
}
