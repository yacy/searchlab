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

package eu.searchlab.http.aaa;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class Authentication {

	private final static Random random = new Random(System.currentTimeMillis());

	public final static String ANONYMOUS_ID = "112358132";

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

	public static void main(final String[] args) {
		for (int i = 0; i < 100; i++) {
			System.out.println(generateRandomID());
		}
	}
}
