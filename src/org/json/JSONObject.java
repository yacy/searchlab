/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * This class was taken from
 * https://android.googlesource.com/platform/libcore/+/refs/heads/master/json/src/main/java/org/json
 * and slightly modified (by mc@yacy.net):
 * - removed dependency from other libraries (i.e. android.compat.annotation)
 * - fixed "statement unnecessary nested" warnings
 * - fixed raw type declarations
 * - added keepOrder option to initializer to reduce memory footprint if order is not required
 * - added deprecated flag to has() an get() methods (see comment in code)
 * - inlined opt() where appropriate
 * - removed unnecessary 'throws JSONException' where possible
 */

package org.json;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

// Note: this class was written without inspecting the non-free org.json sourcecode.

/**
 * A modifiable set of name/value mappings. Names are unique, non-null strings.
 * Values may be any mix of {@link JSONObject JSONObjects}, {@link JSONArray
 * JSONArrays}, Strings, Booleans, Integers, Longs, Doubles or {@link #NULL}.
 * Values may not be {@code null}, {@link Double#isNaN() NaNs}, {@link
 * Double#isInfinite() infinities}, or of any type not listed here.
 *
 * <p>This class can coerce values to another type when requested.
 * <ul>
 *   <li>When the requested type is a boolean, strings will be coerced using a
 *       case-insensitive comparison to "true" and "false".
 *   <li>When the requested type is a double, other {@link Number} types will
 *       be coerced using {@link Number#doubleValue() doubleValue}. Strings
 *       that can be coerced using {@link Double#valueOf(String)} will be.
 *   <li>When the requested type is an int, other {@link Number} types will
 *       be coerced using {@link Number#intValue() intValue}. Strings
 *       that can be coerced using {@link Double#valueOf(String)} will be,
 *       and then cast to int.
 *   <li><a name="lossy">When the requested type is a long, other {@link Number} types will
 *       be coerced using {@link Number#longValue() longValue}. Strings
 *       that can be coerced using {@link Double#valueOf(String)} will be,
 *       and then cast to long. This two-step conversion is lossy for very
 *       large values. For example, the string "9223372036854775806" yields the
 *       long 9223372036854775807.</a>
 *   <li>When the requested type is a String, other non-null values will be
 *       coerced using {@link String#valueOf(Object)}. Although null cannot be
 *       coerced, the sentinel value {@link JSONObject#NULL} is coerced to the
 *       string "null".
 * </ul>
 *
 * <p>This class can look up both mandatory and optional values:
 * <ul>
 *   <li>Use <code>get<i>Type</i>()</code> to retrieve a mandatory value. This
 *       fails with a {@code JSONException} if the requested name has no value
 *       or if the value cannot be coerced to the requested type.
 *   <li>Use <code>opt<i>Type</i>()</code> to retrieve an optional value. This
 *       returns a system- or user-supplied default if the requested name has no
 *       value or if the value cannot be coerced to the requested type.
 * </ul>
 *
 * <p><strong>Warning:</strong> this class represents null in two incompatible
 * ways: the standard Java {@code null} reference, and the sentinel value {@link
 * JSONObject#NULL}. In particular, calling {@code put(name, null)} removes the
 * named entry from the object but {@code put(name, JSONObject.NULL)} stores an
 * entry whose value is {@code JSONObject.NULL}.
 *
 * <p>Instances of this class are not thread safe. Although this class is
 * nonfinal, it was not designed for inheritance and should not be subclassed.
 * In particular, self-use by overrideable methods is not specified. See
 * <i>Effective Java</i> Item 17, "Design and Document or inheritance or else
 * prohibit it" for further information.
 */
public class JSONObject {

    private static final Double NEGATIVE_ZERO = -0d;

