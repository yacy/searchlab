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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.json.JSONException;
import org.json.JSONObject;

public class Authentication {

	private final static Random random = new Random(System.currentTimeMillis());

	public final static String ANONYMOUS_ID = "112358132";
	public final static String[] GOLDEN_ID = new String[]{"123456789", "123456798",  "123456987",  "123459876",  "123498765",  "123987654",  "129876543", "198765432", "987654321"}; // includes at this time only special ids

	private final JSONObject json;

	public Authentication() {
		this.json = new JSONObject();
	}

	/**
	 * set property email from https://schema.org/Person
	 * @param email - the email address; must contain a '@'
	 * @return this
	 * @throws RuntimeException
	 */
	public Authentication setEmail(final String email) throws RuntimeException {
		if (email.indexOf('@') < 0) throw new RuntimeException("email address invalid: " + email);
		try {
			this.json.put("email", email);
		} catch (final JSONException e) {
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
		try {
			return this.json.getString("email");
		} catch (final JSONException e) {
			throw new RuntimeException(e.getMessage());
		}
	}

	/**
	 * set property identifier from https://schema.org/Person
	 * @param identifier
	 * @return
	 * @throws RuntimeException
	 */
	public Authentication setIdentifier(final String identifier) throws RuntimeException {
		if (!isValid(identifier)) throw new RuntimeException("identifier invalid: " + identifier);
		try {
			this.json.put("identifier", identifier);
		} catch (final JSONException e) {
			throw new RuntimeException(e.getMessage());
		}
		return this;
	}

	public String getIdentifier() throws RuntimeException {
		try {
			return this.json.getString("identifier");
		} catch (final JSONException e) {
			throw new RuntimeException(e.getMessage());
		}
	}

	/**
	 * set property name from https://schema.org/Person
	 * This shall be considered as visible name that the person wants to be displayed
	 * @param name
	 * @return
	 * @throws RuntimeException
	 */
	public Authentication setName(final String name) throws RuntimeException {
		try {
			this.json.put("name", name);
		} catch (final JSONException e) {
			throw new RuntimeException(e.getMessage());
		}
		return this;
	}

	public String getName() throws RuntimeException {
		try {
			return this.json.getString("name");
		} catch (final JSONException e) {
			throw new RuntimeException(e.getMessage());
		}
	}

	/**
	 * set property alternateName from https://schema.org/Person
	 * This shall be considered as the nickname of the person which the user wants to be displayed and used as handle for links and sharing.
	 * If the
	 * @param alternateName
	 * @return
	 * @throws RuntimeException
	 */
	public Authentication setAlternateName(final String alternateName) throws RuntimeException {
		try {
			this.json.put("alternateName", alternateName);
		} catch (final JSONException e) {
			throw new RuntimeException(e.getMessage());
		}
		return this;
	}

	public String getAlternateName() throws RuntimeException {
		try {
			return this.json.getString("alternateName");
		} catch (final JSONException e) {
			throw new RuntimeException(e.getMessage());
		}
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
