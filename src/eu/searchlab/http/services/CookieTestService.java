/**
 *  CookieTestService
 *  Copyright 07.06.2022 by Michael Peter Christen, @orbiterlab
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


package eu.searchlab.http.services;

import org.json.JSONObject;

import eu.searchlab.http.Service;
import eu.searchlab.http.ServiceRequest;
import eu.searchlab.http.ServiceResponse;

public class CookieTestService  extends AbstractService implements Service {

    @Override
    public String[] getPaths() {
        return new String[] {"/api/cookie.json"};
    }

    @Override
    public ServiceResponse serve(final ServiceRequest serviceRequest) {
        final String value = serviceRequest.get("value", "");
        final JSONObject json = new JSONObject(true);
        final ServiceResponse serviceResponse = new ServiceResponse(json);
        if (value.length() > 0) {
            if (value.equals("delete"))
                serviceResponse.deleteCookie("test");
            else
                serviceResponse.addSessionCookie("test", value);
        }
        return serviceResponse;
    }
}