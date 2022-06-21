/**
 *  OAuthGithubGetAuth
 *  Copyright 20.06.2022 by Michael Peter Christen, @orbiterlab
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

package eu.searchlab.http.services.aaaaa;

import org.json.JSONObject;

import eu.searchlab.http.AbstractService;
import eu.searchlab.http.Service;
import eu.searchlab.http.ServiceRequest;
import eu.searchlab.http.ServiceResponse;

/**
 * OAuthGithubGetAuth
 * This class is called when a user wants to authorize with github.
 *
 * To configure this class, we need startup parameters in environment variables:
 * -Dgithub.client.id=...
 * -Dgithub.client.secret=...
 *
 * The configuration parameters can be set in
 * https://github.com/settings/applications/<application-id>
 *
 * example: call
 * http://localhost:8400/en/aaaaa/github/get_auth
 */
public class OAuthGithubGetAuth  extends AbstractService implements Service {

    @Override
    public String[] getPaths() {
        return new String[] {"/aaaaa/github/get_auth"};
    }

    @Override
    public ServiceResponse serve(final ServiceRequest serviceRequest) {
        final String login = serviceRequest.get("login", "");
        final String client_id = System.getProperty("github.client.id", "");

        // follow the process described in
        // https://docs.github.com/en/developers/apps/building-oauth-apps/authorizing-oauth-apps

        String state = "0" + Math.abs(("X" + System.currentTimeMillis()).hashCode()); // An unguessable random string. It is used to protect against cross-site request forgery attacks.
        // In case that we set callback.forward = true, we are in a development environment.
        final boolean callbackForward = serviceRequest.get("callback.forward", false); // must be false in production
        // Then we assign a special flag to the state attribute
        if (callbackForward) state = "callback.forward";

        // forward to github for authentication
        String url = "https://github.com/login/oauth/authorize?client_id=" + client_id
                + "&state=" + state
                + "&scope=user:email"; // see https://docs.github.com/en/developers/apps/building-oauth-apps/scopes-for-oauth-apps
        if (login.length() > 0) url += "&login=" + login;

        final JSONObject json = new JSONObject(true);
        final ServiceResponse serviceResponse = new ServiceResponse(json);
        serviceResponse.setFoundRedirect(url);
        return serviceResponse;
    }
}