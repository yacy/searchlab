/**
 *  Billing
 *  Copyright 12.09.2022 by Michael Peter Christen, @orbiterlab
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


package eu.searchlab.aaaaa;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
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

import eu.searchlab.http.services.aaaaa.OAuthPatreonGetAuth;
import eu.searchlab.tools.Logger;

public class Billing {

    public static Set<String> getGithubSponsorNicknames(String forAccount) {
        final Set<String> names = new HashSet<>();
        //final HttpClient httpclient = HttpClients.createDefault();
        final HttpClient httpclient = HttpClients.custom().setDefaultRequestConfig(RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD).build()).build();
        collector: for (int i = 1; i < 1000; i++) {
            try {
                final HttpUriRequest request = RequestBuilder.get()
                        .setUri("https://github.com/sponsors/" + forAccount + "/sponsors_partial?page=" + i)
                        //.setHeader(HttpHeaders.ACCEPT, "application/vnd.github.v3+json")
                        .build();
                final HttpResponse response = httpclient.execute(request);
                final int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode != 200) break collector;
                final HttpEntity entity = response.getEntity();
                String t = new BufferedReader(new InputStreamReader(entity.getContent())).lines().collect(Collectors.joining("\n"));
                t = t.trim();
                if (t.length() == 0) break collector;
                for (String s: t.split("\n")) {
                    int p = s.indexOf("alt=\"@");
                    if (p >= 0) {
                        s = s.substring(p + 6);
                        p = s.indexOf('"');
                        if (p > 2) {
                            names.add(s.substring(0, p).trim().toLowerCase());
                        }
                    }
                }
            } catch (final IOException e) {
                break collector;
            }
        }
        return names;
    }

    public static boolean isAGithubSponsor(String forAccount, String fromNick) {
        final Set<String> set = getGithubSponsorNicknames(forAccount);
        return set.contains(fromNick);
    }

    public static PatreonCampaign getPatreonCampaign() throws IOException {
    	// /api/oauth2/v2/campaigns

    	final String access_token = System.getProperty("patreon.access.token", "");
    	final HttpClient httpclient = HttpClients.custom().setDefaultRequestConfig(RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD).build()).build();
        
    	final HttpUriRequest request = RequestBuilder.get()
                //.setUri("https://www.patreon.com/api/oauth2/api/current_user")
                .setUri("https://www.patreon.com/api/oauth2/api/current_user/campaigns")
                .setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + access_token)
                .build();
    	HttpResponse response = httpclient.execute(request);
    	HttpEntity entity = response.getEntity();
        final String t = new BufferedReader(new InputStreamReader(entity.getContent())).lines().collect(Collectors.joining("\n"));
        PatreonCampaign pc = new PatreonCampaign(t);
        return pc;
    }
    
    public static class PatreonCampaign {
    	Map<String, PatreonReward> rewards;
    	String campaignId;
    	JSONObject json;
    	public PatreonCampaign(String json) {
    		try {
            	this.json = new JSONObject(new JSONTokener(json));
            	Logger.info("json = " + this.json.toString(2));
            	JSONArray data = this.json.optJSONArray("data");
            	JSONObject data0 = data.getJSONObject(0);
            	JSONObject relationships = data0.optJSONObject("relationships");
            	//JSONObject creator = relationships.optJSONObject("creator");
            	//JSONObject creatordata = creator.optJSONObject("data");
            	//String creatorId = creatordata.optString("id");
            	JSONObject rewards = relationships.optJSONObject("rewards");
            	JSONArray rewardsdata = rewards.optJSONArray("data");
            	Set<String> rewardsId = new HashSet<>();
            	for (int i = 0; i < rewardsdata.length(); i++) {
            		JSONObject dataobj = rewardsdata.optJSONObject(i);
            		rewardsId.add(dataobj.optString("id"));
            	}
            	JSONArray included = this.json.optJSONArray("included");
            	this.rewards = new HashMap<>();
            	for (int i = 0; i < included.length(); i++) {
            		JSONObject j = included.getJSONObject(i);
            		String type = j.optString("type");
            		if (type.equals("reward")) {
            			PatreonReward reward = new PatreonReward(j);
            			this.rewards.put(reward.id, reward);
            		}
            	}
            } catch (JSONException e) {
    			Logger.warn(e);
    		}
    	}
    }
    
    public static class PatreonReward {
    	int cents;
    	int declinedCount;
    	int patronCount;
    	String id;
    	String title;
    	String description;
    	JSONObject json;
    	public PatreonReward(JSONObject json) {
    		this.json = json;
    		this.id = this.json.optString("id");
    		JSONObject attributes = this.json.optJSONObject("attributes");
    		this.cents = attributes.optInt("amount_cents");
    		this.declinedCount = attributes.optInt("declined_patron_count");
    		this.patronCount = attributes.optInt("patron_count");
    		this.title = attributes.optString("title");
    		this.description = attributes.optString("description");
    	}
    }

    public static void main(String[] args) {
    	try {
			getPatreonCampaign();
		} catch (IOException e) {
			Logger.warn(e);
		}
    	/*
        Logger.info("reading github sponsors"); // real reason to print this out: initialize the logger
        final Set<String> nicknames = getGithubSponsorNicknames("orbiter");
        System.out.println(nicknames);
        */
        System.exit(0);
    }
}
