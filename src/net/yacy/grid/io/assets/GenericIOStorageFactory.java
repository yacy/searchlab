/**
 *  GenericIOStorageFactory
 *  Copyright 28.1.2017 by Michael Peter Christen, @orbiterlab
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

package net.yacy.grid.io.assets;

import java.io.IOException;

public class GenericIOStorageFactory implements StorageFactory<byte[]> {

    @Override
    public String getSystem() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getHost() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean hasDefaultPort() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public int getPort() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public String getConnectionURL() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Storage<byte[]> getStorage() throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void close() {
        // TODO Auto-generated method stub

    }

}
