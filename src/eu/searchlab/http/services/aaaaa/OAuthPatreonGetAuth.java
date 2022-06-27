/**
 *  OAuthPatreonGetAuth
 *  Copyright 27.06.2022 by Michael Peter Christen, @orbiterlab
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
 * OAuthPatreonGetAuth
 * This class is called when a user wants to authorize with github.
 *
 * To configure this class, we need startup parameters in environment variables:
 * -Dpatreon.client.id=...
 * -Dpatreon.client.secret=...
 *
 * The configuration parameters can be set in
 * https://www.patreon.com/portal/registration/register-clients
 *
 * example: call
 * http://localhost:8400/en/aaaaa/patreon_get_auth
 */
public class OAuthPatreonGetAuth  extends AbstractService implements Service {

    @Override
    public String[] getPaths() {
        return new String[] {"/aaaaa/patreon_get_auth"};
    }

    @Override
    public ServiceResponse serve(final ServiceRequest serviceRequest) {
        final String client_id = System.getProperty("patreon.client.id", "");

        // follow the process described in
        // https://docs.patreon.com/#step-1-registering-your-client

        String state = "0" + Math.abs(("X" + System.currentTimeMillis()).hashCode()); // An unguessable random string. It is used to protect against cross-site request forgery attacks.
        // In case that we set callback.forward = true, we are in a development environment.
        final boolean callbackForward = "true".equals(System.getProperty("callback.forward", "false")); // must be false in production
        // Then we assign a special flag to the state attribute
        if (callbackForward) state = OAuthGithubGetAuth.DEVELOPMENT_FORWARD_STATE;

        // forward to github for authentication
        final String url = "https://www.patreon.com/oauth2/authorize?client_id=" + client_id
                + "&redirect_uri=https%3A%2F%2Fsearchlab.eu%2Fen%2Faaaaa%2Fpatreon_callback%2Findex.html"
                + "&response_type=code"
                + "&state=" + state;

        final JSONObject json = new JSONObject(true);
        final ServiceResponse serviceResponse = new ServiceResponse(json);
        serviceResponse.setFoundRedirect(url);
        return serviceResponse;
    }
}
//https://searchlab.eu/en/aaaaa/patreon_callback