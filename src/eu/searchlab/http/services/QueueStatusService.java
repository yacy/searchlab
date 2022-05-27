/**
 *  QueueStatusService
 *  Copyright 26.05.2022 by Michael Peter Christen, @orbiterlab
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

import org.json.JSONException;
import org.json.JSONObject;

import eu.searchlab.Searchlab;
import eu.searchlab.http.Service;
import eu.searchlab.storage.queues.Queue;
import eu.searchlab.tools.Logger;

/**
 * http://localhost:8400/en/api/queues.json
 */
public class QueueStatusService extends AbstractService implements Service {


    private final static String[] queuesCrawler = new String[]{
            "webcrawler_00", "webcrawler_01", "webcrawler_02", "webcrawler_03",
            "webcrawler_04", "webcrawler_05", "webcrawler_06", "webcrawler_07"
    };
    private final static String[] queuesLoader = new String[]{
            "webloader_00", "webloader_01", "webloader_02", "webloader_03",
            "webloader_04", "webloader_05", "webloader_06", "webloader_07",
            "webloader_08", "webloader_09", "webloader_10", "webloader_11",
            "webloader_12", "webloader_13", "webloader_14", "webloader_15",
            "webloader_16", "webloader_17", "webloader_18", "webloader_19",
            "webloader_20", "webloader_21", "webloader_22", "webloader_23",
            "webloader_24", "webloader_25", "webloader_26", "webloader_27",
            "webloader_28", "webloader_29", "webloader_30", "webloader_31"
    };
    private final static String[] queuesParser = new String[]{
            "yacyparser_00"
    };
    private final static String[] queuesIndexer = new String[]{
            "elasticsearch_00"
    };

    private static JSONObject getStatus(final String serviceName, final String[] queueNames) throws IOException {
        final JSONObject json = new JSONObject(true);
        for (final String queueName: queueNames) {
            final String queueFullName = serviceName + "_" + queueName;
            try {
                final Queue queue = Searchlab.queues.getQueue(queueFullName);
                final long available = queue.available();
                json.put(queueFullName, available);
            } catch (final JSONException e) {
                Logger.error(e);
            }
        }
        return json;
    }

     @Override
     public String[] getPaths() {
         return new String[] {"/api/queues.json"};
     }

     @Override
     public JSONObject serveObject(final JSONObject post) throws IOException {
         final JSONObject json = new JSONObject(true);
         try {
             json.put("crawler", getStatus("crawler", queuesCrawler));
             json.put("loader", getStatus("loader", queuesLoader));
             json.put("parser", getStatus("parser", queuesParser));
             json.put("indexer", getStatus("indexer", queuesIndexer));
         } catch (final JSONException e) {
             throw new IOException(e.getMessage());
         }
         return json;
     }

}
