/**
 *  OAuthGithubLogin
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

import org.json.JSONException;
import org.json.JSONObject;

import eu.searchlab.Searchlab;
import eu.searchlab.aaaaa.Authentication;
import eu.searchlab.aaaaa.Authorization;
import eu.searchlab.http.AbstractService;
import eu.searchlab.http.Service;
import eu.searchlab.http.ServiceRequest;
import eu.searchlab.http.ServiceResponse;
import eu.searchlab.tools.Logger;

/**
 * OAuthGithubLogin
 * This class is called when a user is authorized and authenticated.
 * The class is called when OAuthGithubCallback was executed and the github API was called
 * successfully afterwards.
 *
 * Example call:
 * http://localhost:8400/en/homes
 */
public class HomeService  extends AbstractService implements Service {

    @Override
    public String[] getPaths() {
        return new String[] {"/home/"};
    }

    @Override
    public ServiceResponse serve(final ServiceRequest serviceRequest) {
        // Read a session cookie which has a temporary ID that identifies the user
        // The user has a permanent ID which should be part of the url.
        // In case that the temporary ID can be used to retrieve a authentication record with the permanent ID
        // that is identical to the path-ID, then the user is not only authenticated but also authorized.
        final Authorization authorization = serviceRequest.getAuthorization();
        serviceRequest.isAuthorized();
        // If the authorization object exists, then the user is authorized;
        // We can use that object to get the user credentials
        final Authentication authentication = authorization == null ? null : Searchlab.userDB.getAuthentiationByID(authorization.getUserID());
        if (authentication == null) {
            // failed authentication & authorization
            // forward to logout
            final JSONObject json = new JSONObject(true);
            final ServiceResponse serviceResponse = new ServiceResponse(json);
            serviceResponse.setFoundRedirect("/" + serviceRequest.getUser() + "/logout/");
            return serviceResponse;
        }

        // all good, we respond with user credentials
        final JSONObject json = authentication.getJSON();
        try {
            json.put("authentication", authentication.getJSON());
        } catch (final JSONException e) {
            Logger.warn(e);
        }
        final ServiceResponse serviceResponse = new ServiceResponse(json);
        return serviceResponse;
    }
}