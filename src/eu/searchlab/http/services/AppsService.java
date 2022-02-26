/**
 *  AppsService
 *  Copyright 26.02.2022 by Michael Peter Christen, @orbiterlab
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

package eu.searchlab.http.services;

import java.io.File;
import java.io.IOException;
import java.util.TreeMap;

import org.json.JSONArray;
import org.json.JSONObject;

import eu.searchlab.http.Service;
import eu.searchlab.http.WebServer;
import eu.searchlab.storage.json.AbstractTray;

// http://localhost:8400/en/api/apps.json
public class AppsService extends AbstractService implements Service {


    @Override
    public String[] getPaths() {
        return new String[] {"/api/apps.json"};
    }

    @Override
    public Type getType() {
        return Service.Type.ARRAY;
    }

    @Override
    public JSONArray serveArray(final JSONObject call) {
        final File app_path = new File(WebServer.APPS_PATH, "app");
        final String[] app_list = app_path.list();
        final TreeMap<String, JSONObject> sortlist = new TreeMap<>();
        for (final String appp: app_list) {
            final File appf = new File(new File(app_path, appp), "app.json");
            if (!appf.exists()) continue;
            try {
                final JSONObject appj = AbstractTray.read(appf);
                sortlist.put(appj.optString("name", "") + "|" + appj.optString("headline", ""), appj);
            } catch (final IOException e) {}
        }

        final JSONArray json = new JSONArray();
        for (final JSONObject j: sortlist.values()) json.put(j);
        return json;
    }

}
