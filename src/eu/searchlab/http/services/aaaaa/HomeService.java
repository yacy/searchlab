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

import java.io.IOException;

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
import net.yacy.grid.io.index.ElasticsearchClient;
import net.yacy.grid.io.index.WebMapping;

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

    @SuppressWarnings("deprecation")
    @Override
    public ServiceResponse serve(final ServiceRequest serviceRequest) {
        // Read a session cookie which has a temporary ID that identifies the user
        // The user has a permanent ID which should be part of the url.
        // In case that the temporary ID can be used to retrieve a authentication record with the permanent ID
        // that is identical to the path-ID, then the user is not only authenticated but also authorized.
        final Authorization authorization = serviceRequest.getAuthorization();
        // If the authorization object exists, then the user is authorized;
        // We can use that object to get the user credentials
        final Authentication authentication = serviceRequest.getAuthentication();
        if (authentication == null) {
            // failed authentication & authorization
            // forward to logout
            final JSONObject json = new JSONObject(true);
            final ServiceResponse serviceResponse = new ServiceResponse(json);
            serviceResponse.setFoundRedirect("/" + serviceRequest.getUser() + "/logout/");
            return serviceResponse;
        }

        // read new settings
        final JSONObject post = serviceRequest.getPost();
        if (post.has("change")) {
            final String sponsor_github = post.optString("sponsor_github", null);
            if (sponsor_github != null) {
                authentication.setGithubSponsor(sponsor_github);
            }
            final String sponsor_patreon = post.optString("sponsor_patreon", null);
            if (sponsor_patreon != null) {
                authentication.setPatreonSponsor(sponsor_patreon);
            }
            final boolean self = "on".equals(post.optString("self", "off"));
            authentication.setSelf(self);

            try {
                Searchlab.userDB.setAuthentication(authentication);
            } catch (final IOException e1) {
                Logger.error("storing sponsor account", e1);
            }
        }

        if (post.has("delete")) {
            Searchlab.userDB.deleteAuthentication(authentication.getID());
            Logger.info("deleting userID: " + authorization.getUserID());

            // now do normal logout process to delete the cookie
            Searchlab.userDB.deleteAuthorization(authorization.getSessionID());
            // the account is gone forward to logout
            final JSONObject json = new JSONObject(true);
            final ServiceResponse serviceResponse = new ServiceResponse(json);
            // delete the user cookie
            serviceResponse.deleteUserIDCookie();
            return serviceResponse;
        }

        // all good, we respond with user credentials
        final JSONObject json = new JSONObject(true);
        final String index_name = System.getProperties().getProperty("grid.elasticsearch.indexName.web", ElasticsearchClient.DEFAULT_INDEXNAME_WEB);
        final long documents = Searchlab.ec.count(index_name, WebMapping.user_id_sxt.getMapping().name(), authentication.getID());
        final long collections = Searchlab.ec.aggregationCount(index_name, WebMapping.user_id_sxt.getMapping().name(), authentication.getID(), WebMapping.collection_sxt.getMapping().name());

        try {
            final JSONObject assets = new JSONObject(true);
            final JSONObject size = new JSONObject(true);
            assets.put("size", size);
            size.put("documents", documents);
            size.put("collections", collections);
            json.put("assets", assets);

            if (authorization != null) json.put("authorization", authorization.getJSON());
            json.put("authentication", authentication.getJSON());
            json.put("acl", serviceRequest.getACL());
            Logger.info("DEBUG json = " + json.toString(2));
        } catch (final JSONException e) {
            Logger.warn(e);
        }
        final ServiceResponse serviceResponse = new ServiceResponse(json);
        return serviceResponse;
    }
}