    /**
     * A sentinel value used to explicitly define a name with no value. Unlike
     * {@code null}, names with this value:
     * <ul>
     *   <li>show up in the {@link #names} array
     *   <li>show up in the {@link #keys} iterator
     *   <li>return {@code true} for {@link #has(String)}
     *   <li>do not throw on {@link #get(String)}
     *   <li>are included in the encoded JSON string.
     * </ul>
     *
     * <p>This value violates the general contract of {@link Object#equals} by
     * returning true when compared to {@code null}. Its {@link #toString}
     * method returns "null".
     */
    public static final Object NULL = new Object() {
        @Override public boolean equals(final Object o) {
            return o == this || o == null; // API specifies this broken equals implementation
        }
        // at least make the broken equals(null) consistent with Objects.hashCode(null).
        @Override public int hashCode() { return Objects.hashCode(null); }
        @Override public String toString() {
            return "null";
        }
    };

    private final Map<String, Object> nameValuePairs;

    /**
     * Creates a {@code JSONObject} with no name/value mappings.
     */
    public JSONObject() {
        this.nameValuePairs = new LinkedHashMap<>();
    }

    /**
     * Creates a {@code JSONObject} with no name/value mappings.
     *
     * @param keepOrder if set to true, the elements will preserve its insert order
     */
    public JSONObject(final boolean keepOrder) {
        this.nameValuePairs = keepOrder ? new LinkedHashMap<>() : new HashMap<>();
    }

    /**
     * Creates a new {@code JSONObject} by copying all name/value mappings from
     * the given map.
     *
     * @param copyFrom a map whose keys are of type {@link String} and whose
     *     values are of supported types.
     * @throws NullPointerException if any of the map's keys are null.
     */
    /* (accept a raw type for API compatibility) */
    public JSONObject(final Map<?, ?> copyFrom) {
        this();
        final Map<?, ?> contentsTyped = copyFrom;
        for (final Map.Entry<?, ?> entry : contentsTyped.entrySet()) {
            /*
             * Deviate from the original by checking that keys are non-null and
             * of the proper type. (We still defer validating the values).
             */
            final String key = (String) entry.getKey();
            if (key == null) {
                throw new NullPointerException("key == null");
            }
            this.nameValuePairs.put(key, wrap(entry.getValue()));
        }
    }

    /**
     * Creates a new {@code JSONObject} with name/value mappings from the next
     * object in the tokener.
     *
     * @param readFrom a tokener whose nextValue() method will yield a
     *     {@code JSONObject}.
     * @throws JSONException if the parse fails or doesn't yield a
     *     {@code JSONObject}.
     */
    public JSONObject(final JSONTokener readFrom) throws JSONException {
        /*
         * Getting the parser to populate this could get tricky. Instead, just
         * parse to temporary JSONObject and then steal the data from that.
         */
        final Object object = readFrom.nextValue();
        if (object instanceof JSONObject) {
            this.nameValuePairs = ((JSONObject) object).nameValuePairs;
        } else {
            throw JSON.typeMismatch(object, "JSONObject");
        }
    }

    /**
     * Creates a new {@code JSONObject} with name/value mappings from the JSON
     * string.
     *
     * @param json a JSON-encoded string containing an object.
     * @throws JSONException if the parse fails or doesn't yield a {@code
     *     JSONObject}.
     */
    public JSONObject(final String json) throws JSONException {
        this(new JSONTokener(json));
    }

    /**
     * Creates a new {@code JSONObject} by copying mappings for the listed names
     * from the given object. Names that aren't present in {@code copyFrom} will
     * be skipped.
     */
    public JSONObject(final JSONObject copyFrom, final String [] names) {
        this();
        for (final String name : names) {
            final Object value = copyFrom.nameValuePairs.get(name);
            if (value != null) {
                this.nameValuePairs.put(name, value);
            }
        }
    }

    public Map<String, Object> toMap() {
        return this.nameValuePairs;
    }

    /**
     * Returns the number of name/value mappings in this object.
     */
    public int length() {
        return this.nameValuePairs.size();
    }

    /**
     * Maps {@code name} to {@code value}, clobbering any existing name/value
     * mapping with the same name.
     *
     * @return this object.
     */
    public JSONObject put(final String name, final boolean value) {
        this.nameValuePairs.put(name, value);
        return this;
    }

