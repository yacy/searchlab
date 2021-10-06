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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.github.jknack.handlebars.Context;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;


public class ServiceMap {

    private static Map<String, Service> map = new ConcurrentHashMap<>();

    public static void register(Service service) {
        for (final String path: service.getPaths()) {
            map.put(normalizePath(path), service);
        }
    }

    private static String normalizePath(String path) {
        path = path.trim();
        if (path.charAt(0) == '/') path = path.substring(1);
        return path;
    }

    public static String propose(String path, String html, JSONObject post) throws IOException {

        final Service service = map.get(normalizePath(path));

        if (service == null) {
            return html;
        }

        if (html == null) {
            // its possible that the given html is null in case that we don't want to
            // use a template and instead we want to return the JSON only.
            if (service.getType() == Service.Type.OBJECT) {
                final JSONObject json = service.serveObject(post);
                try {
                    return json.toString(2);
                } catch (final JSONException e) {
                    throw new IOException(e.getMessage());
                }
            } else {
                final JSONArray json = service.serveArray(post);
                if (path.endsWith(".json")) {
                    try {
                        return json.toString(2);
                    } catch (final JSONException e) {
                        throw new IOException(e.getMessage());
                    }
                }
                if (path.endsWith(".csv")) {
                    // write a csv file
                    try {
                        final JSONObject head = json.getJSONObject(0);
                        final List<String> headKeys = new ArrayList<>();
                        headKeys.addAll(head.keySet()); // this MUST be put into an List to ensure that the order is consistent in all lines
                        final StringBuilder sb = new StringBuilder();
                        for (final String k: headKeys) sb.append(k).append(';');
                        sb.setCharAt(sb.length() - 1, '\n');
                        for (int i = 0; i < json.length(); i++) {
                            final JSONObject row = json.getJSONObject(i);
                            for (final String k: headKeys) sb.append(row.optString(k, "")).append(';');
                            sb.setCharAt(sb.length() - 1, '\n');
                        }
                        return sb.toString();
                    } catch (final JSONException e) {
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
