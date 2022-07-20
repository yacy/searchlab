/**
 *  TwitterAPI
 *  Copyright 16.07.2015 by Michael Peter Christen, @orbiterlab
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


package eu.searchlab.tools;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.joda.time.DateTime;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

import twitter4j.IDs;
import twitter4j.RateLimitStatus;
import twitter4j.ResponseList;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.TwitterObjectFactory;
import twitter4j.User;
import twitter4j.conf.ConfigurationBuilder;

@SuppressWarnings("unused")
public class TwitterAPI {
    
    private final static String RATE_ACCOUNT_LOGIN_VERIFICATION_ENROLLMENT = "/account/login_verification_enrollment"; // limit = 15
    private final static String RATE_ACCOUNT_SETTINGS = "/account/settings"; // limit = 15
    private final static String RATE_ACCOUNT_UPDATE_PROFILE = "/account/update_profile"; // limit = 15
    private final static String RATE_ACCOUNT_VERIFY_CREDENTIALS = "/account/verify_credentials"; // limit = 15
    private final static String RATE_APPLICATION_RATE_LIMIT_STATUS = "/application/rate_limit_status"; // limit = 180
    private final static String RATE_BLOCKS_IDS = "/blocks/ids"; // limit = 15
    private final static String RATE_BLOCKS_LIST = "/blocks/list"; // limit = 15
    private final static String RATE_COLLECTIONS_ENTRIES = "/collections/entries"; // limit = 1000
    private final static String RATE_COLLECTIONS_LIST = "/collections/list"; // limit = 1000
    private final static String RATE_COLLECTIONS_SHOW = "/collections/show"; // limit = 1000
    private final static String RATE_CONTACTS_ADDRESSBOOK = "/contacts/addressbook"; // limit = 300
    private final static String RATE_CONTACTS_DELETE_STATUS = "/contacts/delete/status"; // limit = 300
    private final static String RATE_CONTACTS_UPLOADED_BY = "/contacts/uploaded_by"; // limit = 300
    private final static String RATE_CONTACTS_USERS = "/contacts/users"; // limit = 300
    private final static String RATE_CONTACTS_USERS_AND_UPLOADED_BY = "/contacts/users_and_uploaded_by"; // limit = 300
    private final static String RATE_DEVICE_TOKEN = "/device/token"; // limit = 15
    private final static String RATE_DIRECT_MESSAGES = "/direct_messages"; // limit = 15
    private final static String RATE_DIRECT_MESSAGES_SENT = "/direct_messages/sent"; // limit = 15
    private final static String RATE_DIRECT_MESSAGES_SENT_AND_RECEIVED = "/direct_messages/sent_and_received"; // limit = 15
    private final static String RATE_DIRECT_MESSAGES_SHOW = "/direct_messages/show"; // limit = 15
    private final static String RATE_FAVORITES_LIST = "/favorites/list"; // limit = 15
    private final static String RATE_FOLLOWERS_IDS = "/followers/ids"; // limit = 15
    private final static String RATE_FOLLOWERS_LIST = "/followers/list"; // limit = 15
    private final static String RATE_FRIENDS_FOLLOWING_IDS = "/friends/following/ids"; // limit = 15
    private final static String RATE_FRIENDS_FOLLOWING_LIST = "/friends/following/list"; // limit = 15
    private final static String RATE_FRIENDS_IDS = "/friends/ids"; // limit = 15
    private final static String RATE_FRIENDS_LIST = "/friends/list"; // limit = 15
    private final static String RATE_FRIENDSHIPS_INCOMING = "/friendships/incoming"; // limit = 15
    private final static String RATE_FRIENDSHIPS_LOOKUP = "/friendships/lookup"; // limit = 15
    private final static String RATE_FRIENDSHIPS_NO_RETWEETS_IDS = "/friendships/no_retweets/ids"; // limit = 15
    private final static String RATE_FRIENDSHIPS_OUTGOING = "/friendships/outgoing"; // limit = 15
    private final static String RATE_FRIENDSHIPS_SHOW = "/friendships/show"; // limit = 180
    private final static String RATE_GEO_ID_PLACE_ID = "/geo/id/:place_id"; // limit = 15
    private final static String RATE_GEO_REVERSE_GEOCODE = "/geo/reverse_geocode"; // limit = 15
    private final static String RATE_GEO_SEARCH = "/geo/search"; // limit = 15
    private final static String RATE_GEO_SIMILAR_PLACES = "/geo/similar_places"; // limit = 15
    private final static String RATE_HELP_CONFIGURATION = "/help/configuration"; // limit = 15
    private final static String RATE_HELP_LANGUAGES = "/help/languages"; // limit = 15
    private final static String RATE_HELP_PRIVACY = "/help/privacy"; // limit = 15
    private final static String RATE_HELP_SETTINGS = "/help/settings"; // limit = 15
    private final static String RATE_HELP_TOS = "/help/tos"; // limit = 15
    private final static String RATE_LISTS_LIST = "/lists/list"; // limit = 15
    private final static String RATE_LISTS_MEMBERS = "/lists/members"; // limit = 180
    private final static String RATE_LISTS_MEMBERS_SHOW = "/lists/members/show"; // limit = 15
    private final static String RATE_LISTS_MEMBERSHIPS = "/lists/memberships"; // limit = 15
    private final static String RATE_LISTS_OWNERSHIPS = "/lists/ownerships"; // limit = 15
    private final static String RATE_LISTS_SHOW = "/lists/show"; // limit = 15
    private final static String RATE_LISTS_STATUSES = "/lists/statuses"; // limit = 180
    private final static String RATE_LISTS_SUBSCRIBERS = "/lists/subscribers"; // limit = 180
    private final static String RATE_LISTS_SUBSCRIBERS_SHOW = "/lists/subscribers/show"; // limit = 15
    private final static String RATE_LISTS_SUBSCRIPTIONS = "/lists/subscriptions"; // limit = 15
    private final static String RATE_MUTES_USERS_IDS = "/mutes/users/ids"; // limit = 15
    private final static String RATE_MUTES_USERS_LIST = "/mutes/users/list"; // limit = 15
    private final static String RATE_SAVED_SEARCHES_DESTROY_ID = "/saved_searches/destroy/:id"; // limit = 15
    private final static String RATE_SAVED_SEARCHES_LIST = "/saved_searches/list"; // limit = 15
    private final static String RATE_SAVED_SEARCHES_SHOW_ID = "/saved_searches/show/:id"; // limit = 15
    private final static String RATE_SEARCH_TWEETS = "/search/tweets"; // limit = 180
    private final static String RATE_STATUSES_FRIENDS = "/statuses/friends"; // limit = 15
    private final static String RATE_STATUSES_HOME_TIMELINE = "/statuses/home_timeline"; // limit = 15
    private final static String RATE_STATUSES_LOOKUP = "/statuses/lookup"; // limit = 180
    private final static String RATE_STATUSES_MENTIONS_TIMELINE = "/statuses/mentions_timeline"; // limit = 15
    private final static String RATE_STATUSES_OEMBED = "/statuses/oembed"; // limit = 180
    private final static String RATE_STATUSES_RETWEETERS_IDS = "/statuses/retweeters/ids"; // limit = 15
    private final static String RATE_STATUSES_RETWEETS_ID = "/statuses/retweets/:id"; // limit = 60
    private final static String RATE_STATUSES_RETWEETS_OF_ME = "/statuses/retweets_of_me"; // limit = 15
    private final static String RATE_STATUSES_SHOW_ID = "/statuses/show/:id"; // limit = 180
    private final static String RATE_STATUSES_USER_TIMELINE = "/statuses/user_timeline"; // limit = 180
    private final static String RATE_TRENDS_AVAILABLE = "/trends/available"; // limit = 15
    private final static String RATE_TRENDS_CLOSEST = "/trends/closest"; // limit = 15
    private final static String RATE_TRENDS_PLACE = "/trends/place"; // limit = 15
    private final static String RATE_USERS_DERIVED_INFO = "/users/derived_info"; // limit = 15
    private final static String RATE_USERS_LOOKUP = "/users/lookup"; // limit = 180
    private final static String RATE_USERS_PROFILE_BANNER = "/users/profile_banner"; // limit = 180
    private final static String RATE_USERS_REPORT_SPAM = "/users/report_spam"; // limit = 15
    private final static String RATE_USERS_SEARCH = "/users/search"; // limit = 180
    private final static String RATE_USERS_SHOW_ID = "/users/show/:id"; // limit = 180
    private final static String RATE_USERS_SUGGESTIONS = "/users/suggestions"; // limit = 15
    private final static String RATE_USERS_SUGGESTIONS_SLUG = "/users/suggestions/:slug"; // limit = 15
    private final static String RATE_USERS_SUGGESTIONS_SLUG_MEMBERS = "/users/suggestions/:slug/members"; // limit = 15

    private static TwitterFactory appFactory = null;
    private static Map<String, TwitterFactory> userFactory = new HashMap<>();
    
    public static TwitterFactory getAppTwitterFactory(
            String twitterConsumerKey, String twitterConsumerSecret,
            String twitterAccessToken, String twitterAccessTokenSecret) {
        if (twitterAccessToken.length() == 0 || twitterAccessTokenSecret.length() == 0) return null;
        if (appFactory == null) appFactory = getUserTwitterFactory(
                twitterConsumerKey, twitterConsumerSecret,
                twitterAccessToken, twitterAccessTokenSecret);
        return appFactory;
    }

    public static TwitterFactory getUserTwitterFactory(
            String twitterConsumerKey, String twitterConsumerSecret,
            String oauthToken, String oauthTokenSecret,
            String screen_name) {
        TwitterFactory uf = userFactory.get(screen_name);
        if (uf != null) return uf;
        uf = getUserTwitterFactory(
                twitterConsumerKey, twitterConsumerSecret,
                oauthToken, oauthTokenSecret);
        if (uf != null) userFactory.put(screen_name, uf);
        return uf;
    }
    
    private static TwitterFactory getUserTwitterFactory(
            String twitterConsumerKey, String twitterConsumerSecret,
            String twitterAccessToken, String twitterAccessTokenSecret) {
        ConfigurationBuilder cb = new ConfigurationBuilder();
        cb
            .setOAuthConsumerKey(twitterConsumerKey)
            .setOAuthConsumerSecret(twitterConsumerSecret)
            .setOAuthAccessToken(twitterAccessToken)
            .setOAuthAccessTokenSecret(twitterAccessTokenSecret);
        cb.setJSONStoreEnabled(true);
        return new TwitterFactory(cb.build());
    }

    public static RateLimitStatus getRateLimitStatus(
            String twitterConsumerKey, String twitterConsumerSecret,
            String twitterAccessToken, String twitterAccessTokenSecret,
            final String rate_type) throws TwitterException {
        return getAppTwitterFactory(
            twitterConsumerKey, twitterConsumerSecret,
            twitterAccessToken, twitterAccessTokenSecret
        ).getInstance().getRateLimitStatus().get(rate_type);
    }

    public static JSONObject getUser(
            String twitterConsumerKey, String twitterConsumerSecret,
            String twitterAccessToken, String twitterAccessTokenSecret,
            String screen_name) throws TwitterException, IOException {

        TwitterFactory tf = getUserTwitterFactory(
            twitterConsumerKey, twitterConsumerSecret,
            twitterAccessToken, twitterAccessTokenSecret,
            screen_name);
        if (tf == null) tf = getAppTwitterFactory(
                twitterConsumerKey, twitterConsumerSecret,
                twitterAccessToken, twitterAccessTokenSecret);
        if (tf == null) return new JSONObject();
        Twitter twitter = tf.getInstance();
        User user = twitter.showUser(screen_name);
        RateLimitStatus rateLimitStatus = user.getRateLimitStatus();
        String jsonstring = TwitterObjectFactory.getRawJSON(user);
        try {
            JSONObject json = new JSONObject(jsonstring);
            return json;
        } catch (JSONException e) {
            throw new IOException(e.getMessage());
        }
    }
    
}
