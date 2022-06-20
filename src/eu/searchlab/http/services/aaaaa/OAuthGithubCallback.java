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
import java.io.InputStream;
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

import eu.searchlab.http.AbstractService;
import eu.searchlab.http.Service;
import eu.searchlab.http.ServiceRequest;
import eu.searchlab.http.ServiceResponse;
import eu.searchlab.tools.Logger;

/**
 * OAuthGithubCallback
 * This class is called after a user has (tried to) authenticate with github,
 * and github redirects to this callback address.
 * From here we should know the access credentials to call the github api
 * to get the user authentication details.
 *
 * example: call
 * http://localhost:8400/en/aaaaa/github/callback
 */
public class OAuthGithubCallback  extends AbstractService implements Service {

    @Override
    public String[] getPaths() {
        return new String[] {"/aaaaa/github/callback"};
    }

    @Override
    public ServiceResponse serve(final ServiceRequest serviceRequest) {
        // follow the process described in
        // https://docs.github.com/en/developers/apps/building-oauth-apps/authorizing-oauth-apps
        // we now have a code that we can use to access the github API
        // POST https://github.com/login/oauth/access_token

        final String client_id = System.getProperty("github.client.id", "");
        final String client_secret = System.getProperty("github.client.secret", "");
        final String code = serviceRequest.get("code", "");
        //final String state = serviceRequest.get("state", "");

        try {
            final HttpClient httpclient = HttpClients.createDefault();
            final HttpPost httppost = new HttpPost("https://github.com/login/oauth/access_token");

            final List<NameValuePair> params = new ArrayList<>(3);
            params.add(new BasicNameValuePair("client_id", client_id));
            params.add(new BasicNameValuePair("client_secret", client_secret));
            params.add(new BasicNameValuePair("code", code));
            httppost.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));

            final HttpResponse response = httpclient.execute(httppost);
            final HttpEntity entity = response.getEntity();

            if (entity != null) {
                // the response has the following form:
                // access_token=gho_16C7e42F292c6912E7710c838347Ae178B4a&scope=repo%2Cgist&token_type=bearer
                final InputStream inputStream = entity.getContent();
                String s = new BufferedReader(new InputStreamReader(inputStream)).lines().collect(Collectors.joining("\n"));
                int p = s.indexOf("access_token=");
                if (p >= 0) s = s.substring(p + 13);
                p = s.indexOf("&");
                if (p >= 0) s = s.substring(0, p);
                // the access_token is now s

                Logger.info("Access Token is " + s);
            }


        } catch (final IOException e) {
            Logger.warn(e);
        }

        // after evaluation of this code, we redirect again to a page where we tell the user that the log-in actually happened.
        final JSONObject json = new JSONObject(true);
        final ServiceResponse serviceResponse = new ServiceResponse(json);
        serviceResponse.setFoundRedirect("https://searchlab.eu/" + serviceRequest.getUser() + "/aaaaa/login");
        return serviceResponse;
    }
}