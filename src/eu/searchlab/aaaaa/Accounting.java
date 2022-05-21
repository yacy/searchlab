/**
 *  Accounting
 *  Copyright 19.04.2022 by Michael Peter Christen, @orbiterlab
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

package eu.searchlab.aaaaa;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import eu.searchlab.storage.io.GenericIO;
import eu.searchlab.storage.io.IOPath;

/**
 * This class helps to collect the digital gold: assets from crawls which the user
 * is able to process themselves. The assets are:
 * - warc files from the crawl loader
 * - index files from the parser that can be used directly for indexing
 * - a crawl start audit log which also contains index deletions in one timeline
 * - a corpora set of index corpus definitions for each index collection
 */
public class Accounting {

	private GenericIO io;
	private IOPath iop;
	
	public Accounting(GenericIO io, IOPath iop) {
		this.io = io;
		this.iop = iop;
	}

	public void storeIndex(JSONArray indexObjArray) {
		
	}
	
}
