/**
 *  OAuthTwitterCallback
 *  Copyright 07.07.2022 by Michael Peter Christen, @orbiterlab
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
import eu.searchlab.tools.Logger;

/**
 * OAuthTwitterCallback
 * This class is called after a user has (tried to) authenticate with twitter,
 * and twitter redirects to this callback address.
 * From here we should know the access credentials to call the twitter api
 * to get the user authentication details.
 *
 * example: call
 * http://localhost:8400/en/aaaaa/twitter_callback
 */
public class OAuthTwitterCallback  extends AbstractService implements Service {

    @Override
    public String[] getPaths() {
        return new String[] {"/aaaaa/twitter_callback", "/aaaaa/twitter_callback/index.html"};
    }

    @Override
    public ServiceResponse serve(final ServiceRequest serviceRequest) {
        Logger.info("serviceRequest: " + serviceRequest.getPost().toString());

        final String oauth_token = serviceRequest.get("oauth_token", "");
        final String oauth_verifier = serviceRequest.get("oauth_verifier", "");

        // In case that we set callback.forward = true, we are in a development environment.
        // Then we produce an additional forwarding to localhost. Twitter will always call the
        // production instance, but that instance supports development by forwarding.
        // To switch on that case, we hand over the state "development.forward"
        final boolean callbackForward = "true".equals(System.getProperty("callback.forward", "false"));
        Logger.info("Catched callback from twitter; callbackForward = " + callbackForward + "; requestTokenIsFresh = " + OAuthTwitterGetAuth.requestTokenIsFresh());
        // to prevent that in development environments the call is executed forever, we must check the forward flag here as well
        if (!callbackForward && !OAuthTwitterGetAuth.requestTokenIsFresh()) {
            Logger.info("catched callback for development, forwarding to localhost");
            final ServiceResponse serviceResponse = new ServiceResponse(new JSONObject(true));
            serviceResponse.setFoundRedirect("http://localhost:8400/en/aaaaa/twitter_callback/index.html?oauth_token=" + oauth_token + "&oauth_verifier=" + oauth_verifier);
            return serviceResponse;
        }


        // follow the process described in
        // https://developer.twitter.com/en/docs/authentication/api-reference/access_token

        /*
        final String client_id = System.getProperty("twitter.client.id", "");
        final String client_secret = System.getProperty("twitter.client.secret", "");
        String userTwitterLogin = "";
        String userName = "";
        String userEmail = "";
        final String userLocationName = "";
        final String userTwitterUsername = "";

        try {
            final HttpClient httpclient = HttpClients.createDefault();
            final HttpPost httppost = new HttpPost("https://www.twitter.com/api/oauth2/token");

            final List<NameValuePair> params = new ArrayList<>(3);
            params.add(new BasicNameValuePair("code", code));
            params.add(new BasicNameValuePair("grant_type", "authorization_code"));
            params.add(new BasicNameValuePair("client_id", client_id));
            params.add(new BasicNameValuePair("client_secret", client_secret));
            params.add(new BasicNameValuePair("redirect_uri", OAuthTwitterGetAuth.redirect_uri));

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
                //      --url https://www.twitter.com/api/oauth2/v2/identity \
                //      --header 'authorization: Bearer <access_token>'
                final HttpUriRequest request = RequestBuilder.get()
                  .setUri("https://www.twitter.com/api/oauth2/api/current_user")
                  .setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + access_token)
                  .build();
                response = httpclient.execute(request);
                entity = response.getEntity();
                final String t = new BufferedReader(new InputStreamReader(entity.getContent())).lines().collect(Collectors.joining("\n"));
                final JSONObject json = new JSONObject(new JSONTokener(t));
                Logger.info("json = " + json.toString());
                final JSONObject data = json.getJSONObject("data");
                final JSONObject attributes = data.getJSONObject("attributes");
                userEmail = attributes.optString("email", "");
                userName = attributes.optString("full_name", "");
                userTwitterLogin = data.optString("vanity", "");

                if ("null".equals(userEmail)) userEmail = "";
            }
        } catch (final IOException | JSONException e) {
            Logger.warn(e);
        }

        // Decide if the credentials are sufficient for authentication
        // We redirect again to a page where we tell the user that the log-in actually happened.
        final ServiceResponse serviceResponse = new ServiceResponse(new JSONObject(true));
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
            authentication.setTwitterLogin(userTwitterLogin);
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

        */
        // user is rejected
        final ServiceResponse serviceResponse = new ServiceResponse(new JSONObject(true));
        serviceResponse.setFoundRedirect("/" + serviceRequest.getUser() + "/logout/");
        return serviceResponse;
    }
}