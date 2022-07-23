/**
 *  UsageCount
 *  Copyright 23.07.2022 by Michael Peter Christen, @orbiterlab
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

package eu.searchlab.http;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Count number of same-usage events for given remote IPs. This is used to identify i.e. distributed dictionary-based
 * attacks on services.
 */
public class UsageCount {

    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Long>> events; // key will be the occurring object (i.e. search requests) and the value the clients who operated on the same object
    private final int maxusage;
    private final long maxtime;

    public UsageCount(final int maxusage, final long maxtime) {
        this.events = new ConcurrentHashMap<>();
        this.maxusage = maxusage;
        this.maxtime = maxtime;
    }

    /**
     * approve an usage object:
     * The object is approved if it does not appear too often.
     * To store the clients who ask for approval, also the client must be submitted.
     * @param obj
     * @param client
     * @return true if the object is approved.
     */
    public final boolean approve(final String obj, final String client) {
        ConcurrentHashMap<String, Long> eclients = this.events.get(obj);
        if (eclients == null) {
            eclients = new ConcurrentHashMap<>();
            this.events.put(obj, eclients);
        }

        // shrink down the events to those that are not too old
        final Iterator<Map.Entry<String, Long>> i = eclients.entrySet().iterator();
        final long now = System.currentTimeMillis();
        while (i.hasNext()) {
            if (now - i.next().getValue() > this.maxtime) i.remove();
        }

        // check size
        if (eclients.size() >= this.maxusage) return false;
        eclients.put(client, now); // overwriting mit most recent time is correct
        return eclients.size() < this.maxusage;
    }

    /**
     * Get set of clients who wanted approval for the same object.
     * @param obj the object that has been approved for all the clients
     * @return the clients who wanted approval
     */
    public final Set<String> getClients(final String obj) {
        final ConcurrentHashMap<String, Long> eclients = this.events.get(obj);
        if (eclients == null) return new HashSet<String>();
        return eclients.keySet();
    }

}
