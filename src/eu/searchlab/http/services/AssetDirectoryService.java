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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import eu.searchlab.Searchlab;
import eu.searchlab.aaaaa.Authentication;
import eu.searchlab.http.Service;
import eu.searchlab.storage.io.IOMeta;
import eu.searchlab.storage.io.IOPath;
import eu.searchlab.tools.DateParser;
import eu.searchlab.tools.Logger;

// http://localhost:8400/en/api/assetdir.json?path=/
public class AssetDirectoryService extends AbstractService implements Service {

	private final static long kb = 1024L, mb = 1024L * kb, gb = 1024L * mb;

    @Override
    public String[] getPaths() {
        return new String[] {"/api/assetdir.json"};
    }

    @Override
    public Type getType() {
        return Service.Type.ARRAY;
    }

    @Override
    public JSONArray serveArray(final JSONObject call) {

        // evaluate request parameter
        String path = call.optString("path", "/");
        if (!path.startsWith("/")) path = "/" + path;
        if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        final String user_id = call.optString("USER", Authentication.ANONYMOUS_ID);
        int p;
        while (path.length() > 3 && (p = path.indexOf("/..")) >= 0) {
        	final String h = path.substring(0, p);
        	final int q = h.lastIndexOf('/');
        	path = q < 0 ? "" : h.substring(0, q) + path.substring(p + 3);
        }
        final IOPath assets = Searchlab.accounting.getAssetPathForUser(user_id);
        final String assetsPath = assets.getPath();
        final IOPath dirpath = assets.append(path);
        final JSONArray json = new JSONArray();
        final Set<String> knownDir = new HashSet<>();
        try {
			final List<IOMeta> dir = Searchlab.io.list(dirpath);
			for (final IOMeta meta: dir) {
				final JSONObject d = new JSONObject(true);
				final IOPath o = meta.getIOPath();
				final String fullpath = o.getPath();
				String subpath = fullpath.substring(assetsPath.length());
				// this is the 'full' path 'behind' assetsPath. We must also subtract the path where we are navigating to
				assert subpath.startsWith(path);
				subpath = subpath.substring(path.length());
				// find first element in that path
				p = subpath.indexOf('/', 1);
				String name = null;
				boolean isDir = false;
				if (p < 0) {
					name = subpath.substring(1);
				} else {
					name = subpath.substring(1, p);
					isDir = true;
					if (knownDir.contains(name)) continue;
					knownDir.add(name);
				}
				d.put("name", name);
				d.put("isdir", isDir);
				if (!isDir) {
					final long size = meta.getSize();
					final String sizes = size > gb ? (size / mb) + " MB" : size > mb ? (size / kb) + " KB" : size + " bytes";
					d.put("size", size);
					d.put("sizes", sizes);
					d.put("date", DateParser.iso8601Format.format(new Date(meta.getLastModified())));
					d.put("time", meta.getLastModified());
				} else {
					d.put("size", 0);
					d.put("sizes", "");
					d.put("date", "");
					d.put("time", 0);
				}
				json.put(d);
			}
		} catch (IOException | JSONException e) {
			Logger.warn("attempt to list " + dirpath.toString(), e);
		}

        return json;
    }

}
