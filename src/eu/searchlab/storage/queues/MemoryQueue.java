/**
 *  MemoryQueue
 *  Copyright 30.11.2022 by Michael Peter Christen, @orbiterlab
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class MemoryQueue extends AbstractQueue implements Queue {

    private final ConcurrentLinkedQueue<byte[]> queue;
    private final ConcurrentHashMap<Long, byte[]> ackc;

    public MemoryQueue() {
        this.queue = new ConcurrentLinkedQueue<>();
        this.ackc = new ConcurrentHashMap<>();
    }

    @Override
    public void checkConnection() throws IOException {
    }

    @Override
    public void send(byte[] message) throws IOException {
        this.queue.add(message);
    }

    @Override
    public MessageContainer receive(long timeout, boolean autoAck) throws IOException {
        final long t = System.currentTimeMillis();
        final byte[] b = this.queue.poll();
        if (b != null) {
            this.ackc.put(t,  b);
            return new MessageContainer(b, t);
        }
        return null;
    }

    @Override
    public void acknowledge(long deliveryTag) throws IOException {
        // TODO Auto-generated method stub

    }

    @Override
    public void reject(long deliveryTag) throws IOException {
        // TODO Auto-generated method stub

    }

    @Override
    public void recover() throws IOException {
        // TODO Auto-generated method stub

    }

    @Override
    public long available() throws IOException {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public void purge() throws IOException {
        // TODO Auto-generated method stub

    }

    @Override
    public void delete() throws IOException {
        // TODO Auto-generated method stub

    }

    @Override
    public void close() throws IOException {
        // TODO Auto-generated method stub

    }

}
