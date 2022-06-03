/**
 *  LogService
 *  Copyright 27.05.2022 by Michael Peter Christen, @orbiterlab
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
import java.util.List;

import org.json.JSONObject;

import eu.searchlab.http.Service;
import eu.searchlab.http.ServiceResponse;
import eu.searchlab.tools.Logger;

// http://localhost:8400/en/api/log.txt?lines=100
public class LogService extends AbstractService implements Service {

    @Override
    public String[] getPaths() {
        return new String[] {"/api/log.txt"};
    }

    @Override
    public ServiceResponse serve(final JSONObject post) throws IOException {
        final int tail = post.optInt("tail", post.optInt("count", post.optInt("lines", 0)));
        final StringBuilder buffer = new StringBuilder(1000);
        final List<String> lines = Logger.getLines(tail);
        for (final String line: lines) buffer.append(line); // line has line break attached
        return new ServiceResponse(buffer.toString());
    }

}
