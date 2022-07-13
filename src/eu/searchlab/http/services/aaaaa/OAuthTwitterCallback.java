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
import java.net.HttpURLConnection;
import java.net.URL;
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
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import eu.searchlab.http.AbstractService;
import eu.searchlab.http.Service;
import eu.searchlab.http.ServiceRequest;
import eu.searchlab.http.ServiceResponse;
import eu.searchlab.tools.Logger;
import oauth.signpost.OAuthConsumer;
import oauth.signpost.basic.DefaultOAuthConsumer;
import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;

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
                //final URL url = new URL("https://api.twitter.com/1.1/account/verify_credentials.json");
                final URL url = new URL("https://api.twitter.com/1.1/users/show.json?user_id=" + user_id);
                final HttpURLConnection request = (HttpURLConnection) url.openConnection();
                final OAuthConsumer consumer = new DefaultOAuthConsumer(consumerKey, consumerSecret);
                consumer.setTokenWithSecret(access_token, access_token_secret);
                consumer.sign(request);
                request.connect();

                if (request.getResponseCode() == 200) {
                    final String u = new BufferedReader(new InputStreamReader(request.getInputStream())).lines().collect(Collectors.joining("\n"));
                    final JSONObject json = new JSONObject(new JSONTokener(u));

                } else {
                    Logger.warn("Response Code: " + request.getResponseCode());
                    Logger.warn("Response Message: " + request.getResponseMessage());
                    throw new IOException("Response Code: " + request.getResponseCode());
                }
            }
        } catch (final IOException | JSONException | OAuthMessageSignerException | OAuthExpectationFailedException | OAuthCommunicationException e) {
            Logger.warn(e);
        }

        // user is rejected
        final ServiceResponse serviceResponse = new ServiceResponse(new JSONObject(true));
        serviceResponse.setFoundRedirect("/" + serviceRequest.getUser() + "/logout/");
        return serviceResponse;
    }
}