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
import java.util.Map;

public class MemoryQueueFactory implements QueueFactory {

    @Override
    public Queue getQueue(String queueName) throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Map<String, QueueStats> getAllQueues() throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Map<String, Queue> getOpenQueues() throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public QueueStats getAggregatedStats() throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void close() {
        // TODO Auto-generated method stub

    }

}
