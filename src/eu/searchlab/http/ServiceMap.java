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

import java.util.ArrayList;
import java.util.List;

import eu.searchlab.storage.io.IOPath;


public class ServiceMap {

    private static List<Service> services = new ArrayList<>();

    public static void register(final Service service) {
        services.add(service);
    }

    public static Service getService(String path) {
        path = IOPath.canonicalPath(path);
        Service service = null;
        for (int i = 0; i < services.size(); i++) {
            if (services.get(i).supportsPath(path)) {
                service = services.get(i);
                break;
            }
        }
        return service;
    }

}
