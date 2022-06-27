/**
 *  OAuthPatreonCallback
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import eu.searchlab.Searchlab;
import eu.searchlab.aaaaa.Authentication;
import eu.searchlab.aaaaa.Authorization;
import eu.searchlab.http.AbstractService;
import eu.searchlab.http.Service;
import eu.searchlab.http.ServiceRequest;
import eu.searchlab.http.ServiceResponse;
import eu.searchlab.http.WebServer;
import eu.searchlab.tools.Logger;

/**
 * OAuthPatreonCallback
 * This class is called after a user has (tried to) authenticate with patreon,
 * and patreon redirects to this callback address.
 * From here we should know the access credentials to call the patreon api
 * to get the user authentication details.
 *
 * example: call
 * http://localhost:8400/en/aaaaa/patreon_callback
 */
public class OAuthPatreonCallback  extends AbstractService implements Service {

    @Override
    public String[] getPaths() {
        return new String[] {"/aaaaa/patreon_callback", "/aaaaa/patreon_callback/index.html"};
    }

    @Override
    public ServiceResponse serve(final ServiceRequest serviceRequest) {
        Logger.info("serviceRequest: " + serviceRequest.getPost().toString());
        // {'error':'invalid_scope','state':'development.forward','USER':'273584169','PATH':'\/aaaaa\/patreon_callback\/','QUERY':'error=invalid_scope&state=development.forward'}"
        final String code = serviceRequest.get("code", "");
        final String state = serviceRequest.get("state", "");
        final JSONObject json = new JSONObject(true);

        // In case that we set callback.forward = true, we are in a development environment.
        // Then we produce an additional forwarding to localhost. Patreon will always call the
        // production instance, but that instance supports development by forwarding.
        // To switch on that case, we hand over the state "development.forward"
        final boolean callbackForward = "true".equals(System.getProperty("callback.forward", "false"));
        Logger.info("Catched callback from patreon; state = " + state + "; callbackForward = " + callbackForward);
        // to prevent that in development environments the call is executed forever, we must check the forward flag here as well
        if (!callbackForward && OAuthGithubGetAuth.DEVELOPMENT_FORWARD_STATE.equals(state)) {
            Logger.info("catched callback for development, forwarding to localhost");
            final ServiceResponse serviceResponse = new ServiceResponse(json);
            serviceResponse.setFoundRedirect("http://localhost:8400/en/aaaaa/patreon_callback/?code=" + code + "&state=" + state);
            return serviceResponse;
        }


        // follow the process described in
        // https://docs.patreon.com/en/developers/apps/building-oauth-apps/authorizing-oauth-apps
        // we now have a code that we can use to access the patreon API
        // POST https://patreon.com/login/oauth/access_token

        final String client_id = System.getProperty("patreon.client.id", "");
        final String client_secret = System.getProperty("patreon.client.secret", "");
        String userPatreonLogin = "";
        String userName = "";
        String userEmail = "";
        final String userLocationName = "";
        final String userTwitterUsername = "";

        try {
            final HttpClient httpclient = HttpClients.createDefault();
            final HttpPost httppost = new HttpPost("https://www.patreon.com/api/oauth2/token");

            final List<NameValuePair> params = new ArrayList<>(3);
            params.add(new BasicNameValuePair("code", code));
            params.add(new BasicNameValuePair("grant_type", "authorization_code"));
            params.add(new BasicNameValuePair("client_id", client_id));
            params.add(new BasicNameValuePair("client_secret", client_secret));
            params.add(new BasicNameValuePair("redirect_uri", OAuthPatreonGetAuth.redirect_uri));

            httppost.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));
            httppost.addHeader("Content-Type", "application/x-www-form-urlencoded");
            HttpResponse response = httpclient.execute(httppost);
            HttpEntity entity = response.getEntity();

            if (entity != null) {
                // the response is a JSON
                final String j = new BufferedReader(new InputStreamReader(entity.getContent())).lines().collect(Collectors.joining("\n"));
                final JSONObject receipt = new JSONObject(new JSONTokener(j));
                Logger.info("receipt = " + receipt.toString());
                final String access_token = receipt.optString("access_token");

                Logger.info("Access Token is " + access_token);

                // read the user information
                // curl --request GET \
                //      --url https://www.patreon.com/api/oauth2/v2/identity \
                //      --header 'authorization: Bearer <access_token>'
                final HttpUriRequest request = RequestBuilder.get()
                  .setUri("https://www.patreon.com/api/oauth2/v2/identity")
                  .setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + access_token)
                  .build();
                response = httpclient.execute(request);
                entity = response.getEntity();
                final String t = new BufferedReader(new InputStreamReader(entity.getContent())).lines().collect(Collectors.joining("\n"));
                final JSONObject user = new JSONObject(new JSONTokener(t));
                Logger.info("user = " + user.toString());
                // {"errors":[{"code":1,"code_name":"Unauthorized",
                // "detail":"The server could not verify that you are authorized to access the URL requested. You either supplied the wrong credentials (e.g. a bad password), or your browser doesn't understand how to supply the credentials required.",
                // "id":"47c651c1-7a40-58e7-83a7-2a1c9e2d6411","status":"401","title":"Unauthorized"}]}
                final JSONObject data = user.getJSONObject("data");
                final JSONObject attributes = data.getJSONObject("attributes");
                userEmail = user.optString("email", "");
                userName = user.optString("full_name", "");
                userPatreonLogin = data.optString("id", "");

                if ("null".equals(userEmail)) userEmail = "";
            }
        } catch (final IOException | JSONException e) {
            Logger.warn(e);
        }

        // Decide if the credentials are sufficient for authentication
        // We redirect again to a page where we tell the user that the log-in actually happened.
        final ServiceResponse serviceResponse = new ServiceResponse(json);
        if (userEmail.length() > 0 && userEmail.indexOf('@') > 1) try {

            Logger.info("User Login: " + userEmail);

            // get userid for user to authenticate the user
            // - search email address in authentication database
            Authentication authentication = Searchlab.userDB.getAuthentiationByEmail(userEmail);
            // - if not present, generate new entry
            if (authentication == null) {
                authentication = new Authentication();
                authentication.setEmail(userEmail);
            }
            authentication.setPatreonLogin(userPatreonLogin);
            authentication.setName(userName);

            // create an authorization cookie
            final String id = authentication.getID();
            final Authorization authorization = new Authorization(id);
            final String cookie = authorization.toString();
            serviceResponse.addSessionCookie(WebServer.COOKIE_USER_ID_NAME, cookie);

            // create an enry in two databases:
            // - authentication to store the user credentials
            Searchlab.userDB.setAuthentication(authentication);
            // - authorization with cookie entry to give user access and operation right when accessing further webpages
            Searchlab.userDB.setAuthorization(authorization);

            serviceResponse.setFoundRedirect("/" + authentication.getID() + "/aaaaa/login/");

            return serviceResponse;
        } catch (final IOException e) {
            Logger.error(e);
        }

        // user is rejected
        serviceResponse.setFoundRedirect("/" + serviceRequest.getUser() + "/aaaaa/dismiss/");
        return serviceResponse;
    }
}