    /**
     * Maps {@code name} to {@code value}, clobbering any existing name/value
     * mapping with the same name.
     *
     * @param value a finite value. May not be {@link Double#isNaN() NaNs} or
     *     {@link Double#isInfinite() infinities}.
     * @return this object.
     * @throws JSONException
     */
    public JSONObject put(final String name, final double value) throws JSONException {
        this.nameValuePairs.put(name, JSON.checkDouble(value));
        return this;
    }

    /**
     * Maps {@code name} to {@code value}, clobbering any existing name/value
     * mapping with the same name.
     *
     * @return this object.
     */
    public JSONObject put(final String name, final int value) {
        this.nameValuePairs.put(name, value);
        return this;
    }

    /**
     * Maps {@code name} to {@code value}, clobbering any existing name/value
     * mapping with the same name.
     *
     * @return this object.
     */
    public JSONObject put(final String name, final long value) {
        this.nameValuePairs.put(name, value);
        return this;
    }

    /**
     * Maps {@code name} to {@code value}, clobbering any existing name/value
     * mapping with the same name.
     *
     * @return this object.
     */
    public JSONObject put(final String name, final String value) {
        if (value == null) {
            this.nameValuePairs.remove(name);
            return this;
        }
        this.nameValuePairs.put(name, value);
        return this;
    }

    /**
     * Maps {@code name} to {@code value}, clobbering any existing name/value
     * mapping with the same name.
     *
     * @return this object.
     */
    public JSONObject put(final String name, final JSONObject value) {
        if (value == null) {
            this.nameValuePairs.remove(name);
            return this;
        }
        this.nameValuePairs.put(name, value);
        return this;
    }

    /**
     * Maps {@code name} to {@code value}, clobbering any existing name/value
     * mapping with the same name.
     *
     * @return this object.
     */
    public JSONObject put(final String name, final JSONArray value) {
        if (value == null) {
            this.nameValuePairs.remove(name);
            return this;
        }
        this.nameValuePairs.put(name, value);
        return this;
    }

    /**
     * Maps {@code name} to {@code value}, clobbering any existing name/value
     * mapping with the same name. If the value is {@code null}, any existing
     * mapping for {@code name} is removed.
     *
     * @param value a {@link JSONObject}, {@link JSONArray}, String, Boolean,
     *     Integer, Long, Double, {@link #NULL}, or {@code null}. May not be
     *     {@link Double#isNaN() NaNs} or {@link Double#isInfinite()
     *     infinities}.
     * @return this object.
     * @throws JSONException
     *
     * Marked as deprecated because  put should be made with an actual value
     */
    @Deprecated
    public JSONObject put(final String name, final Object value) throws JSONException {
        if (value == null) {
            this.nameValuePairs.remove(name);
            return this;
        }
        if (value instanceof Number) {
            // deviate from the original by checking all Numbers, not just floats & doubles
            JSON.checkDouble(((Number) value).doubleValue());
        }
        this.nameValuePairs.put(name, value);
        return this;
    }

    /**
     * Equivalent to {@code put(name, value)} when both parameters are non-null;
     * does nothing otherwise.
     * @throws JSONException
     */
    @Deprecated
    public JSONObject putOpt(final String name, final Object value) throws JSONException {
        if (name == null || value == null) {
            return this;
        }
        return this.put(name, value);
    }

    public void putAll(final JSONObject other) {
        this.nameValuePairs.putAll(other.nameValuePairs);
    }

