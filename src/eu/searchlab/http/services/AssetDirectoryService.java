/**
 *  AssetDirectoryService
 *  Copyright 21.05.2022 by Michael Peter Christen, @orbiterlab
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


import java.io.IOException;
import java.util.Date;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import eu.searchlab.Searchlab;
import eu.searchlab.aaaaa.Authentication;
import eu.searchlab.http.Service;
import eu.searchlab.http.ServiceResponse;
import eu.searchlab.storage.io.IODirList;
import eu.searchlab.storage.io.IOPath;
import eu.searchlab.tools.DateParser;
import eu.searchlab.tools.Logger;

// http://localhost:8400/en/api/assetdir.json?path=/
public class AssetDirectoryService extends AbstractService implements Service {

    private final static long kb = 1024L, mb = 1024L * kb, gb = 1024L * mb;

    @Override
    public String[] getPaths() {
        return new String[] {"/api/assetdir.json", "/data_warehouse/assets/"};
    }

    @Override
    public ServiceResponse serve(final JSONObject call) {

        // evaluate request parameter
        String path = IOPath.normalizePath(call.optString("path", ""));
        if (path.length() == 1 && path.equals("/")) path = "";
        final String user_id = call.optString("USER", Authentication.ANONYMOUS_ID);
        final IOPath assets = Searchlab.accounting.getAssetsPathForUser(user_id);
        //final String assetsPath = assets.getPath();
        final IOPath dirpath = assets.append(path);
        final JSONArray dirarray = new JSONArray();
        //final Set<String> knownDir = new HashSet<>();
        try {
            //System.out.println(list.toString());
            if (path.length() > 1) {
                final JSONObject d = new JSONObject(true);
                d.put("name", "../");
                d.put("isdir", true);
                d.put("size", 0);
                d.put("size_p", "");
                d.put("date", "");
                d.put("time", 0);
                dirarray.put(d);
            }
            int max_name_len = 0;
            int max_size_len = 0;
            final IODirList dirList = Searchlab.io.dirList(dirpath);
            for (final IODirList.Entry entry: dirList) {
                final JSONObject d = new JSONObject(true);
                d.put("name", entry.name);
                max_name_len = Math.max(max_name_len, entry.name.length());
                d.put("isdir", entry.isDir);
                if (entry.isDir) {
                    d.put("size", 0);
                    d.put("size_p", "");
                    d.put("date", "");
                    d.put("time", 0);
                } else {
                    final String sizes = entry.size > gb ? (entry.size / mb) + " MB   " : entry.size > mb ? (entry.size / kb) + " KB   " : entry.size + " bytes";
                    d.put("size", entry.size);
                    d.put("size_p", sizes);
                    max_size_len = Math.max(max_size_len, sizes.length());
                    d.put("date", DateParser.iso8601Format.format(new Date(entry.time)));
                    d.put("time", entry.time);
                }
                dirarray.put(d);
            }

            // now go through the list and complete formatting strings;
            for (int i = 0; i < dirarray.length(); i++) {
                final JSONObject j = dirarray.getJSONObject(i);
                j.put("size_p", String.format("%1$" + (max_size_len - j.getString("size_p").length() + 1) + "s", " ") + j.getString("size_p"));
                j.put("name_p", j.getString("name") + String.format("%1$" + (max_name_len - j.getString("name").length() + 1) + "s", " "));
            }
        } catch (IOException | JSONException e) {
            Logger.warn("attempt to list " + dirpath.toString(), e);
        }
        final JSONObject json = new JSONObject(true);
        try {
            json.put("path", path);
            json.put("user_id", user_id);
            json.put("dir", dirarray);
        } catch (final JSONException e) {
            Logger.warn(e);
        }
        return new ServiceResponse(json);
    }

}
