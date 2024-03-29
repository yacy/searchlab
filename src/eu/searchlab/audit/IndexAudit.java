/**
 *  IndexAudit
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
import java.util.concurrent.ConcurrentHashMap;

import eu.searchlab.operation.FrequencyTask;
import eu.searchlab.storage.io.ConcurrentIO;
import eu.searchlab.storage.io.GenericIO;
import eu.searchlab.storage.io.IOPath;
import eu.searchlab.storage.table.MinuteSeriesTable;

public class IndexAudit implements FrequencyTask {

    private final static String[] indexSizeViewColNames = new String[] {"view.index"};
    private final static String[] indexSizeMetaColNames = new String[] {};
    private final static String[] indexSizeDataColNames = new String[] {"data.docs", "data.size"};


    private final static String[] webViewColNames = new String[] {"view"};
    private final static String[] webMetaColNames = new String[] {};
    private final static String[] webDataColNames = new String[] {"data.visitors"};

    private final ConcurrentHashMap<String, ConcurrentHashMap<Long, String>> lastSeen;
    private final ConcurrentIO cio;
    private final IOPath requestsIOp, visitorsIOp;
    private MinuteSeriesTable requestsTable, visitorsTable;
    private long requestsTableModified, visitorsTableModified;

    public IndexAudit(final GenericIO io, final IOPath requestsIOp, final IOPath visitorsIOp) throws IOException {
        this.cio = new ConcurrentIO(io, 10000);
        this.requestsIOp = requestsIOp;
        this.visitorsIOp = visitorsIOp;
        this.lastSeen = new ConcurrentHashMap<>();
        this.requestsTable = new MinuteSeriesTable(indexSizeViewColNames, indexSizeMetaColNames, indexSizeDataColNames, false);
        if (io.exists(requestsIOp)) try {this.requestsTable = new MinuteSeriesTable(this.cio, requestsIOp, indexSizeViewColNames.length, indexSizeMetaColNames.length, indexSizeDataColNames.length, false);} catch (final IOException e) {}
        this.requestsTableModified = System.currentTimeMillis();
        this.visitorsTable = new MinuteSeriesTable(webViewColNames, webMetaColNames, webDataColNames, false);
        if (io.exists(visitorsIOp)) try {this.visitorsTable = new MinuteSeriesTable(this.cio, visitorsIOp, webViewColNames.length, webMetaColNames.length, webDataColNames.length, false);} catch (final IOException e) {}
        this.visitorsTableModified = System.currentTimeMillis();
    }

    public void event(final String id, final String ip) {
        ConcurrentHashMap<Long, String> u = this.lastSeen.get(id);
        if (u == null) {
            u = new ConcurrentHashMap<>();
            this.lastSeen.put(id, u);
        }
        u.put(System.currentTimeMillis(), ip);
    }

    @Override
    public void check() {

        // flush the lastSeen and update tables
        final long now = System.currentTimeMillis();
        final int sizeBeforeRequest = this.requestsTable.size();
        final int sizeBeforeVisitor = this.visitorsTable.size();
        final ConcurrentHashMap<String, ConcurrentHashMap<Long, String>> w = new ConcurrentHashMap<>();
        w.putAll(this.lastSeen);
        this.lastSeen.clear();

        // check if the tables have been updated by another process meanwhile
        try {
            final long modified = this.cio.getIO().lastModified(this.requestsIOp);
            if (modified > this.requestsTableModified) {
                this.requestsTable = new MinuteSeriesTable(this.cio, this.requestsIOp, indexSizeViewColNames.length, indexSizeMetaColNames.length, indexSizeDataColNames.length, false);
                this.requestsTableModified = System.currentTimeMillis();
            }
        } catch (final IOException e) {
        }
        try {
            final long modified = this.cio.getIO().lastModified(this.visitorsIOp);
            if (modified > this.visitorsTableModified) {
                this.visitorsTable = new MinuteSeriesTable(this.cio, this.visitorsIOp, webViewColNames.length, webMetaColNames.length, webDataColNames.length, false);
                this.visitorsTableModified = System.currentTimeMillis();
            }
        } catch (final IOException e) {
        }

        // read out copy of audit (the original one has been flushed already)
        w.forEach((id, tip) -> {
            final int count = tip.size();
            if (count > 0) {
                final String ip = tip.values().iterator().next();
                this.requestsTable.addValues(now, new String[] {id}, new String[] {ip}, new long[] {count});
            }
        });
        if (w.size() > 0) {
            this.visitorsTable.addValues(now, new String[] {}, new String[] {}, new long[] {w.size()});
        }

        // store tables
        int sizeAfter = this.requestsTable.size();
        if (sizeAfter > sizeBeforeRequest) {
            // store the table
            this.requestsTable.storeCSV(this.cio, this.requestsIOp);
            this.requestsTableModified = System.currentTimeMillis();
        }

        sizeAfter = this.visitorsTable.size();
        if (sizeAfter > sizeBeforeVisitor) {
            // store the table
            this.visitorsTable.storeCSV(this.cio, this.visitorsIOp);
            this.visitorsTableModified = System.currentTimeMillis();
        }

    }

}
