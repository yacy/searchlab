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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import eu.searchlab.storage.io.IOPath;
import eu.searchlab.storage.table.IndexedTable;


public class ServiceMap {

    private static List<Service> services = new ArrayList<>();

    public static void register(final Service service) {
        services.add(service);
    }

    public static Service getService(String path) {
        path = IOPath.normalizePath(path);
        Service service = null;
        for (int i = 0; i < services.size(); i++) {
            if (services.get(i).supportsPath(path)) {
                service = services.get(i);
                break;
            }
        }
        return service;
    }

    /**
     * Handle services defined for given paths: generate html.
     * This method combines existing html with defined services. In most cases the existence of html and services
     * is exclusive, but in case of handlebars templates both, the path-oriented service and html can exist.
     * Depending on the path extension, the method may generate html..
     * - ".json": from JSON (either Object or Array)
     * - ".table": from a table-generator, which again requires a JSON/Array delivering service
     * - ".tablei": like for ".table" but without table-rendering framework (css,js) to be used within iframes
     * - ".graph": a time-series graph generator, which requires a
     * - ".csv": a different style of table generator which outputs plain text csv
     * - all other extensions: from plain text, which must be service-pre-rendered html
     * @param path request path in URI
     * @param request post requests, i.e. query attributes
     * @return a service response or NULL if no service is defined
     * @throws IOException
     */
    public static byte[] serviceDispatcher(final Service service, final String path, final ServiceRequest request) throws IOException {

        if (service == null) {
            return null; // not a fail, just a signal that no service is defined
        }

        // its possible that the given html is null in case that we don't want to
        // use a template and instead we want to return the JSON only.
        String tablename = null;
        int p = path.indexOf("/get/");
        if (p > 0) {
            tablename = path.substring(p + 5);
            p = tablename.indexOf('.');
            if (p > 0) tablename = tablename.substring(0, p); else tablename = null;
        }
        final ServiceResponse serviceResponse = service.serve(request);
        if (serviceResponse.getType() == Service.Type.OBJECT) {
            final JSONObject json = serviceResponse.getObject();
            if (json == null) return null;

            if (path.endsWith(".json")) {
                final String callback = request.get("callback", ""); //  used like "callback=?", which encapsulates then json into <callback> "([" <json> "]);"
                final boolean minified = request.get("minified", false);
                String jsons = "";
                try {
                    jsons = minified ? json.toString() : json.toString(2);
                } catch (final JSONException e) {
                    throw new IOException(e.getMessage());
                }
                return callback.length() > 0 ?
                        (callback + "([" + jsons + "]);").getBytes(StandardCharsets.UTF_8) :
                            jsons.getBytes(StandardCharsets.UTF_8);
            }
            if (path.endsWith(".table")) {
                try {
                    return new TableGenerator(tablename, json).getTable().getBytes(StandardCharsets.UTF_8);
                } catch (final JSONException e) {
                    throw new IOException(e.getMessage());
                }
            }
            if (path.endsWith(".tablei")) {
                try {
                    return new TableGenerator(tablename, json).getTableI().getBytes(StandardCharsets.UTF_8);
                } catch (final JSONException e) {
                    throw new IOException(e.getMessage());
                }
            }
            new IOException("extension not appropriate for JSONObject");
        } else if (serviceResponse.getType() == Service.Type.ARRAY) {
            final JSONArray array = serviceResponse.getArray();
            if (array == null) return null;

            if (path.endsWith(".json")) {
                try {
                    return array.toString(2).getBytes(StandardCharsets.UTF_8);
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
                            for (final String k: headKeys) {
                                final Object vo = row.opt(k);
                                String vs = vo == null ? "" : vo instanceof String ? (String) vo : String.valueOf(vo);
                                if (vo instanceof Double || vo instanceof Float) vs = vs.replace('.', ','); // german decimal separator
                                sb.append(vs).append(';');
                            }
                            sb.setCharAt(sb.length() - 1, '\n');
                        }
                    }
                    return  sb.toString().getBytes(StandardCharsets.UTF_8);
                } catch (final JSONException e) {
                    throw new IOException(e.getMessage());
                }
            }
            if (path.endsWith(".table")) {
                try {
                    return new TableGenerator(tablename, array).getTable().getBytes(StandardCharsets.UTF_8);
                } catch (final JSONException e) {
                    throw new IOException(e.getMessage());
                }
            }
            if (path.endsWith(".tablei")) {
                try {
                    return new TableGenerator(tablename, array).getTableI().getBytes(StandardCharsets.UTF_8);
                } catch (final JSONException e) {
                    throw new IOException(e.getMessage());
                }
            }
            new IOException("extension not appropriate for JSONArray");
        } else if (serviceResponse.getType() == Service.Type.TABLE) {
            final IndexedTable table = serviceResponse.getTable();
            if (table == null) return null;

            if (path.endsWith(".table")) {
                try {
                    return new TableGenerator(path, table.toJSON(true)).getTable().getBytes(StandardCharsets.UTF_8);
                } catch (final JSONException e) {
                    throw new IOException(e.getMessage());
                }
            }
            if (path.endsWith(".tablei")) {
                try {
                    return new TableGenerator(path, table.toJSON(true)).getTableI().getBytes(StandardCharsets.UTF_8);
                } catch (final JSONException e) {
                    throw new IOException(e.getMessage());
                }
            }
            new IOException("extension not appropriate for JSONArray");
        } else if (serviceResponse.getType() == Service.Type.STRING) {
            return serviceResponse.getString().getBytes(StandardCharsets.UTF_8);
        } else if (serviceResponse.getType() == Service.Type.BINARY) {
            return serviceResponse.getByteArray();
        }

        // this should never happen, we checked all service types
        throw new IOException("unknown service type " + serviceResponse.getType().toString());
    }
}
