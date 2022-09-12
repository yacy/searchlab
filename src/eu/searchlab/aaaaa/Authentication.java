/**
 *  Authentication
 *  Copyright 18.04.2022 by Michael Peter Christen, @orbiterlab
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

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.json.JSONException;
import org.json.JSONObject;

import eu.searchlab.Searchlab;
import eu.searchlab.tools.DateParser;
import eu.searchlab.tools.Logger;

public class Authentication {

    private final static Random random = new Random(System.currentTimeMillis());
    private final static long dayms = 1000L * 60L * 60L * 24L;

    public final static String ANONYMOUS_ID = "112358132";
    public final static String[] GOLDEN_ID = new String[]{"123456789", "123456798",  "123456987",  "123459876",  "123498765",  "123987654",  "129876543", "198765432", "987654321"}; // includes at this time only special ids

    private final JSONObject json;
    private final boolean isAnonymous;

    /**
     *  Store the fields:
     *  "id"
     *  "email"
     *  "name"
     *  "sponsor_github"
     *  "sponsor_github_approved"
     *  "sponsor_patreon"
     *  "sponsor_patreon_approved"
     *  "anonymous_production"
     *  "login_github"
     *  "date_registration"
     *  "date_visit"
     * @param json
     */
    public Authentication(final JSONObject json) {
        this.json = json;
        this.isAnonymous = false;
        this.init();
    }

    /**
     * initialize a new authentication object with self-assigned id.
     * The id is generated in such a way that it is unique and not existent before in the userDB.
     */
    public Authentication() {
        this.json = new JSONObject();
        while (true) {
            // get a new ID
            final String id = generateRandomID();
            try {
                if (Searchlab.userDB.getAuthentiationByID(id) == null) {
                    setID(id);
                    break;
                }
            } catch (final RuntimeException e) {
                Logger.error(e);
            }
        }
        this.isAnonymous = false;
        this.init();
    }

    /**
     * create an authentication object with anonymous id
     * @param id
     * @throws RuntimeException
     */
    public Authentication(final String id) throws RuntimeException {
        this.json = new JSONObject();
        setID(id);
        this.isAnonymous = true;
        this.init();
    }

    @SuppressWarnings("deprecation")
    private void init() {
        try {
            final String sponsor_github = this.json.optString("sponsor_github");
            if (sponsor_github == null || sponsor_github.length() == 0) {
                this.json.put("sponsor_github", "");
            }
            final String sponsor_patreon = this.json.optString("sponsor_patreon");
            if (sponsor_patreon == null || sponsor_patreon.length() == 0) {
                this.json.put("sponsor_patreon", "");
            }
            if (!this.json.has("self")) this.json.put("self", true);
            if (!this.json.has("anonymous_production")) this.json.put("anonymous_production", false);

            final Date randomPast = new Date(System.currentTimeMillis() - ((Math.abs(random.nextLong())) % (90 * dayms)));
            final String date_registration = this.json.optString("date_registration");
            if (date_registration == null || date_registration.length() == 0) {
                this.json.put("date_registration", DateParser.minuteDateFormat.format(randomPast));
            }
            final String date_visit = this.json.optString("date_visit");
            if (date_visit == null || date_visit.length() == 0) {
                this.json.put("date_visit", DateParser.minuteDateFormat.format(randomPast));
            }
        } catch (final JSONException e) {
            Logger.error(e);
        }
    }

    public Authentication setVisitDate(Date date) throws RuntimeException {
        try {this.json.put("date_visit", DateParser.minuteDateFormat.format(date));} catch (final JSONException e) {
            throw new RuntimeException(e.getMessage());
        }
        return this;
    }

    public Date getRegistrationDate() {
        if (this.isAnonymous) throw new RuntimeException("anonmous accounts do not have registration dates");
        try {return DateParser.minuteDateFormat.parse(this.json.getString("date_registration"));} catch (final JSONException | ParseException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    public Date getVisitDate() {
        if (this.isAnonymous) throw new RuntimeException("anonmous accounts do not have registration dates");
        try {return DateParser.minuteDateFormat.parse(this.json.getString("date_visit"));} catch (final JSONException | ParseException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    /**
     * set property email from https://schema.org/Person
     * @param email - the email address; must contain a '@'
     * @return this
     * @throws RuntimeException
     */
    public Authentication setEmail(final String email) throws RuntimeException {
        if (this.isAnonymous) throw new RuntimeException("anonmous accounts cannot set email addresses");
        if (email.indexOf('@') < 0) throw new RuntimeException("email address invalid: " + email);
        try {this.json.put("email", email);} catch (final JSONException e) {
            throw new RuntimeException(e.getMessage());
        }
        return this;
    }

    /**
     * get property email from https://schema.org/Person
     * @return the email address
     * @throws RuntimeException
     */
    public String getEmail() throws RuntimeException {
        if (this.isAnonymous) throw new RuntimeException("anonmous accounts cannot read email addresses");
        try {return this.json.getString("email");} catch (final JSONException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    public String getEmailPseudonymized() throws RuntimeException {
        if (this.isAnonymous) throw new RuntimeException("anonmous accounts cannot read email addresses");
        final String email = getEmail();
        final int p = email.indexOf('@');
        if (p < 0) throw new RuntimeException("email not valid");
        return email.charAt(0) + "***" + email.substring(p - 1);
    }

    /**
     * set searchlab id, 9 digit number
     * @param id
     * @return
     * @throws RuntimeException
     */
    private Authentication setID(final String id) throws RuntimeException {
        if (!isValid(id)) throw new RuntimeException("identifier invalid: " + id);
        try {this.json.put("id", id);} catch (final JSONException e) {
            throw new RuntimeException(e.getMessage());
        }
        return this;
    }

    public String getID() throws RuntimeException {
        try {return this.json.getString("id");} catch (final JSONException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    public Authentication setGithubLogin(final String github_login) throws RuntimeException {
        if (this.isAnonymous) throw new RuntimeException("anonmous accounts cannot set authentication providers");
        try {
            this.json.put("login_github", github_login);
            final String sponsor_github = this.json.optString("sponsor_github");
            if (sponsor_github == null || sponsor_github.length() == 0) {
                // pre-set this as the same account, however that might change
                this.json.put("sponsor_github", github_login);
            }
        } catch (final JSONException e) {
            throw new RuntimeException(e.getMessage());
        }
        return this;
    }

    public String getGithubLogin() throws RuntimeException {
        if (this.isAnonymous) throw new RuntimeException("anonmous accounts cannot set authentication providers");
        return this.json.optString("login_github", "");
    }

    public Authentication setGithubSponsor(final String github_sponsor) throws RuntimeException {
        if (this.isAnonymous) throw new RuntimeException("anonmous accounts cannot set authentication providers");
        try {
            this.json.put("sponsor_github", github_sponsor);
        } catch (final JSONException e) {
            throw new RuntimeException(e.getMessage());
        }
        return this;
    }

    public String getGithubSponsor() throws RuntimeException {
        if (this.isAnonymous) throw new RuntimeException("anonmous accounts cannot set authentication providers");
        return this.json.optString("sponsor_github", "");
    }

    public Authentication setGithubSponsorApproved(final boolean approved) throws RuntimeException {
        if (this.isAnonymous) throw new RuntimeException("anonmous accounts cannot set authentication providers");
        try {
            this.json.put("sponsor_github_approved", approved);
        } catch (final JSONException e) {
            throw new RuntimeException(e.getMessage());
        }
        return this;
    }

    public boolean getGithubSponsorApproved() throws RuntimeException {
        if (this.isAnonymous) throw new RuntimeException("anonmous accounts cannot set authentication providers");
        return this.json.optBoolean("sponsor_github_approved", false);
    }

    public Authentication setPatreonLogin(final String patreon_login) throws RuntimeException {
        if (this.isAnonymous) throw new RuntimeException("anonmous accounts cannot set authentication providers");
        try {
            this.json.put("login_patreon", patreon_login);
            final String sponsor_patreon = this.json.optString("sponsor_patreon");
            if (sponsor_patreon== null || sponsor_patreon.length() == 0) {
                // pre-set this as the same account, however that might change
                this.json.put("sponsor_patreon", patreon_login);
            }
        } catch (final JSONException e) {
            throw new RuntimeException(e.getMessage());
        }
        return this;
    }

    public String getPatreonLogin() throws RuntimeException {
        if (this.isAnonymous) throw new RuntimeException("anonmous accounts cannot set authentication providers");
        return this.json.optString("login_patreon", "");
    }

    public Authentication setPatreonSponsor(final String patreon_sponsor) throws RuntimeException {
        if (this.isAnonymous) throw new RuntimeException("anonmous accounts cannot set authentication providers");
        try {
            this.json.put("sponsor_patreon", patreon_sponsor);
        } catch (final JSONException e) {
            throw new RuntimeException(e.getMessage());
        }
        return this;
    }

    public String getPatreonSponsor() throws RuntimeException {
        if (this.isAnonymous) throw new RuntimeException("anonmous accounts cannot set authentication providers");
        return this.json.optString("sponsor_patreon", "");
    }

    public Authentication setPatreonSponsorApproved(final boolean approved) throws RuntimeException {
        if (this.isAnonymous) throw new RuntimeException("anonmous accounts cannot set authentication providers");
        try {
            this.json.put("sponsor_patreon_approved", approved);
        } catch (final JSONException e) {
            throw new RuntimeException(e.getMessage());
        }
        return this;
    }

    public boolean getPatreonSponsorApproved() throws RuntimeException {
        if (this.isAnonymous) throw new RuntimeException("anonmous accounts cannot set authentication providers");
        return this.json.optBoolean("sponsor_patreon_approved", false);
    }

    public Authentication setTwitterLogin(final String twitter_login) throws RuntimeException {
        if (this.isAnonymous) throw new RuntimeException("anonmous accounts cannot set authentication providers");
        try {
            this.json.put("login_twitter", twitter_login);
        } catch (final JSONException e) {
            throw new RuntimeException(e.getMessage());
        }
        return this;
    }

    /**
     * set property name from https://schema.org/Person
     * This shall be considered as visible name that the person wants to be displayed
     * @param name
     * @return
     * @throws RuntimeException
     */
    public Authentication setName(final String name) throws RuntimeException {
        if (this.isAnonymous) throw new RuntimeException("anonmous accounts cannot set names");
        try {this.json.put("name", name);} catch (final JSONException e) {
            throw new RuntimeException(e.getMessage());
        }
        return this;
    }

    public String getName() throws RuntimeException {
        if (this.isAnonymous) throw new RuntimeException("anonmous accounts cannot read names");
        return this.json.optString("name", "");
    }

    /**
     * Set the self attribute to swtich on or off a facet for documents that the user
     * has generated themself
     * @param self
     * @return
     * @throws RuntimeException
     */
    public Authentication setSelf(final boolean self) throws RuntimeException {
        if (this.isAnonymous) throw new RuntimeException("anonmous accounts cannot set self");
        try {this.json.put("self", self);} catch (final JSONException e) {
            throw new RuntimeException(e.getMessage());
        }
        return this;
    }

    /**
     * get the "self" attribute which switches on a search facet on the search index
     * for the documents that the user has generated. When self is true, the user can only
     * retrieve documents that the user has generated themself.
     * @return true if only documents generated by the user shall be retrieved
     * @throws RuntimeException
     */
    public boolean getSelf() throws RuntimeException {
        if (this.isAnonymous) return true; // anonymous requests MUST be set to self because that is what a client of a shared accounts sees. It's the purpose that someone else sees only the documents which an authenticated user has generated.
        return this.json.optBoolean("self", true);
    }


    public JSONObject getJSON() {
        return this.json;
    }

    @Override
    public String toString() {
        try {
            return this.json.toString(2);
        } catch (final JSONException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    public static boolean isValid(final String userID) {
        if (userID.length() == 2) {
            return "en".equals(userID);
        }
        if (userID.length() == 9) {
            if (ANONYMOUS_ID.equals(userID)) return true;

            // must consist of only digits and every digit must appear only once;
            final Set<Integer> c = new HashSet<>(); for (int i = 1; i <= 9; i++) c.add(i);
            for (int i = 0; i < 9; i++) {
                if (!c.remove(userID.charAt(i) - 48)) return false;
            }
            return true;
        }
        return false;
    }

    public static String generateRandomID() {
        final StringBuilder sb = new StringBuilder();
        final List<Integer> a = new ArrayList<>();
        for (int i = 1; i <= 9; i++) a.add(i);
        while (a.size() > 1) {
            final int c = a.remove(random.nextInt(a.size()));
            sb.append(Integer.toString(c));
        }
        sb.append(Integer.toString(a.get(0)));
        final String id = sb.toString();
        assert isValid(id);
        return id;
    }

    /*
     * Properties we want for authentication (taken from https://schema.org/Person
     * - email          | used for authorization
     * - identifier     | the id of the account
     * - name           | the public name of the account
     * - alternateName  | a nickname of the account
     *
     */

    public static void main(final String[] args) {
        for (int i = 0; i < 100; i++) {
            System.out.println(generateRandomID());
        }
    }
}
