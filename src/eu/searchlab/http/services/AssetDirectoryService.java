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
import java.util.List;

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

// http://localhost:8400/en/api/assetdir.json?q=ne
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
        final String userId = call.optString("USER", Authentication.ANONYMOUS_ID);
        if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        while (path.length() > 3 && path.endsWith("/..")) {
        	path = path.substring(0, path.length() - 3);
        	int p = path.lastIndexOf('/');
        	path = p < 0 ? "" : path.substring(0, p);
        }
        IOPath assets = Searchlab.accounting.getAssetPathForUser(userId);
        IOPath dirpath = assets.append(path);
        final JSONArray json = new JSONArray();
        try {
			List<IOMeta> dir = Searchlab.io.list(dirpath);
			for (IOMeta meta: dir) {
				JSONObject d = new JSONObject(true);
				IOPath o = meta.getIOPath();
				long size = meta.getSize();
				String sizes = size > gb ? (size / mb) + " MB" : size > mb ? (size / kb) + " KB" : size + " bytes";
				d.append("name", o.name());
				d.append("size", size);
				d.append("sizes", sizes);
				d.append("isdir", o.isFolder());
				d.append("date", DateParser.iso8601Format.format(new Date(meta.getLastModified())));
				d.append("time", meta.getLastModified());
				json.put(d);
			}
		} catch (IOException | JSONException e) {
			Logger.warn("attempt to list " + dirpath.toString(), e);
		}
        
        return json;
    }

}
