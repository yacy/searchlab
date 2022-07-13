/**
 *  OAuthTwitterGetAuth
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

import org.json.JSONObject;

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
 * OAuthTwitterGetAuth
 * This class is called when a user wants to authorize with twitter.
 *
 * To configure this class, we need startup parameters in environment variables:
 * -Dtwitter.client.id=...
 * -Dtwitter.client.secret=...
 *
 * Twitter OAuth documentation
 * https://developer.twitter.com/en/docs/authentication/guides/log-in-with-twitter
 *
 * example: call
 * http://localhost:8400/en/aaaaa/twitter_get_auth
 */
public class OAuthTwitterGetAuth  extends AbstractService implements Service {

    public final static String DEVELOPMENT_FORWARD_STATE = "development.forward";

    public static final String redirect_uri = "https://searchlab.eu/en/aaaaa/twitter_callback/index.html";
    public static final String redirect_uri_urlencoded = redirect_uri.replaceAll(":", "%3A").replaceAll("/", "%2F");

    public static RequestToken latestRequestToken = null;

    @Override
    public String[] getPaths() {
        return new String[] {"/aaaaa/twitter_get_auth"};
    }

    @Override
    public ServiceResponse serve(final ServiceRequest serviceRequest) {
        final String consumerKey = System.getProperty("twitter.client.id", "");
        final String consumerSecret = System.getProperty("twitter.client.secret", "");

        final JSONObject json = new JSONObject(true);
        final ServiceResponse serviceResponse = new ServiceResponse(json);
        try {
            final RequestToken requestToken = new RequestToken(consumerKey, consumerSecret);

            // forward to twitter for authentication
            // see https://developer.twitter.com/en/docs/authentication/api-reference/authenticate
            // see https://developer.twitter.com/en/docs/authentication/api-reference/authorize
            //final String url = "https://api.twitter.com/oauth/authenticate?oauth_token=" + requestToken.oauth_token;
            final String url = "https://api.twitter.com/oauth/authorize?oauth_token=" + requestToken.oauth_token;
            serviceResponse.setFoundRedirect(url);
        } catch (final IOException e) {
            Logger.warn(e);

            // user is rejected
            serviceResponse.setFoundRedirect("/" + serviceRequest.getUser() + "/logout/");
        }
        return serviceResponse;
    }

    public static class RequestToken {

        // Temporaray Credentials:
        public final String oauth_token;        // == Request Token
        public final String oauth_token_secret; // == Request Token Secret
        public final long time;

        /**
         * Call request_token API according to
         * https://developer.twitter.com/en/docs/authentication/oauth-1-0a/obtaining-user-access-tokens
         * @param consumerKey == App Key === API Key === Consumer API Key === Consumer Key === Customer Key === oauth_consumer_key
         * @param consumerSecret == App Key Secret === API Secret Key === Consumer Secret === Consumer Key === Customer Key === oauth_consumer_secret
         * @throws IOException
         */
        public RequestToken(final String consumerKey, final String consumerSecret) throws IOException {
            this.time = System.currentTimeMillis();
            final URL url = new URL("https://api.twitter.com/oauth/request_token");
            final HttpURLConnection request = (HttpURLConnection) url.openConnection();
            try {
                final OAuthConsumer consumer = new DefaultOAuthConsumer(consumerKey, consumerSecret);

                // sign the request (consumer is a Signpost DefaultOAuthConsumer)
                consumer.sign(request);

                // send the request
                request.connect();
                //System.out.println(request.getResponseCode());
                //System.out.println(request.getResponseMessage());
                BufferedReader br = null;
                if (request.getResponseCode() == 200) {
                    br = new BufferedReader(new InputStreamReader(request.getInputStream()));
                    final String s = br.readLine();
                    final String[] t = s.split("&");
                    this.oauth_token = t[0].substring(12);
                    this.oauth_token_secret = t[1].substring(19);
                } else {
                    throw new IOException("Response Code: " + request.getResponseCode());
                }
            } catch (OAuthMessageSignerException | OAuthExpectationFailedException | OAuthCommunicationException e) {
                Logger.warn(e);
                Logger.warn("Response Code: " + request.getResponseCode());
                Logger.warn("Response Message: " + request.getResponseMessage());
                throw new IOException(e.getMessage());
            }
        }
    }

    public static boolean requestTokenIsFresh() {
        if (latestRequestToken == null) return false;
        if (System.currentTimeMillis() - latestRequestToken.time > 60000) return false;
        return true;
    }

    public static void main(final String[] args) {
        try {
            final RequestToken requestToken = new RequestToken("consumerKey", "consumerSecret");
            System.out.println("oauth_token = " + requestToken.oauth_token);
            System.out.println("oauth_token_secret = " + requestToken.oauth_token_secret);

        } catch (final IOException e) {
            e.printStackTrace();
        }
    }

}