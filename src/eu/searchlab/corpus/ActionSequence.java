/**
 *  ActionSequence
 *  Copyright 29.06.2016 by Michael Peter Christen, @orbiterlab
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General private
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General private License for more details.
 *
 *  You should have received a copy of the GNU Lesser General private License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package eu.searchlab.corpus;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ActionSequence extends JSONObject {

    private final String metadata_name, data_name;

    /**
     * create an empty thought, to be filled with single data entities.
     */
    public ActionSequence() {
        super(true);
        this.metadata_name = "metadata";
        this.data_name = "data";
    }

    @Override
    public boolean equals(final Object o) {
        if (!(o instanceof ActionSequence)) return false;
        final ActionSequence t = (ActionSequence) o;
        try {
            return this.getData().equals(t.getData());
        } catch (final JSONException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * All details of the creation of this thought is collected in a metadata statement.
     * @return the set of meta information for this thought
     */
    private JSONObject getMetadata() throws JSONException {
        JSONObject md;
        if (this.has(this.metadata_name)) md = this.getJSONObject(this.metadata_name); else {
            md = new JSONObject();
            this.put(this.metadata_name, md);
        }
        if (!md.has("count")) md.put("count", getData().length());
        return md;
    }

    /**
     * Information contained in this thought has the form of a result set table, organized in rows and columns.
     * The columns must have all the same name in each row.
     * @param table the information for this thought.
     * @return the thought
     */
    public ActionSequence setData(final JSONArray table) throws JSONException {
        this.put(this.data_name, table);
        final JSONObject md = getMetadata();
        md.put("count", getData().length());
        return this;
    }

    /**
     * Information contained in this thought can get returned as a table, a set of information pieces.
     * @return a table of information pieces as a set of rows which all have the same column names.
     */
    private JSONArray getData() throws JSONException {
        if (this.has(this.data_name)) return this.getJSONArray(this.data_name);
        final JSONArray a = new JSONArray();
        this.put(this.data_name, a);
        return a;
    }

    public ActionSequence addAction(final Action action) throws JSONException {
        final JSONArray a = getActionsJSON();
        a.put(action.toJSONClone());
        return this;
    }

    /**
     * To be able to apply (re-)actions to this thought, the actions on the information can be retrieved.
     * @return the (re-)actions which are applicable to this thought.
     * @throws JSONException
     */
    public List<Action> getActions() throws JSONException {
        final List<Action> actions = new ArrayList<>();
        getActionsJSON().forEach(action -> actions.add(new Action((JSONObject) action)));
        return actions;
    }

    private JSONArray getActionsJSON() throws JSONException {
        JSONArray actions;
        if (!this.has("actions")) {
            actions = new JSONArray();
            this.put("actions", actions);
        } else {
            actions = this.getJSONArray("actions");
        }
        return actions;
    }

    @Override
    public String toString() {
        try {
            return super.toString(2); // thats here to get a better debugging output
        } catch (final JSONException e) {
        }
        return "";
    }

}
