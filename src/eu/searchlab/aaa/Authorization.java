/**
 *  Authorization
 *  Copyright 19.04.2022 by Michael Peter Christen, @orbiterlab
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

package eu.searchlab.aaa;

import org.json.JSONException;
import org.json.JSONObject;

public class Authorization {

	private final JSONObject json;

	public Authorization() {
		this.json = new JSONObject();
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
}
