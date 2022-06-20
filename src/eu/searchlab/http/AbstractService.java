/**
 *  AbstractService
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

import eu.searchlab.storage.io.IOPath;

public abstract class AbstractService implements Service {

    @Override
    public String[] getPaths() {
        return new String[] {};
    }

    @Override
    public boolean supportsPath(String path) {
        path = IOPath.normalizePath(path);
        final String[] paths = this.getPaths();
        for (final String p: paths) if (IOPath.normalizePath(p).equals(path)) return true;
        return false;
    }

}
