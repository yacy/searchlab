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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

import eu.searchlab.storage.io.ConcurrentIO;
import eu.searchlab.storage.io.GenericIO;
import eu.searchlab.storage.io.IOObject;
import eu.searchlab.storage.io.IOPath;
import eu.searchlab.storage.table.TimeSeriesTable;
import eu.searchlab.tools.Logger;

/**
 * Class which stores information about the presence and actions of users
 */
public class UserAudit implements AuditTask {

    private final static String[] viewColNames = new String[] {"id"};
    private final static String[] metaColNames = new String[] {"ip"};
    private final static String[] dataColNames = new String[] {"requests"};

    private final ConcurrentHashMap<String, ConcurrentHashMap<Long, String>> lastSeen;
    private final TimeSeriesTable tsd;
    private final ConcurrentIO cio;
    private final IOPath iop;

    public UserAudit(final GenericIO io, final IOPath backupPath) {
        this.cio = new ConcurrentIO(io);
        this.iop = backupPath;
        this.lastSeen = new ConcurrentHashMap<>();
        this.tsd = new TimeSeriesTable(viewColNames, metaColNames, dataColNames);
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
        // flush the lastSeen and make a table entry
        final long now = System.currentTimeMillis();
        final int sizeBefore = this.tsd.size();
        final ConcurrentHashMap<String, ConcurrentHashMap<Long, String>> w = new ConcurrentHashMap<>();
        w.putAll(this.lastSeen);
        this.lastSeen.clear();
        w.forEach((id, tip) -> {
            final int count = tip.size();
            if (count > 0) {
                final String ip = tip.values().iterator().next();
                this.tsd.addValues(now, new String[] {id}, new String[] {ip}, new double[] {(double) count});
            }
        });
        final int sizeAfter = this.tsd.size();
        if (sizeAfter > sizeBefore) {
            // store the table
            final long start = System.currentTimeMillis();
            try {
                final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                final OutputStreamWriter osw = new OutputStreamWriter(baos, StandardCharsets.UTF_8);
                this.tsd.table.table().write().csv(osw);
                osw.close();
                this.cio.writeForced(10000, new IOObject(this.iop, baos.toByteArray()));
                final long stop = System.currentTimeMillis();
                Logger.info("wrote user audit to " + this.iop.toString() + " in " + (stop - start) + " milliseconds");
            } catch (final IOException e) {
                Logger.warn("failed to write user audit to " + this.iop.toString(), e);
            }
        }
    }
}
