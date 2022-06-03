/**
 *  ServiceRequest
 *  Copyright 03.06.2022 by Michael Peter Christen, @orbiterlab
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

package eu.searchlab.http;

import org.json.JSONObject;

import eu.searchlab.aaaaa.Authentication;


public class ServiceRequest {

	private final JSONObject post;
	private final String user; // the user_id
	private final String path; // this looks like "/js/jquery.min.js", a root path looks like "/"
	private final String query; // the part after "?"

	public ServiceRequest(final JSONObject post, final String user, final String path, final String query) {
		this.post = post == null ? new JSONObject() : post;
		this.user = user == null ? Authentication.ANONYMOUS_ID : user;
		this.path = path == null ? "" : path;
		this.query = query == null ? "" : query;
	}

	public String getUser() {
		return this.user;
	}

	public String getPath() {
		return this.path;
	}

	public String getQuery() {
		return this.query;
	}

	public JSONObject getPost() {
		return this.post;
	}

	public String get(final String key, final String dflt) {
		return this.post.optString(key, dflt);
	}

	public boolean get(final String key, final boolean dflt) {
		return this.post.optBoolean(key, dflt);
	}

	public int get(final String key, final int dflt) {
		return this.post.optInt(key, dflt);
	}

	public long get(final String key, final long dflt) {
		return this.post.optLong(key, dflt);
	}

	public double get(final String key, final double dflt) {
		return this.post.optDouble(key, dflt);
	}

}