    /**
     * Appends {@code value} to the array already mapped to {@code name}. If
     * this object has no mapping for {@code name}, this inserts a new mapping.
     * If the mapping exists but its value is not an array, the existing
     * and new values are inserted in order into a new array which is itself
     * mapped to {@code name}. In aggregate, this allows values to be added to a
     * mapping one at a time.
     *
     * <p> Note that {@code append(String, Object)} provides better semantics.
     * In particular, the mapping for {@code name} will <b>always</b> be a
     * {@link JSONArray}. Using {@code accumulate} will result in either a
     * {@link JSONArray} or a mapping whose type is the type of {@code value}
     * depending on the number of calls to it.
     *
     * @param value a {@link JSONObject}, {@link JSONArray}, String, Boolean,
     *     Integer, Long, Double, {@link #NULL} or null. May not be {@link
     *     Double#isNaN() NaNs} or {@link Double#isInfinite() infinities}.
     * @throws JSONException
     */
    // TODO: Change {@code append) to {@link #append} when append is
    // unhidden.
    @Deprecated
    public JSONObject accumulate(final String name, final Object value) throws JSONException {
        final Object current = this.nameValuePairs.get(name);
        if (current == null) {
            return this.put(name, value);
        }

        if (current instanceof JSONArray) {
            final JSONArray array = (JSONArray) current;
            array.checkedPut(value);
        } else {
            final JSONArray array = new JSONArray(2);
            array.checkedPut(current);
            array.checkedPut(value);
            this.nameValuePairs.put(name, array);
        }
        return this;
    }

    /**
     * Appends values to the array mapped to {@code name}. A new {@link JSONArray}
     * mapping for {@code name} will be inserted if no mapping exists. If the existing
     * mapping for {@code name} is not a {@link JSONArray}, a {@link JSONException}
     * will be thrown.
     *
     * @throws JSONException if {@code name} is {@code null} or if the mapping for
     *         {@code name} is non-null and is not a {@link JSONArray}.
     */
    @Deprecated
    public JSONObject append(final String name, final Object value) throws JSONException {
        final Object current = this.nameValuePairs.get(name);

        final JSONArray array;
        if (current instanceof JSONArray) {
            array = (JSONArray) current;
        } else if (current == null) {
            final JSONArray newArray = new JSONArray();
            this.nameValuePairs.put(name, newArray);
            array = newArray;
        } else {
            throw new JSONException("Key " + name + " is not a JSONArray");
        }

        array.checkedPut(value);

        return this;
    }

    /**
     * Removes the named mapping if it exists; does nothing otherwise.
     *
     * @return the value previously mapped by {@code name}, or null if there was
     *     no such mapping.
     */
    public Object remove(final String name) {
        return this.nameValuePairs.remove(name);
    }

    /**
     * Returns true if this object has no mapping for {@code name} or if it has
     * a mapping whose value is {@link #NULL}.
     */
    public boolean isNull(final String name) {
        final Object value = this.nameValuePairs.get(name);
        return value == null || value == NULL;
    }

    /**
     * Returns true if this object has a mapping for {@code name}. The mapping
     * may be {@link #NULL}.
     *
     * This method has the deprecated flag because usage of this method leads to poor
     * code performance if used in combination with get(). Its much better to opt() and
     * check the result instead of doing two look-ups (first has(), then get()).
     */
    @Deprecated
    public boolean has(final String name) {
        return this.nameValuePairs.containsKey(name);
    }

    /**
     * Returns the value mapped by {@code name}, or throws if no such mapping exists.
     *
     * @throws JSONException if no such mapping exists.
     *
     * This method has the deprecated flag because usage of this method leads to poor
     * code performance if used in combination with has(). Its much better to opt() and
     * check the result instead of doing two look-ups (first has(), then get()).
     */
    @Deprecated
    public Object get(final String name) throws JSONException {
        final Object result = this.nameValuePairs.get(name);
        if (result == null) {
            throw new JSONException("No value for " + name);
        }
        return result;
    }

    /**
     * Returns the value mapped by {@code name}, or null if no such mapping
     * exists.
     */
    public Object opt(final String name) {
        return this.nameValuePairs.get(name);
    }

    /**
     * Returns the value mapped by {@code name} if it exists and is a boolean or
     * can be coerced to a boolean, or throws otherwise.
     *
     * @throws JSONException if the mapping doesn't exist or cannot be coerced
     *     to a boolean.
     */
    public boolean getBoolean(final String name) throws JSONException {
        final Object object = this.get(name);
        final Boolean result = JSON.toBoolean(object);
        if (result == null) {
            throw JSON.typeMismatch(name, object, "boolean");
        }
        return result;
    }

    /**
     * Returns the value mapped by {@code name} if it exists and is a boolean or
     * can be coerced to a boolean, or false otherwise.
     */
    public boolean optBoolean(final String name) {
        return this.optBoolean(name, false);
    }

