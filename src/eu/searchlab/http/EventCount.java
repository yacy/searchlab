/**
 *  EventCount
 *  Copyright 21.07.2022 by Michael Peter Christen, @orbiterlab
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

import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * Timeline structure to store sequences of events with the purpose to measure the number of events
 * that happened in a specific timespan in the past.
 */
public class EventCount {

    private final Random random = new Random(System.currentTimeMillis());
    private final ConcurrentHashMap<String, ConcurrentSkipListSet<Long>> history;
    private final long maxtime;

    public EventCount(final long maxtimemillis) {
        this.history = new ConcurrentHashMap<>();
        this.maxtime = maxtimemillis;
    }

    public final void event(final String key) {
        final ConcurrentSkipListSet<Long> tl = this.history.get(key);
        final long now = System.currentTimeMillis();
        if (tl == null) {
            final ConcurrentSkipListSet<Long> tln = new ConcurrentSkipListSet<>();
            this.history.put(key, tln);
            tln.add(now);
        } else {
            tl.add(now);
            if (tl.size() % 77 == 0) {
                tl.subSet(0L, now - this.maxtime).forEach(event -> tl.remove(event));
            }
        }
    }

    public final int[] count(final String key, final long... timespanmillis) {
        final int[] counts = new int[timespanmillis.length];
        if (counts.length == 0) return counts;
        final ConcurrentSkipListSet<Long> tl = this.history.get(key);
        if (tl == null) {
            for (int i = 0; i < counts.length; i++) counts[i] = 0;
        } else {
            final long now = System.currentTimeMillis();
            for (int i = 0; i < counts.length; i++) counts[i] = tl.subSet(now - timespanmillis[i], now).size();
        }
        return counts;
    }

    public final int size(final String key) {
        final ConcurrentSkipListSet<Long> tl = this.history.get(key);
        if (tl == null) return 0;
        return tl.size();
    }

    public final long retryAfter(final int count, final int maxcount, final long timespanmillis) {
        assert count >= maxcount;
        final long expectedtimeperevent = timespanmillis / maxcount;
        assert count * timespanmillis / maxcount - timespanmillis > 0;
        long retryAfter = 2 * (count * expectedtimeperevent - timespanmillis + this.random.nextInt(5000));
        retryAfter = retryAfter / 10000;
        return 1 + retryAfter * 10;
    }

    public static void main(final String[] args) {
        final EventCount tl = new EventCount(60000);
        while (true) {
            try {
                Thread.sleep(tl.random.nextInt(4000));
                tl.event("test");
                System.out.println("events in last 20 seconds: " + tl.count("test", 20000)[0] + ", tl size: " + tl.size("test"));
            } catch (final InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
