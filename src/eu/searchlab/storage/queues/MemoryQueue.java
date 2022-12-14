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
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;


public class MemoryQueue extends AbstractQueue implements Queue {

    private AtomicLong acnt = new AtomicLong(0);
    private final LinkedBlockingDeque<byte[]> queue;
    private final ConcurrentHashMap<Long, byte[]> ackc;
    boolean isClosed;

    public MemoryQueue() {
        this.queue = new LinkedBlockingDeque<>();
        this.ackc = new ConcurrentHashMap<>();
        this.isClosed = false;
    }

    @Override
    public void checkConnection() throws IOException {
        // not required, do nothing
    }

    @Override
    public void send(byte[] message) throws IOException {
        this.queue.add(message);
    }

    private long getAckHandle() {
        return System.currentTimeMillis() * 1000 + acnt.incrementAndGet();
    }

    @Override
    public MessageContainer receive(long timeout, boolean autoAck) throws IOException {
        final long deliveryTag = getAckHandle();
        if (timeout <= 0) {
            try {
                final byte[] b = this.queue.takeFirst();
                if (!autoAck) {
                    this.ackc.put(deliveryTag, b);
                }
                return new MessageContainer(b, deliveryTag);
            } catch (InterruptedException e) {
                throw new IOException(e.getMessage());
            }
        } else {
            try {
                final byte[] b = this.queue.pollFirst(timeout, TimeUnit.MILLISECONDS);
                if (b == null) throw new IOException("no new object in queue");
                this.ackc.put(deliveryTag,  b);
                return new MessageContainer(b, deliveryTag);
            } catch (InterruptedException e) {
                throw new IOException(e.getMessage());
            }
        }
    }

    @Override
    public void acknowledge(long deliveryTag) throws IOException {
        final byte[] b = this.ackc.remove(deliveryTag);
        if (b == null) throw new IOException("tag " + deliveryTag + " cannot be acknowledged, it is unknown.");
    }

    @Override
    public void reject(long deliveryTag) throws IOException {
        final byte[] b = this.ackc.remove(deliveryTag);
        if (b == null) throw new IOException("tag " + deliveryTag + " cannot be rejected, it is unknown.");
        this.queue.add(b);
    }

    @Override
    public void recover() throws IOException {
        final ConcurrentHashMap<Long, byte[]> a = new ConcurrentHashMap<>();
        a.putAll(this.ackc);
        for (final Entry<Long, byte[]> entry: a.entrySet()) {
            this.ackc.remove(entry.getKey());
            this.queue.add(entry.getValue());
        }
    }

    @Override
    public long available() throws IOException {
        return this.queue.size();
    }

    public long unacknowledged() throws IOException {
        return this.ackc.size();
    }

    public long total() throws IOException {
        return unacknowledged() + available();
    }

    @Override
    public void purge() throws IOException {
        this.queue.clear();
        this.ackc.clear();
    }

    @Override
    public void delete() throws IOException {
        this.purge();
    }

    @Override
    public void close() throws IOException {
        this.purge();
        this.isClosed = true;
    }

    @Override
    public boolean isClosed() throws IOException {
        return this.isClosed;
    }
}
