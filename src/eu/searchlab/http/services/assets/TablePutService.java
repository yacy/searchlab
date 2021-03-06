/**
 *  TablePutService
 *  Copyright 12.10.2021 by Michael Peter Christen, @orbiterlab
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

import org.json.JSONArray;

import eu.searchlab.http.AbstractService;
import eu.searchlab.http.Service;
import eu.searchlab.http.ServiceRequest;
import eu.searchlab.http.ServiceResponse;

public class TablePutService extends AbstractService implements Service {

    @Override
    public String[] getPaths() {
        return new String[] {"/api/get/test.json", "/api/get/test.csv", "/api/get/test.table"};
    }

    @Override
    public ServiceResponse serve(final ServiceRequest request) {
        final JSONArray array = new JSONArray();
        return new ServiceResponse(array);
    }
}
