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

public class ServiceRequest {

	public final JSONObject post;
	public final String path; // this looks like "/js/jquery.min.js", a root path looks like "/"
	public final String user; // the user_id
	public final String query; // the part after "?"

	public ServiceRequest(final JSONObject post, final String path, final String user, final String query) {
		this.post = post;
		this.path = path;
		this.user = user;
		this.query = query;
	}
}
