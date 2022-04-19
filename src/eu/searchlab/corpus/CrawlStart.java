/**
 *  CrawlStart
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

package eu.searchlab.corpus;

import org.json.JSONException;
import org.json.JSONObject;

import eu.searchlab.aaa.Authentication;

public class CrawlStart {

	public static JSONObject defaultValues = new JSONObject(true);
    static {
        try {
			defaultValues.put("crawlingMode", "url");
	        defaultValues.put("crawlingURL", "");
	        defaultValues.put("sitemapURL", "");
	        defaultValues.put("crawlingFile", "");
	        defaultValues.put("crawlingDepth", 3);
	        defaultValues.put("crawlingDepthExtension", "");
	        defaultValues.put("range", "domain");
	        defaultValues.put("mustmatch", ".*");
	        defaultValues.put("mustnotmatch", ".*\\.(js|css|jpg|jpeg|png|dmg|mpg|mpeg|zip|gz|exe|pkg)");
	        defaultValues.put("ipMustmatch", ".*");
	        defaultValues.put("ipMustnotmatch", "");
	        defaultValues.put("indexmustmatch", ".*");
	        defaultValues.put("indexmustnotmatch", "");
	        defaultValues.put("deleteold", "off");
	        defaultValues.put("deleteIfOlderNumber", 0);
	        defaultValues.put("deleteIfOlderUnit", "day");
	        defaultValues.put("recrawl", "nodoubles");
	        defaultValues.put("reloadIfOlderNumber", 0);
	        defaultValues.put("reloadIfOlderUnit", "day");
	        defaultValues.put("crawlingDomMaxCheck", "off");
	        defaultValues.put("crawlingDomMaxPages", 1000);
	        defaultValues.put("crawlingQ", "off");
	        defaultValues.put("cachePolicy", "if fresh");
	        defaultValues.put("agentName", "");
	        defaultValues.put("priority", 0);
	        defaultValues.put("loaderHeadless", "true");
	        defaultValues.put("userId", Authentication.ANONYMOUS_ID);
	        defaultValues.put("collection", "user"); // corpus name
		} catch (final JSONException e) {
		}
    }

	private final JSONObject json;

	public CrawlStart() {
		this.json = new JSONObject();
	}

	public CrawlStart set(final String key, final String value) throws RuntimeException {
		final Object v = defaultValues.opt(key);
		if (v == null) throw new RuntimeException("key " + key + " not allowed as attribute key");
		if (!(v instanceof String)) throw new RuntimeException("value" + value + " not allowed as attribute value type for key " + key);
		try {
			this.json.put(key, value);
		} catch (final JSONException e) {
			throw new RuntimeException(e.getMessage());
		}
		return this;
	}

	public CrawlStart set(final String key, final int value) throws RuntimeException {
		final Object v = defaultValues.opt(key);
		if (v == null) throw new RuntimeException("key " + key + " not allowed as attribute key");
		if (!(v instanceof Number)) throw new RuntimeException("value" + value + " not allowed as attribute value type for key " + key);
		try {
			this.json.put(key, value);
		} catch (final JSONException e) {
			throw new RuntimeException(e.getMessage());
		}
		return this;
	}

	public CrawlStart set(final String key, final long value) throws RuntimeException {
		final Object v = defaultValues.opt(key);
		if (v == null) throw new RuntimeException("key " + key + " not allowed as attribute key");
		if (!(v instanceof Number)) throw new RuntimeException("value" + value + " not allowed as attribute value type for key " + key);
		try {
			this.json.put(key, value);
		} catch (final JSONException e) {
			throw new RuntimeException(e.getMessage());
		}
		return this;
	}

	public String getString(final String key) throws RuntimeException {
		final Object v = defaultValues.opt(key);
		if (v == null) throw new RuntimeException("key " + key + " not allowed as attribute key");
		if (!(v instanceof String)) throw new RuntimeException("String not allowed as attribute value type for key " + key);
		try {
			return this.json.getString(key);
		} catch (final JSONException e) {
			throw new RuntimeException(e.getMessage());
		}
	}

	public int getInteger(final String key) throws RuntimeException {
		final Object v = defaultValues.opt(key);
		if (v == null) throw new RuntimeException("key " + key + " not allowed as attribute key");
		if (!(v instanceof Number)) throw new RuntimeException("Number not allowed as attribute value type for key " + key);
		try {
			return this.json.getInt(key);
		} catch (final JSONException e) {
			throw new RuntimeException(e.getMessage());
		}
	}

	public long getLong(final String key) throws RuntimeException {
		final Object v = defaultValues.opt(key);
		if (v == null) throw new RuntimeException("key " + key + " not allowed as attribute key");
		if (!(v instanceof Number)) throw new RuntimeException("Number not allowed as attribute value type for key " + key);
		try {
			return this.json.getLong(key);
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

}
