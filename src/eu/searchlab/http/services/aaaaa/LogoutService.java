/**
 *  Logout
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

import eu.searchlab.Searchlab;
import eu.searchlab.aaaaa.Authentication;
import eu.searchlab.aaaaa.Authorization;
import eu.searchlab.http.AbstractService;
import eu.searchlab.http.Service;
import eu.searchlab.http.ServiceRequest;
import eu.searchlab.http.ServiceResponse;
import eu.searchlab.tools.Logger;

/**
 * Logout
 * This class is called when a user wants to give up authorization and authentication.
 *
 * Example call:
 * http://localhost:8400/logout/
 */
public class LogoutService  extends AbstractService implements Service {

    @Override
    public String[] getPaths() {
        return new String[] {"/logout/"};
    }

    @Override
    public ServiceResponse serve(final ServiceRequest serviceRequest) {
        final JSONObject json = new JSONObject(true);
        final ServiceResponse serviceResponse = new ServiceResponse(json);

        // in case that a user is still logged in, check who we are logging out
        final String user = serviceRequest.getUser();
        Logger.info("logging out user: " + user);

        // check if cookie exist
        final Authorization authorization = serviceRequest.getAuthorization(); // read possible cookie
        if (authorization != null) {
            // delete the user authorization
            Searchlab.userDB.deleteAuthorization(authorization.getSessionID());
            Logger.info("logging out userID: " + authorization.getUserID());
        }

        final Authentication authentication = authorization == null ? null : Searchlab.userDB.getAuthentiationByID(authorization.getUserID());
        if (authentication != null) {
            Logger.info("logging out email:" + authentication.getEmailPseudonymized());
        }

        // delete the user cookie
        serviceResponse.deleteUserIDCookie();
        return serviceResponse;
    }
}