/**
 *  UserAudit
 *  Copyright 28.05.2022 by Michael Peter Christen, @orbiterlab
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

package eu.searchlab.audit;

import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import eu.searchlab.Searchlab;
import eu.searchlab.operation.FrequencyTask;
import eu.searchlab.storage.io.ConcurrentIO;
import eu.searchlab.storage.io.GenericIO;
import eu.searchlab.storage.io.IOPath;
import eu.searchlab.storage.table.MinuteSeriesTable;
import eu.searchlab.storage.table.TableViewer;
import eu.searchlab.tools.Logger;

/**
 * Class which stores information about the presence and actions of users
 */
public class UserAudit implements FrequencyTask {

    private final static String[] requestsViewColNames = new String[] {"view.id"};
    private final static String[] requestsMetaColNames = new String[] {"meta.ip"};
    private final static String[] requestdDataColNames = new String[] {"data.requests"};


    private final static String[] visitorsViewColNames = new String[] {};
    private final static String[] visitorsMetaColNames = new String[] {};
    private final static String[] visitorsDataColNames = new String[] {"data.visitors"};

    private final ConcurrentHashMap<String, TreeMap<Long, String>> lastSeen;
    private final ConcurrentIO cio;
    private final IOPath requestsIOp, visitorsIOp;
    private MinuteSeriesTable requestsTable, visitorsTable, visitorsTableAggregated;
    private long requestsTableModified, visitorsTableModified;

    public UserAudit(final GenericIO io, final IOPath requestsIOp, final IOPath visitorsIOp) throws IOException {
        this.cio = new ConcurrentIO(io, 10000);
        this.requestsIOp = requestsIOp;
        this.visitorsIOp = visitorsIOp;
        this.lastSeen = new ConcurrentHashMap<>();
        this.requestsTable = new MinuteSeriesTable(requestsViewColNames, requestsMetaColNames, requestdDataColNames, false);
        Logger.info("loading " + visitorsIOp.toString());
        if (io.exists(requestsIOp)) try {this.requestsTable = new MinuteSeriesTable(this.cio, requestsIOp, false);} catch (final IOException e) {}
        this.requestsTableModified = System.currentTimeMillis();
        this.visitorsTable = new MinuteSeriesTable(visitorsViewColNames, visitorsMetaColNames, visitorsDataColNames, false);
        this.visitorsTableAggregated = new MinuteSeriesTable(visitorsViewColNames, visitorsMetaColNames, visitorsDataColNames, false);
        Logger.info("loading " + visitorsIOp.toString());
        if (io.exists(visitorsIOp)) try {this.visitorsTable = new MinuteSeriesTable(this.cio, visitorsIOp, false);} catch (final IOException e) {}
        this.visitorsTableModified = System.currentTimeMillis();
    }

    /**
     * The event method collects the request of a specific user with a specific (pseudomized) ip to a specific time
     * @param id    the user id
     * @param ip00  the pseudomized IP
     */
    public void event(final String id, final String ip00) {
        TreeMap<Long, String> u = this.lastSeen.get(id);
        if (u == null) {
            u = new TreeMap<>();
            this.lastSeen.put(id, u);
        }
        synchronized (u) {
            u.put(System.currentTimeMillis(), ip00);
        }
    }

    @Override
    public void check() {

        // flush the lastSeen and update tables & make a copy of the audit log:
        // this will prevent that the log is modified while we evaluate it
        final ConcurrentHashMap<String, TreeMap<Long, String>> audit = new ConcurrentHashMap<>();
        audit.putAll(this.lastSeen);
        this.lastSeen.clear();

        // check if the tables have been updated by another process meanwhile
        final long now = System.currentTimeMillis();
        final int sizeBeforeRequest = this.requestsTable.size();
        final int sizeBeforeVisitor = this.visitorsTable.size();
        try {
            final long modified = this.cio.getIO().lastModified(this.requestsIOp);
            if (modified > this.requestsTableModified) {
                this.requestsTable = new MinuteSeriesTable(this.cio, this.requestsIOp, false);
                this.requestsTableModified = now;
            }
        } catch (final IOException e) {
        }
        try {
            final long modified = this.cio.getIO().lastModified(this.visitorsIOp);
            if (modified > this.visitorsTableModified) {
                this.visitorsTable = new MinuteSeriesTable(this.cio, this.visitorsIOp, false);
                this.visitorsTableModified = now;
            }
        } catch (final IOException e) {
        }

        // read out copy of audit (the original one has been flushed already)
        // and write into the requests and visitor tables
        if (audit.size() > 0) {
            audit.forEach((id, events) -> {
                // events is now a time -> IP mapping.
                // for the request table we are not interested in the different IPs, just the number of requests that happened:
                final int count = events.size();
                if (count > 0) {
                    // we get out just one single ip as reference. We consider that all IPs should be the same
                    final Map.Entry<Long, String> event = events.entrySet().iterator().next();
                    final long eventTime = event.getKey(); // we could also use 'now', not sure what is best.
                    final String ip = event.getValue();
                    this.requestsTable.addValues(eventTime, new String[] {id}, new String[] {ip}, new long[] {count});
                }
            });

            // the number of visitors is just the number of entries because the visitor is identified by it's id, not the IP
            this.visitorsTable.addValues(now, new String[] {}, new String[] {}, new long[] {audit.size()});
        }
        this.requestsTable.sort();
        this.visitorsTable.sort();

        // store tables
        int sizeAfter = this.requestsTable.size();
        if (sizeAfter > sizeBeforeRequest) {
            // store the table
            this.requestsTable.storeCSV(this.cio, this.requestsIOp);
            this.requestsTableModified = now;
        }

        sizeAfter = this.visitorsTable.size();
        if (sizeAfter > sizeBeforeVisitor) {
            // store the table
            this.visitorsTable.storeCSV(this.cio, this.visitorsIOp);
            this.visitorsTableModified = now;
        }

        // make aggregation of visitorsTable into visitorsTableAggregated
        this.visitorsTableAggregated = this.visitorsTable.aggregation();

        // paint a graph
        final TableViewer requestsTableViewer = this.requestsTable.getGraph("requests_per_minute", "Requests per Minute", "Date", MinuteSeriesTable.TS_DATE, new String[] {"data.requests SteelBlue"}, new String[] {});
        Searchlab.htmlPanel.put("requests_per_minute", requestsTableViewer);
        final TableViewer visitorsTableViewer = this.visitorsTable.getGraph("visitors_per_minute", "Pseudo-Unique Visitors per Minute", "Date", MinuteSeriesTable.TS_DATE, new String[] {"data.visitors SteelBlue"}, new String[] {});
        Searchlab.htmlPanel.put("visitors_per_minute", visitorsTableViewer);
        final TableViewer visitorsTableViewerAggregated = this.visitorsTableAggregated.getGraph("visitors_per_minute_aggregated", "Pseudo-Unique Visitors per Minute (aggregated)", "Date", MinuteSeriesTable.TS_DATE, new String[] {"data.visitors SteelBlue"}, new String[] {});
        Searchlab.htmlPanel.put("visitors_per_minute_aggregated", visitorsTableViewerAggregated);
    }

}