    /**
     * Returns the value mapped by {@code name} if it exists and is a boolean or
     * can be coerced to a boolean, or {@code fallback} otherwise.
     */
    public boolean optBoolean(final String name, final boolean fallback) {
        final Object object = this.nameValuePairs.get(name);
        final Boolean result = JSON.toBoolean(object);
        return result != null ? result : fallback;
    }

    /**
     * Returns the value mapped by {@code name} if it exists and is a double or
     * can be coerced to a double, or throws otherwise.
     *
     * @throws JSONException if the mapping doesn't exist or cannot be coerced
     *     to a double.
     */
    public double getDouble(final String name) throws JSONException {
        final Object object = this.get(name);
        final Double result = JSON.toDouble(object);
        if (result == null) {
            throw JSON.typeMismatch(name, object, "double");
        }
        return result;
    }

    /**
     * Returns the value mapped by {@code name} if it exists and is a double or
     * can be coerced to a double, or {@code NaN} otherwise.
     */
    public double optDouble(final String name) {
        return this.optDouble(name, Double.NaN);
    }

    /**
     * Returns the value mapped by {@code name} if it exists and is a double or
     * can be coerced to a double, or {@code fallback} otherwise.
     */
    public double optDouble(final String name, final double fallback) {
        final Object object = this.nameValuePairs.get(name);
        final Double result = JSON.toDouble(object);
        return result != null ? result : fallback;
    }

    /**
     * Returns the value mapped by {@code name} if it exists and is an int or
     * can be coerced to an int, or throws otherwise.
     *
     * @throws JSONException if the mapping doesn't exist or cannot be coerced
     *     to an int.
     */
    public int getInt(final String name) throws JSONException {
        final Object object = this.get(name);
        final Integer result = JSON.toInteger(object);
        if (result == null) {
            throw JSON.typeMismatch(name, object, "int");
        }
        return result;
    }

    /**
     * Returns the value mapped by {@code name} if it exists and is an int or
     * can be coerced to an int, or 0 otherwise.
     */
    public int optInt(final String name) {
        return this.optInt(name, 0);
    }

    /**
     * Returns the value mapped by {@code name} if it exists and is an int or
     * can be coerced to an int, or {@code fallback} otherwise.
     */
    public int optInt(final String name, final int fallback) {
        final Object object = this.nameValuePairs.get(name);
        final Integer result = JSON.toInteger(object);
        return result != null ? result : fallback;
    }

    /**
     * Returns the value mapped by {@code name} if it exists and is a long or
     * can be coerced to a long, or throws otherwise.
     * Note that JSON represents numbers as doubles,
     * so this is <a href="#lossy">lossy</a>; use strings to transfer numbers via JSON.
     *
     * @throws JSONException if the mapping doesn't exist or cannot be coerced
     *     to a long.
     */
    public long getLong(final String name) throws JSONException {
        final Object object = this.get(name);
        final Long result = JSON.toLong(object);
        if (result == null) {
            throw JSON.typeMismatch(name, object, "long");
        }
        return result;
    }

    /**
     * Returns the value mapped by {@code name} if it exists and is a long or
     * can be coerced to a long, or 0 otherwise. Note that JSON represents numbers as doubles,
     * so this is <a href="#lossy">lossy</a>; use strings to transfer numbers via JSON.
     */
    public long optLong(final String name) {
        return this.optLong(name, 0L);
    }

    /**
     * Returns the value mapped by {@code name} if it exists and is a long or
     * can be coerced to a long, or {@code fallback} otherwise. Note that JSON represents
     * numbers as doubles, so this is <a href="#lossy">lossy</a>; use strings to transfer
     * numbers via JSON.
     */
    public long optLong(final String name, final long fallback) {
        final Object object = this.nameValuePairs.get(name);
        final Long result = JSON.toLong(object);
        return result != null ? result : fallback;
    }

