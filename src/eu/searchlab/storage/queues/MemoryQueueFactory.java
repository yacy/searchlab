/**
 *  MemoryQueueFactory
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
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

public class MemoryQueueFactory implements QueueFactory {

    private final ConcurrentHashMap<String, MemoryQueue> queues;

    public MemoryQueueFactory() {
        this.queues = new ConcurrentHashMap<>();
    }

    @Override
    public Queue getQueue(String queueName) throws IOException {
        MemoryQueue queue = this.queues.get(queueName);
        if (queue == null) {
            queue = new MemoryQueue();
            this.queues.put(queueName, queue);
        }
        return queue;
    }

    @Override
    public Map<String, QueueStats> getAllQueues() throws IOException {
        final Map<String, QueueStats> statsMap = new HashMap<>();
        for (final Entry<String, MemoryQueue> entry: this.queues.entrySet()) {
            final MemoryQueue queue = entry.getValue();
            final QueueStats stats = new QueueStats()
                    .setReady(queue.available())
                    .setTotal(queue.total())
                    .setUnacknowledged(queue.unacknowledged());
            statsMap.put(entry.getKey(), stats);
        }
        return statsMap;
    }

    @Override
    public Map<String, Queue> getOpenQueues() throws IOException {
        final Map<String, Queue> openMap = new HashMap<>();
        for (final Entry<String, MemoryQueue> entry: this.queues.entrySet()) {
            if (!entry.getValue().isClosed()) openMap.put(entry.getKey(), entry.getValue());
        }
        return openMap;
    }

    @Override
    public QueueStats getAggregatedStats() throws IOException {
        final Map<String, QueueStats> statsMap = getAllQueues();
        long available = 0, total = 0, unacknowledged = 0;
        for (final QueueStats stats: statsMap.values()) {
            available += stats.getReady();
            total += stats.getTotal();
            unacknowledged += stats.getUnacknowledged();
        }
        return new QueueStats().setReady(available).setTotal(total).setUnacknowledged(unacknowledged);
    }

    @Override
    public void close() throws IOException {
        for (final MemoryQueue queue: this.queues.values()) {
            if (!queue.isClosed()) queue.close();
        }
    }

}
