/**
 *  JSONObjectValueResolver
 *  Copyright 05.10.2021 by Michael Peter Christen, @orbiterlab
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


/*
 * ATTENTION ATTENTION ATTENTION
 * This class requires that the resolving target - JSONObject and JSONArray from json.org -
 * has some minor modifications:
 * - JSONArray must implement Iterable<Object>
 * - JSONObject must implement a toMap Method
 */

package eu.searchlab.http;

import java.math.BigInteger;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.github.jknack.handlebars.Context;
import com.github.jknack.handlebars.ValueResolver;

public class JSONObjectValueResolver  implements ValueResolver {

    public static JSONObjectValueResolver INSTANCE = new JSONObjectValueResolver();

    /**
     * Resolve the attribute's name in the context object. If a {@link #UNRESOLVED} is returned, the
     * {@link Context context stack} will
     * continue with the next value resolver in the chain.
     *
     * @param context The context object. Not null.
     * @param name The attribute's name. Not null.
     * @return A {@link #UNRESOLVED} is returned, the {@link Context context
     *         stack} will continue with the next value resolver in the chain.
     *         Otherwise, it returns the associated value.
     */
    @Override
    public Object resolve(final Object context, final String name) {
        Object value = null;
        if (context instanceof JSONArray) {
            try {
                if (name.equals("length")) {
                    return ((JSONArray) context).length();
                }
                value = this.resolve(((JSONArray) context).get(Integer.parseInt(name)));
            } catch (final NumberFormatException | JSONException ex) {
                // ignore undefined key and move on, see https://github.com/jknack/handlebars.java/pull/280
                value = null;
            }
        } else if (context instanceof JSONObject) {
            value = this.resolve(((JSONObject) context).opt(name));
        }
        return value == null ? UNRESOLVED : value;
    }

    /**
     * Resolve the the context object by optionally converting the value if necessary.
     * If a {@link #UNRESOLVED} is returned, the {@link Context context stack} will continue with
     * the next value resolver in the chain.
     *
     * @param context The context object. Not null.
     * @return A {@link #UNRESOLVED} is returned, the {@link Context context
     *         stack} will continue with the next value resolver in the chain.
     *         Otherwise, it returns the associated value.
     */
    @Override
    public Object resolve(final Object context) {
        return resolvei(context);
    }

    public static Object resolvei(final Object context) {
        if (context == null) {
            return null;
        }
        if (context instanceof Boolean) {
            return ((Boolean) context).booleanValue();
        }
        if (context instanceof BigInteger) {
            return ((BigInteger) context).intValue();
        }
        if (context instanceof Double) {
            return ((Double) context).doubleValue();
        }
        if (context instanceof Integer) {
            return ((Integer) context).intValue();
        }
        if (context instanceof Long) {
            return ((Long) context).longValue();
        }
        if (context instanceof String) {
            return ((String) context).toString();
        }
        if (context instanceof JSONArray) {
            return (context);
        }
        if (context instanceof JSONObject) {
            return ((JSONObject) context).toMap();
        }
        return UNRESOLVED;
    }

    /**
     * List all the properties and their values for the given object.
     *
     * @param context The context object. Not null.
     * @return All the properties and their values for the given object.
     */
    @Override
    public Set<Entry<String, Object>> propertySet(final Object context) {
        if (context instanceof JSONObject) {
            final JSONObject node = (JSONObject) context;
            final Iterator<String> fieldNames = node.keys();
            final Map<String, Object> result = new LinkedHashMap<>();
            while (fieldNames.hasNext()) {
                final String key = fieldNames.next();
                result.put(key, this.resolve(node, key));
            }
            return result.entrySet();
        }
        return Collections.emptySet();
    }

}