    /**
     * Returns the value mapped by {@code name} if it exists, coercing it if
     * necessary, or throws if no such mapping exists.
     *
     * @throws JSONException if no such mapping exists.
     */
    public String getString(final String name) throws JSONException {
        final Object object = this.get(name);
        final String result = JSON.toString(object);
        if (result == null) {
            throw JSON.typeMismatch(name, object, "String");
        }
        return result;
    }

    /**
     * Returns the value mapped by {@code name} if it exists, coercing it if
     * necessary, or the empty string if no such mapping exists.
     */
    public String optString(final String name) {
        return this.optString(name, "");
    }

    /**
     * Returns the value mapped by {@code name} if it exists, coercing it if
     * necessary, or {@code fallback} if no such mapping exists.
     */
    public String optString(final String name, final String fallback) {
        final Object object = this.nameValuePairs.get(name);
        final String result = JSON.toString(object);
        return result != null ? result : fallback;
    }

    /**
     * Returns the value mapped by {@code name} if it exists and is a {@code
     * JSONArray}, or throws otherwise.
     *
     * @throws JSONException if the mapping doesn't exist or is not a {@code
     *     JSONArray}.
     */
    public JSONArray getJSONArray(final String name) throws JSONException {
        final Object object = this.get(name);
        if (object instanceof JSONArray) {
            return (JSONArray) object;
        }
        throw JSON.typeMismatch(name, object, "JSONArray");
    }

    /**
     * Returns the value mapped by {@code name} if it exists and is a {@code
     * JSONArray}, or null otherwise.
     */
    public JSONArray optJSONArray(final String name) {
        final Object object = this.nameValuePairs.get(name);
        return object instanceof JSONArray ? (JSONArray) object : null;
    }

    /**
     * Returns the value mapped by {@code name} if it exists and is a {@code
     * JSONObject}, or throws otherwise.
     *
     * @throws JSONException if the mapping doesn't exist or is not a {@code
     *     JSONObject}.
     */
    public JSONObject getJSONObject(final String name) throws JSONException {
        final Object object = this.get(name);
        if (object instanceof JSONObject) {
            return (JSONObject) object;
        }
        throw JSON.typeMismatch(name, object, "JSONObject");
    }

    /**
     * Returns the value mapped by {@code name} if it exists and is a {@code
     * JSONObject}, or null otherwise.
     */
    public JSONObject optJSONObject(final String name) {
        final Object object = this.nameValuePairs.get(name);
        return object instanceof JSONObject ? (JSONObject) object : null;
    }

    /**
     * Returns an array with the values corresponding to {@code names}. The
     * array contains null for names that aren't mapped. This method returns
     * null if {@code names} is either null or empty.
     */
    public JSONArray toJSONArray(final JSONArray names) {
        if (names == null) {
            return null;
        }
        final int length = names.length();
        if (length == 0) {
            return null;
        }
        final JSONArray result = new JSONArray(length);
        for (int i = 0; i < length; i++) {
            final String name = JSON.toString(names.opt(i));
            result.put(this.nameValuePairs.get(name));
        }
        return result;
    }

    /**
     * Returns an iterator of the {@code String} names in this object. The
     * returned iterator supports {@link Iterator#remove() remove}, which will
     * remove the corresponding mapping from this object. If this object is
     * modified after the iterator is returned, the iterator's behavior is
     * undefined. The order of the keys is undefined.
     */
    public Iterator<String> keys() {
        return this.nameValuePairs.keySet().iterator();
    }

    /**
     * Returns the set of {@code String} names in this object. The returned set
     * is a view of the keys in this object. {@link Set#remove(Object)} will remove
     * the corresponding mapping from this object and set iterator behaviour
     * is undefined if this object is modified after it is returned.
     *
     * See {@link #keys()}.
     *
     * @hide.
     */
    public Set<String> keySet() {
        return this.nameValuePairs.keySet();
    }

    /**
     * Returns an array containing the string names in this object. This method
     * returns null if this object contains no mappings.
     */
    public JSONArray names() {
        return this.nameValuePairs.isEmpty()
                ? null
                : new JSONArray(new ArrayList<>(this.nameValuePairs.keySet()));
    }

