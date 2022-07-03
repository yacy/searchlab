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
import org.json.JSONArray;
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
 * OAuthGithubCallback
 * This class is called after a user has (tried to) authenticate with github,
 * and github redirects to this callback address.
 * From here we should know the access credentials to call the github api
 * to get the user authentication details.
 *
 * example: call
 * http://localhost:8400/en/aaaaa/github_callback
 */
public class OAuthGithubCallback  extends AbstractService implements Service {

    @Override
    public String[] getPaths() {
        return new String[] {"/aaaaa/github_callback"};
    }

    @Override
    public ServiceResponse serve(final ServiceRequest serviceRequest) {
        final String code = serviceRequest.get("code", "");
        final String state = serviceRequest.get("state", "");
        final JSONObject json = new JSONObject(true);

        // In case that we set callback.forward = true, we are in a development environment.
        // Then we produce an additional forwarding to localhost. Github will always call the
        // production instance, but that instance supports development by forwarding.
        // To switch on that case, we hand over the state "development.forward"
        final boolean callbackForward = "true".equals(System.getProperty("callback.forward", "false"));
        Logger.info("Catched callback from github; state = " + state + "; callbackForward = " + callbackForward);
        // to prevent that in development environments the call is executed forever, we must check the forward flag here as well
        if (!callbackForward && OAuthGithubGetAuth.DEVELOPMENT_FORWARD_STATE.equals(state)) {
            Logger.info("catched callback for development, forwarding to localhost");
            final ServiceResponse serviceResponse = new ServiceResponse(json);
            serviceResponse.setFoundRedirect("http://localhost:8400/en/aaaaa/github_callback?code=" + code + "&state=" + state);
            return serviceResponse;
        }


        // follow the process described in
        // https://docs.github.com/en/developers/apps/building-oauth-apps/authorizing-oauth-apps
        // we now have a code that we can use to access the github API
        // POST https://github.com/login/oauth/access_token

        final String client_id = System.getProperty("github.client.id", "");
        final String client_secret = System.getProperty("github.client.secret", "");
        String userGithubLogin = "";
        String userName = "";
        String userEmail = "";
        String userLocationName = "";
        String userTwitterUsername = "";

        try {
            final HttpClient httpclient = HttpClients.createDefault();
            final HttpPost httppost = new HttpPost("https://github.com/login/oauth/access_token");

            final List<NameValuePair> params = new ArrayList<>(3);
            params.add(new BasicNameValuePair("client_id", client_id));
            params.add(new BasicNameValuePair("client_secret", client_secret));
            params.add(new BasicNameValuePair("code", code));
            httppost.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));
            HttpResponse response = httpclient.execute(httppost);
            HttpEntity entity = response.getEntity();

            if (entity != null) {
                // the response has the following form:
                // access_token=gho_16C7e42F292c6912E7710c838347Ae178B4a&scope=repo%2Cgist&token_type=bearer
                String s = new BufferedReader(new InputStreamReader(entity.getContent())).lines().collect(Collectors.joining("\n"));
                int p = s.indexOf("access_token=");
                if (p >= 0) s = s.substring(p + 13);
                p = s.indexOf("&");
                if (p >= 0) s = s.substring(0, p);
                // the access_token is now s

                Logger.info("Access Token is " + s);

                // read the user information
                // see https://docs.github.com/en/rest/users/users#get-the-authenticated-user
                // i.e.
                // curl -H "Authorization: token TOKEN" https://api.github.com/user
                HttpUriRequest request = RequestBuilder.get()
                  .setUri("https://api.github.com/user")
                  .setHeader(HttpHeaders.ACCEPT, "application/vnd.github.v3+json")
                  .setHeader(HttpHeaders.AUTHORIZATION, "token " + s)
                  .build();
                response = httpclient.execute(request);
                entity = response.getEntity();
                String t = new BufferedReader(new InputStreamReader(entity.getContent())).lines().collect(Collectors.joining("\n"));
                final JSONObject user = new JSONObject(new JSONTokener(t));
                userGithubLogin = user.optString("login", "");
                userName = user.optString("name", "");
                userEmail = user.optString("email", "");
                userLocationName = user.optString("location", "");
                userTwitterUsername = user.optString("twitter_username", "");
                if ("null".equals(userEmail)) userEmail = "";

                // in case that the email is not part of a public user profile, we call the user/emails api
                // which we should be allowed to use since we required that user right
                if (userEmail.length() == 0) {
                    // call the user/emails API to get the users email addresses. The email address is the single point of identification for searchlab
                    // see https://docs.github.com/en/rest/users/emails#list-email-addresses-for-the-authenticated-user
                    request = RequestBuilder.get()
                            .setUri("https://api.github.com/user/emails")
                            .setHeader(HttpHeaders.ACCEPT, "application/vnd.github.v3+json")
                            .setHeader(HttpHeaders.AUTHORIZATION, "token " + s)
                            .build();
                    response = httpclient.execute(request);
                    entity = response.getEntity();
                    t = new BufferedReader(new InputStreamReader(entity.getContent())).lines().collect(Collectors.joining("\n"));
                    final JSONArray emails = new JSONArray(new JSONTokener(t));
                    for (int i = 0; i < emails.length(); i++) {
                        final JSONObject j = emails.getJSONObject(i);
                        if (j.optBoolean("verified") && j.optBoolean("primary")) {
                            userEmail = j.optString("email");
                        }
                    }
                }
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
            authentication.setGithubLogin(userGithubLogin);
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

            // successfully logged in
            serviceResponse.setFoundRedirect("/" + authentication.getID() + "/home/");

            return serviceResponse;
        } catch (final IOException e) {
            Logger.error(e);
        }

        // user is rejected
        serviceResponse.setFoundRedirect("/" + serviceRequest.getUser() + "/logout/");
        return serviceResponse;
    }
}