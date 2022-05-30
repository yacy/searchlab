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

import org.json.JSONObject;

import eu.searchlab.Searchlab;
import eu.searchlab.aaaaa.Authentication;
import eu.searchlab.http.Service;
import eu.searchlab.storage.io.IOPath;
import eu.searchlab.tools.Logger;

// http://localhost:8400/en/api/assetget.json?path=/index/fsfe.org-2022-05-20-19-01-53-0/d003-t0520170345973-p041.index.jsonlist
public class AssetDownloadService extends AbstractService implements Service {

	private final static long kb = 1024L, mb = 1024L * kb, gb = 1024L * mb;

    @Override
    public String[] getPaths() {
        return new String[] {"/api/assetget.json"};
    }

    @Override
    public Type getType() {
        return Service.Type.BINARY;
    }

    @Override
    public byte[] serveByteArray(final JSONObject call) {

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
        final IOPath apppath = assets.append(path);

        try {
        	final byte[] b = Searchlab.io.readAll(apppath);
        	return b;
        } catch (final IOException e) {
        	Logger.warn("attempt to list " + apppath.toString(), e);
        	return new byte[] {};
        }
    }

}
