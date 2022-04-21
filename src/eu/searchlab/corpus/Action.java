/**
 *  Action
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

import org.json.JSONException;
import org.json.JSONObject;

import eu.searchlab.tools.JSONList;

/**
 * An action is an application on the information deduced during inferences on mind states
 * as they are represented in an argument. If we want to produce respond sentences or if
 * we want to visualize deduces data as a graph or in a picture, thats an action.
 */
public class Action {

    private static enum RenderType {loader, parser, indexer;}

    private final JSONObject json;
    /**
     * initialize an action using a json description.
     * @param json
     */
    public Action(final JSONObject json) {
        this.json = json;
    }

    /**
     * An action is backed with a JSON data structure. That can be retrieved here.
     * @return the json structure of the action
     */
    JSONObject toJSONClone() {
        final JSONObject j = new JSONObject(true);
        this.json.keySet().forEach(key -> {
            try {
                j.put(key, this.json.get(key));
            } catch (final JSONException e) {
            }
        }); // make a clone
        if (j.has("expression")) {
            j.remove("phrases");
            j.remove("select");
        }
        return j;
    }

    public Action setJSONListAsset(final String name, final JSONList list) throws JSONException {
        JSONObject assets;
        if (this.json.has("assets")) assets = this.json.getJSONObject("assets"); else {
            assets = new JSONObject();
            this.json.put("assets", assets);
        }
        assets.put(name, list.toArray());
        return this;
    }

    /**
     * toString
     * @return return the json representation of the object as a string
     */
    @Override
    public String toString() {
        return toJSONClone().toString();
    }
}
