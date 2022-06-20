/**
 *  OAuthGithubCallback
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
 * OAuthGithubCallback
 * This class is called after a user has (tried to) authenticate with github,
 * and github redirects to this callback address.
 * From here we should know the access credentials to call the github api
 * to get the user authentication details.
 */
public class OAuthGithubCallback  extends AbstractService implements Service {

    @Override
    public String[] getPaths() {
        return new String[] {"/api/aaaaa/github/callback"};
    }

    @Override
    public ServiceResponse serve(final ServiceRequest serviceRequest) {
    	final String code = serviceRequest.get("code", "");
    	final String state = serviceRequest.get("state", "");

    	// after evaluation of this code, we redirect again to a page where we tell the user that the log-in actually happened.
        final JSONObject json = new JSONObject(true);
        final ServiceResponse serviceResponse = new ServiceResponse(json);
        serviceResponse.setFoundRedirect("https://searchlab.eu/" + serviceRequest.getUser() + "/aaaaa/login");
        return serviceResponse;
    }
}