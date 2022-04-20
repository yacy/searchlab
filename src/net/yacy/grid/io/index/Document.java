/**
 *  Document
 *  Copyright 6.3.2018 by Michael Peter Christen, @orbiterlab
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

package net.yacy.grid.io.index;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import eu.searchlab.tools.DateParser;

public class Document extends JSONObject {

    public Document() {
        super();
    }

    public Document(final Map<String, Object> map) {
        super(map);
    }

    public Document(final JSONObject obj) {
        super(obj.toMap());
    }

    public Document putObject(final MappingDeclaration declaration, final JSONObject o) throws JSONException {
        if (!isString(declaration)) return this;
        this.put(declaration.getMapping().name(), o);
        return this;
    }

    public JSONObject getObject(final MappingDeclaration declaration) {
        if (!isString(declaration)) return null;
        return this.optJSONObject(declaration.getMapping().name());
    }

    public Document putString(final MappingDeclaration declaration, final String s) throws JSONException {
        if (!isString(declaration)) return this;
        this.put(declaration.getMapping().name(), s);
        return this;
    }

    public String getString(final MappingDeclaration declaration, final String dflt) {
        if (!isString(declaration)) return null;
        return this.optString(declaration.getMapping().name(), dflt);
    }

    public boolean isString(final MappingDeclaration declaration) {
        final MappingType type = declaration.getMapping().getType();
        boolean valid = type == MappingType.string || type == MappingType.text_en_splitting_tight || type == MappingType.text_general;
        valid = valid && !declaration.getMapping().isMultiValued();
        return valid;
    }

    public Document putStrings(final MappingDeclaration declaration, final Collection<String> list) throws JSONException {
        if (!isStrings(declaration)) return this;
        this.put(declaration.getMapping().name(), list);
        return this;
    }

    public List<String> getStrings(final MappingDeclaration declaration) {
        if (!isStrings(declaration)) return null;
        final Object obj = this.opt(declaration.getMapping().name());
        if (obj == null) return new ArrayList<>(0);
        final boolean valid = obj instanceof JSONArray;
        if (!valid) return null;
        return ((JSONArray) obj).toList().stream().map(o -> Objects.toString(o, null)).collect(Collectors.toList());
    }

    public boolean isStrings(final MappingDeclaration declaration) {
        final MappingType type = declaration.getMapping().getType();
        boolean valid = type == MappingType.string || type == MappingType.text_en_splitting_tight || type == MappingType.text_general;
        valid = valid && declaration.getMapping().isMultiValued();
        return valid;
    }

    public Document putInt(final MappingDeclaration declaration, final int i) throws JSONException {
        if (!isInt(declaration)) return this;
        this.put(declaration.getMapping().name(), i);
        return this;
    }

    public int getInt(final MappingDeclaration declaration) {
        if (!isInt(declaration)) return 0;
        return this.optInt(declaration.getMapping().name());
    }

    private boolean isInt(final MappingDeclaration declaration) {
        final MappingType type = declaration.getMapping().getType();
        final boolean valid = type == MappingType.num_integer && !declaration.getMapping().isMultiValued();
        assert valid;
        return valid;
    }

    public Document putInts(final MappingDeclaration declaration, final Collection<Integer> ints) throws JSONException {
        if (!isInts(declaration)) return this;
        this.put(declaration.getMapping().name(), ints);
        return this;
    }

    public List<Integer> getInts(final MappingDeclaration declaration) {
        if (!isInts(declaration)) return null;
        final Object obj = this.opt(declaration.getMapping().name());
        final boolean valid = obj instanceof JSONArray;
        assert valid; if (!valid) return null;
        return ((JSONArray) obj).toList().stream().map(o -> (int) o).collect(Collectors.toList());
    }

    private boolean isInts(final MappingDeclaration declaration) {
        final MappingType type = declaration.getMapping().getType();
        final boolean valid = type == MappingType.num_integer && declaration.getMapping().isMultiValued();
        assert valid;
        return valid;
    }

    public Document putLong(final MappingDeclaration declaration, final long l) throws JSONException {
        if (!isLong(declaration)) return this;
        this.put(declaration.getMapping().name(), l);
        return this;
    }

    public long getLong(final MappingDeclaration declaration) {
        if (!isLong(declaration)) return 0;
        return this.optLong(declaration.getMapping().name());
    }

    private boolean isLong(final MappingDeclaration declaration) {
        final MappingType type = declaration.getMapping().getType();
        final boolean valid = type == MappingType.num_long && !declaration.getMapping().isMultiValued();
        assert valid;
        return valid;
    }

    public Document putlongs(final MappingDeclaration declaration, final Collection<Long> longs) throws JSONException {
        if (!isInts(declaration)) return this;
        this.put(declaration.getMapping().name(), longs);
        return this;
    }

    public List<Long> getLongs(final MappingDeclaration declaration) {
        if (!isLongs(declaration)) return null;
        final Object obj = this.opt(declaration.getMapping().name());
        final boolean valid = obj instanceof JSONArray;
        assert valid; if (!valid) return null;
        return ((JSONArray) obj).toList().stream().map(o -> (long) o).collect(Collectors.toList());
    }

    private boolean isLongs(final MappingDeclaration declaration) {
        final MappingType type = declaration.getMapping().getType();
        final boolean valid = type == MappingType.num_long && declaration.getMapping().isMultiValued();
        assert valid;
        return valid;
    }

    public Document putDate(final MappingDeclaration declaration, final Date date) throws JSONException {
        if (!isDate(declaration)) return this;
        this.put(declaration.getMapping().name(), DateParser.iso8601MillisFormat.format(date));
        return this;
    }

    public Date getDate(final MappingDeclaration declaration) throws JSONException {
       if (!isDate(declaration)) return null;
        if (!this.has(declaration.getMapping().name())) return null;
        final String date = this.getString(declaration.getMapping().name());
        return DateParser.iso8601MillisParser(date);
    }

    private boolean isDate(final MappingDeclaration declaration) {
        final MappingType type = declaration.getMapping().getType();
        final boolean valid = type == MappingType.date && !declaration.getMapping().isMultiValued();
        assert valid;
        return valid;
    }
}
