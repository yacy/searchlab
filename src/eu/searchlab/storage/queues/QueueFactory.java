/**
 *  QueueConnector
 *  Copyright 14.01.2017 by Michael Peter Christen, @orbiterlab
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
     * Close the Factory
     */
    public void close();

}