    /**
     * Encodes this object as a compact JSON string, such as:
     * <pre>{"query":"Pizza","locations":[94043,90210]}</pre>
     */
    @Override public String toString() {
        try {
            final JSONStringer stringer = new JSONStringer();
            this.writeTo(stringer);
            return stringer.toString();
        } catch (final JSONException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    /**
     * Encodes this object as a human readable JSON string for debugging, such
     * as:
     * <pre>
     * {
     *     "query": "Pizza",
     *     "locations": [
     *         94043,
     *         90210
     *     ]
     * }</pre>
     *
     * @param indentSpaces the number of spaces to indent for each level of
     *     nesting.
     */
    public String toString(final int indentSpaces) {
        try {
            final JSONStringer stringer = new JSONStringer(indentSpaces);
            this.writeTo(stringer);
            return stringer.toString();
        } catch (final JSONException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    void writeTo(final JSONStringer stringer) throws JSONException {
        stringer.object();
        for (final Map.Entry<String, Object> entry : this.nameValuePairs.entrySet()) {
            stringer.key(entry.getKey()).value(entry.getValue());
        }
        stringer.endObject();
    }

    /**
     * Encodes the number as a JSON string.
     *
     * @param number a finite value. May not be {@link Double#isNaN() NaNs} or
     *     {@link Double#isInfinite() infinities}.
     * @throws JSONException
     */
    public static String numberToString(final Number number) throws JSONException {
        if (number == null) {
            throw new JSONException("Number must be non-null");
        }

        final double doubleValue = number.doubleValue();
        JSON.checkDouble(doubleValue);

        // the original returns "-0" instead of "-0.0" for negative zero
        if (number.equals(NEGATIVE_ZERO)) {
            return "-0";
        }

        final long longValue = number.longValue();
        if (doubleValue == longValue) {
            return Long.toString(longValue);
        }

        return number.toString();
    }

    /**
     * Encodes {@code data} as a JSON string. This applies quotes and any
     * necessary character escaping.
     *
     * @param data the string to encode. Null will be interpreted as an empty
     *     string.
     */
    public static String quote(final String data) {
        if (data == null) {
            return "\"\"";
        }
        try {
            final JSONStringer stringer = new JSONStringer();
            stringer.open(JSONStringer.Scope.NULL, "");
            stringer.value(data);
            stringer.close(JSONStringer.Scope.NULL, JSONStringer.Scope.NULL, "");
            return stringer.toString();
        } catch (final JSONException e) {
            throw new AssertionError();
        }
    }

    /**
     * Wraps the given object if necessary.
     *
     * <p>If the object is null or , returns {@link #NULL}.
     * If the object is a {@code JSONArray} or {@code JSONObject}, no wrapping is necessary.
     * If the object is {@code NULL}, no wrapping is necessary.
     * If the object is an array or {@code Collection}, returns an equivalent {@code JSONArray}.
     * If the object is a {@code Map}, returns an equivalent {@code JSONObject}.
     * If the object is a primitive wrapper type or {@code String}, returns the object.
     * Otherwise if the object is from a {@code java} package, returns the result of {@code toString}.
     * If wrapping fails, returns null.
     */
    protected static Object wrap(final Object o) {
        if (o == null) {
            return NULL;
        }
        if (o instanceof JSONArray || o instanceof JSONObject) {
            return o;
        }
        if (o.equals(NULL)) {
            return o;
        }
        try {
            if (o instanceof Collection) {
                return new JSONArray((Collection<?>) o);
            } else if (o.getClass().isArray()) {
                return new JSONArray(o);
            }
            if (o instanceof Map) {
                return new JSONObject((Map<?, ?>) o);
            }
            if (o instanceof Boolean ||
                o instanceof Byte ||
                o instanceof Character ||
                o instanceof Double ||
                o instanceof Float ||
                o instanceof Integer ||
                o instanceof Long ||
                o instanceof Short ||
                o instanceof String) {
                return o;
            }
            if (o.getClass().getPackage().getName().startsWith("java.")) {
                return o.toString();
            }
        } catch (final Exception ignored) {
        }
        return null;
    }
}
