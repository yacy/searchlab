/**
 *  IODirList
 *  Copyright 01.06.2022 by Michael Peter Christen, @orbiterlab
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

package eu.searchlab.storage.io;

import java.util.ArrayList;
import java.util.Date;

import eu.searchlab.storage.io.IODirList.Entry;

/**
 * IODirList is a list with a creation date of Entry objects,
 * which is the collection of metadata about an object within a (virtual!) directory of a IOPath
 */
public class IODirList extends ArrayList<Entry> {

	private static final long serialVersionUID = 1L;

	private final long created;
	private long latestEntry;

	public IODirList() {
		super();
		this.created = System.currentTimeMillis();
		this.latestEntry = 0;
	}

	public long getCreated() {
		return this.created;
	}

	public long getAge() {
		return System.currentTimeMillis() - this.created;
	}

	public long getLatestEntry() {
		return this.latestEntry;
	}

	public boolean isStale() {
		if (this.latestEntry == 0) {
			// no actual information available, we simply use a time-out of 10 seconds
			// which should cover clicking-around
			return getAge() > 10000;
		} else {
			// use a typical time-to-live here
			return getAge() * 2 > this.created - this.latestEntry;
		}
	}

    @Override
	public boolean add(final Entry e) {
    	this.latestEntry = Math.max(this.latestEntry, e.time);
    	assert this.latestEntry <= this.created;
        return super.add(e);
    }

	public final static class Entry {

	    public final String name;
	    public final boolean isDir;
	    public final long size;
	    public final long time;

	    public Entry(final String name, final boolean isDir, final long size, final long time) {
	        this.name = name; this.isDir = isDir; this.size = size; this.time = time;
	    }

	    @Override
	    public String toString() {
	        return this.name + " " + (this.isDir ? "" : this.size + " " + new Date(this.time).toString());
	    }
	}
}
