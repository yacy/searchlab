/**
 *  ServiceMap
 *  Copyright 06.10.2021 by Michael Peter Christen, @orbiterlab
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.github.jknack.handlebars.Context;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;

import eu.searchlab.http.services.AbstractService;


public class ServiceMap {

    private static List<Service> services = new ArrayList<>();

    public static void register(Service service) {
        services.add(service);
    }


    public static String propose(String path, String html, JSONObject post) throws IOException {

        path = AbstractService.normalizePath(path);
        Service service = null;
        for (int i = 0; i < services.size(); i++) {
            if (services.get(i).supportsPath(path)) {
                service = services.get(i);
                break;
            }
        }

        if (service == null) {
            return html;
        }

        if (html == null) {
            // its possible that the given html is null in case that we don't want to
            // use a template and instead we want to return the JSON only.
            if (service.getType() == Service.Type.OBJECT) {
                final JSONObject json = service.serveObject(post);
                if (path.endsWith(".json")) {
                    try {
                        return json.toString(2);
                    } catch (final JSONException e) {
                        throw new IOException(e.getMessage());
                    }
                }
                if (path.endsWith(".table")) {
                    try {
                        return new TableGenerator(json).getTable();
                    } catch (JSONException e) {
                        throw new IOException(e.getMessage());
                    }
                }
                if (path.endsWith(".tablei")) {
                    try {
                        return new TableGenerator(json).getTableI();
                    } catch (JSONException e) {
                        throw new IOException(e.getMessage());
                    }
                }
                new IOException("extension not appropriate for JSONObject");
            } else {
                final JSONArray array = service.serveArray(post);
                if (path.endsWith(".json")) {
                    try {
                        return array.toString(2);
                    } catch (final JSONException e) {
                        throw new IOException(e.getMessage());
                    }
                }
                if (path.endsWith(".csv")) {
                    // write a csv file
                    try {
                        final List<String> headKeys = new ArrayList<>();
                        final StringBuilder sb = new StringBuilder();
                        // there are two types of array representations
                        // - either as array of arrays where the first array has the column names
                        // - or as array of objects where each array entry has objects with same keys
                        final Object head = array.get(0);
                        if (head instanceof JSONArray) {
                            // array of arrays
                            for (int i = 0; i < ((JSONArray) head).length(); i++) headKeys.add(((JSONArray) head).getString(i));
                            for (final String k: headKeys) sb.append(k).append(';');
                            sb.setCharAt(sb.length() - 1, '\n');
                            for (int i = 1; i < array.length(); i++) {
                                final JSONArray row = array.getJSONArray(i);
                                for (int j = 0; j < headKeys.size(); j++) sb.append(row.getString(j)).append(';');
                                sb.setCharAt(sb.length() - 1, '\n');
                            }
                        } else {
                            // array of objects
                            headKeys.addAll(((JSONObject) head).keySet()); // this MUST be put into an List to ensure that the order is consistent in all lines
                            for (final String k: headKeys) sb.append(k).append(';');
                            sb.setCharAt(sb.length() - 1, '\n');
                            for (int i = 0; i < array.length(); i++) {
                                final JSONObject row = array.getJSONObject(i);
                                for (final String k: headKeys) sb.append(row.optString(k, "")).append(';');
                                sb.setCharAt(sb.length() - 1, '\n');
                            }
                        }
                        return sb.toString();
                    } catch (final JSONException e) {
                        throw new IOException(e.getMessage());
                    }
                }
                if (path.endsWith(".table")) {
                    try {
                        return new TableGenerator(array).getTable();
                    } catch (JSONException e) {
                        throw new IOException(e.getMessage());
                    }
                }
                if (path.endsWith(".tablei")) {
                    try {
                        return new TableGenerator(array).getTableI();
                    } catch (JSONException e) {
                        throw new IOException(e.getMessage());
                    }
                }
                new IOException("extension not appropriate for JSONArray");
            }
        }

        // we have an html with handlebars templates
        assert service.getType() == Service.Type.OBJECT;
        final JSONObject json = service.serveObject(post);
        final Handlebars handlebars = new Handlebars();
        final Context context = Context
                .newBuilder(json)
                .resolver(JSONObjectValueResolver.INSTANCE)
                .build();
        final Template template = handlebars.compileInline(html);
        html = template.apply(context);
        return html;
    }
}
