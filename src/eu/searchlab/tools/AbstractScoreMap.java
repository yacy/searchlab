/**
 *  AbstractScoreMap
 *  Copyright 2011 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 28.04.2011 at http://yacy.net
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

package eu.searchlab.tools;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;


public abstract class AbstractScoreMap<E> implements ScoreMap<E> {

    /**
     * apply all E/int mappings from an external ScoreMap to this ScoreMap
     */
    @Override
    public void inc(final ScoreMap<E> map) {
        if (map == null) return;
        for (final E entry: map) {
            final int count = map.get(entry);
            if (count > 0) this.inc(entry, count);
        }
    }

    /**
     * divide the map into two halve parts using the count of the entries
     * @return the objects of the smaller entries from at least 1/2 of the list
     */
    @Override
    public List<E> lowerHalf() {

        // first calculate the average of the entries
        long a = 0;
        for (final E entry: this) a += get(entry);
        a = a / this.size();

        // then select all entries which are smaller that the average

        final ArrayList<E> list = new ArrayList<E>();
        for (final E entry: this) if (get(entry) < a) list.add(entry);
        return list;

        /*
        int half = this.size() >> 1;
        int smallestCount = 0;
        ArrayList<E> list = new ArrayList<E>();
        while (list.size() < half) {
            for (E entry: this) {
                if (get(entry) == smallestCount) list.add(entry);
            }
            smallestCount++;
        }
        return list;
        */
    }

    @Override
    public Collection<E> keyList(final boolean up) {
        final List<E> list = new ArrayList<E>(this.size());
        final Iterator<E> i = this.keys(up);
        while (i.hasNext()) list.add(i.next());
        return list;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append('[');
        final Iterator<E> i = this.keys(false);
        while (i.hasNext()) {
            final E e = i.next();
            final String s = e.toString();
            sb.append(s.length() == 0 ? "\"\"" : s).append('/').append(Integer.toString(this.get(e))).append(',');
        }
        if (sb.length() == 1) sb.append(']'); else sb.replace(sb.length() - 1, sb.length(), "]");
        return sb.toString();
    }
}