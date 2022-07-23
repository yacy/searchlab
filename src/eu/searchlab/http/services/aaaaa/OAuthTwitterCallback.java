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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONObject;

import eu.searchlab.Searchlab;
import eu.searchlab.aaaaa.Authentication;
import eu.searchlab.aaaaa.Authorization;
import eu.searchlab.http.AbstractService;
import eu.searchlab.http.Service;
import eu.searchlab.http.ServiceRequest;
import eu.searchlab.http.ServiceResponse;
import eu.searchlab.http.WebServer;
import eu.searchlab.tools.Logger;
import eu.searchlab.tools.TwitterAPI;
import twitter4j.TwitterException;

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
        /*
        if (!callbackForward && !OAuthTwitterGetAuth.requestTokenIsFresh()) {
            Logger.info("catched callback for development, forwarding to localhost");
            final ServiceResponse serviceResponse = new ServiceResponse(new JSONObject(true));
            serviceResponse.setFoundRedirect("http://localhost:8400/en/aaaaa/twitter_callback/index.html?oauth_token=" + oauth_token + "&oauth_verifier=" + oauth_verifier);
            return serviceResponse;
        }
        */

        final String consumerKey = System.getProperty("twitter.client.id", "");
        final String consumerSecret = System.getProperty("twitter.client.secret", "");
        try {
            final HttpClient httpclient = HttpClients.createDefault();
            //final HttpPost httppost = new HttpPost("https://api.twitter.com/oauth2/token");
            final HttpPost httppost = new HttpPost("https://api.twitter.com/oauth/access_token");

            final List<NameValuePair> params = new ArrayList<>(3);
            params.add(new BasicNameValuePair("oauth_consumer_key", consumerKey));
            params.add(new BasicNameValuePair("oauth_token", oauth_token));
            params.add(new BasicNameValuePair("oauth_verifier", oauth_verifier));
            params.add(new BasicNameValuePair("grant_type", "client_credentials"));
            httppost.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));
            httppost.addHeader("Content-Type", "application/x-www-form-urlencoded");

            final HttpResponse response = httpclient.execute(httppost);
            final HttpEntity entity = response.getEntity();

            if (entity != null) {
                final String s = new BufferedReader(new InputStreamReader(entity.getContent())).lines().collect(Collectors.joining("\n"));
                final String[] t = s.split("&");
                if (t.length < 4) throw new IOException("bad content: " + s);
                // Get the Access Token:
                // these are user-specific credentials used to authenticate OAuth 1.0a API requests.
                // They specify the Twitter account the request is made on behalf of.
                final String access_token = t[0].substring(12);
                final String access_token_secret = t[1].substring(19);
                final String user_id = t[2].substring(8);
                final String screen_name = t[3].substring(12);
                // now we have permanent Access Token credentials

                // read the user information
                /*
                final JSONObject json = TwitterAPI.getUserByScreenName(
                        consumerKey, consumerSecret,
                        access_token, access_token_secret,
                        screen_name);
                 */
                final JSONObject credentialsj = TwitterAPI.verifyCredentials(
                        consumerKey, consumerSecret,
                        access_token, access_token_secret);
                //Logger.info("TWITTER CREDENTIALS: " + credentialsj.toString(2));
                /*
                final JSONObject resourcej = TwitterAPI.getUsersResourcesByID(
                        consumerKey, consumerSecret,
                        access_token, access_token_secret,
                        Long.parseLong(user_id));
                Logger.info("TWITTER RESOURCES: " + resourcej.toString(2));
                final JSONObject userj = TwitterAPI.getUserByID(
                        consumerKey, consumerSecret,
                        access_token, access_token_secret,
                        Long.parseLong(user_id));
                Logger.info("TWITTER USER: " + userj.toString(2));
                 */
                final String userName = credentialsj.optString("name");
                final String userEmail = credentialsj.optString("email");


                // Decide if the credentials are sufficient for authentication
                // We redirect again to a page where we tell the user that the log-in actually happened.
                final ServiceResponse serviceResponse = new ServiceResponse(new JSONObject(true));
                if (userEmail != null && userEmail.length() > 0 && userEmail.indexOf('@') > 1) {

                    Logger.info("User Login: " + userEmail);

                    // get userid for user to authenticate the user
                    // - search email address in authentication database
                    Authentication authentication = Searchlab.userDB.getAuthentiationByEmail(userEmail);
                    // - if not present, generate new entry
                    if (authentication == null) {
                        authentication = new Authentication();
                        authentication.setEmail(userEmail);
                    }
                    authentication.setTwitterLogin(screen_name);
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
                }
            }
        } catch (final IOException | TwitterException e) {
            Logger.warn(e);
        }

        // user is rejected
        final ServiceResponse serviceResponse = new ServiceResponse(new JSONObject(true));
        serviceResponse.setFoundRedirect("/" + serviceRequest.getUser() + "/logout/");
        return serviceResponse;
    }
}