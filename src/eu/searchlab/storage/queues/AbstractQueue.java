/**
 *  AbstractQueue
 *  Copyright 03.01.2018 by Michael Peter Christen, @orbiterlab
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

package eu.searchlab.storage.queues;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractQueue implements Queue {

    @Override
    public void purge() throws IOException {
        long count = available();
        while (count-- > 0) {if  (receive(100, true) == null) break;}
    }

    @Override
    public byte[] peek() throws IOException {
        this.recover();
        final MessageContainer m = this.receive(-1, false);
        if (m == null) return null;
        this.recover();
        return m.getPayload();
    }


    @Override
    public List<byte[]> peek(int count) throws IOException {
        final List<byte[]> p = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            final MessageContainer m = this.receive(-1, false);
            if (m == null) {
                this.recover();
                return p;
            }
        }
        this.recover();
        return p;
    }
}
