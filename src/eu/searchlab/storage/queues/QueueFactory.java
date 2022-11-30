/**
 *  QueueFactory
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

/**
 * A factory for a queue.
 */
public interface QueueFactory {

    /**
     * Get the connection to a queue. The connection is either established initially
     * or created as a new connection. If the queue did not exist, it will exist automatically
     * after calling the method
     * @param queueName name of the queue
     * @return the Queue
     * @throws IOException
     */
    public Queue getQueue(String queueName) throws IOException;

    /**
     * Get all available queues of that QueueFactory with the number of available messages
     * @return a map of all queue names with the QueueStats for the queue
     * @throws IOException
     */
    public Map<String, QueueStats> getAllQueues() throws IOException;

    /**
     * Get all queues which had been opened with getQueue
     * @return a map of all existing Queue objects, mapped by name
     * @throws IOException
     */
    public Map<String, Queue> getOpenQueues() throws IOException;

    /**
     * Get statistics for all queues:
     * - the total number of messages in all queues,
     * - the total number of messages that are ready and
     * - the total number of messages that are unacknowledged.
     * @return one QueueStats object for the aggregation of all queues
     * @throws IOException
     */
    public QueueStats getAggregatedStats() throws IOException;

    /**
     * Close the Factory
     */
    public void close();

}
