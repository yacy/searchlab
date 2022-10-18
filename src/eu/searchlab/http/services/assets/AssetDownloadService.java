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

package eu.searchlab.http.services.assets;


import java.io.IOException;

import eu.searchlab.Searchlab;
import eu.searchlab.http.AbstractService;
import eu.searchlab.http.Service;
import eu.searchlab.http.ServiceRequest;
import eu.searchlab.http.ServiceResponse;
import eu.searchlab.storage.io.IOPath;
import eu.searchlab.tools.Logger;
import io.undertow.util.Headers;

// http://localhost:8400/en/api/assetget.json?path=/index/fsfe.org-2022-05-20-19-01-53-0/d003-t0520170345973-p041.index.jsonlist
public class AssetDownloadService extends AbstractService implements Service {

    private final static long kb = 1024L, mb = 1024L * kb, gb = 1024L * mb;

    @Override
    public String[] getPaths() {
        return new String[] {"/api/assetget.json"};
    }

    @Override
    public ServiceResponse serve(final ServiceRequest serviceRequest) {

        // evaluate request parameter
        String path = IOPath.normalizePath(serviceRequest.get("path", ""));
        if (path.length() == 1 && path.equals("/")) path = "";

        final int p = path.lastIndexOf('.');
        final String ext = p < 0 ? "html" : path.substring(p + 1);
        final int q = path.lastIndexOf('/');
        final String filename = q < 0 ? path : path.substring(q + 1);

        final String user_id = serviceRequest.getUser();
        final IOPath assets = Searchlab.accounting.getAssetsPathForUser(user_id);
        final IOPath apppath = assets.append(path);

        byte[] b;
        try {
            b = Searchlab.io.readAll(apppath);
        } catch (final IOException e) {
            Logger.warn("attempt to list " + apppath.toString(), e);
            b = new byte[] {};
        }

        final ServiceResponse serviceResponse = new ServiceResponse(b);
        serviceResponse.setMime(ServiceRequest.getMime(ext));
        serviceResponse.setSpecial(200, Headers.CONTENT_DISPOSITION.toString(), "attachment; filename=\"" + filename + "\"");
        return serviceResponse;
    }

}
