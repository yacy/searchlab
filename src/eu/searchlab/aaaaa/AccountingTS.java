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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.json.JSONException;
import org.json.JSONObject;

import eu.searchlab.storage.io.ConcurrentIO;
import eu.searchlab.storage.io.GenericIO;
import eu.searchlab.storage.io.IOPath;
import eu.searchlab.storage.table.TableParser;
import eu.searchlab.storage.table.TimeSeriesTable;
import eu.searchlab.tools.DateParser;
import eu.searchlab.tools.MultiProtocolURL;

/**
 * This class helps to collect the digital gold: assets from crawls which the user
 * is able to process themselves. The assets are:
 * - "warc" warc files from the crawl loader
 * - "index" index files from the parser that can be used directly for indexing
 * - "graph" the hyperlink graph of connected documents
 * - "crawl" a crawl start audit log which also contains index deletions in one timeline
 * - "corpus" a corpora set of index corpus definitions for each index collection
 */
public class AccountingTS {

    private final ConcurrentIO cio;
    private final IOPath aaaaaIop, accountingIop;

    public AccountingTS(final GenericIO io, final IOPath aaaaaIop) {
        this.cio = new ConcurrentIO(io, 10000);
        this.aaaaaIop = aaaaaIop;
        this.accountingIop = this.aaaaaIop.append("accounting");
    }

    public IOPath getAssetsPathForUser(final String user_id) {
        return this.accountingIop.append(user_id);
    }

    public void storeCrawlStart(final String user_id, final JSONObject crawlStart) throws IOException {
        final IOPath userPath = getAssetsPathForUser(user_id);
        final IOPath crawlPath = userPath.append("crawl");
        final IOPath datePath =  crawlPath.append("crawlstart-" + DateParser.dayDateFormat.format(new Date()) + ".jsonlist");
        try {
            final String jsona = crawlStart.toString(0).replaceAll("\n", "") + "\n";
            final byte[] b = jsona.getBytes(StandardCharsets.UTF_8);
            this.cio.appendForced(datePath, b);
        } catch (final JSONException e) {
            throw new IOException(e.getMessage());
        }
    }

    private final static String[] corpusViewColNames = new String[] {"view.user_id", "view.range", "view.host"};
    private final static String[] corpusMetaColNames = new String[] {"meta.collections"};
    private final static String[] corpusDataColNames = new String[] {"data.depth", "data.size"};

    public void storeCorpus(final String user_id, final String range, final List<MultiProtocolURL> urls, final Set<String> collections, final long depth, final long size) throws IOException {
        final IOPath userPath = getAssetsPathForUser(user_id);
        final IOPath corpusPath = userPath.append("corpus.csv");
        TimeSeriesTable corpusTable = new TimeSeriesTable(corpusViewColNames, corpusMetaColNames, corpusDataColNames, false);
        if (this.cio.exists(corpusPath)) try {corpusTable = new TimeSeriesTable(this.cio, corpusPath, false);} catch (final IOException e) {}
        if (corpusTable.viewCols.length != corpusViewColNames.length ||
            corpusTable.metaCols.length != corpusMetaColNames.length ||
            corpusTable.dataCols.length != corpusDataColNames.length) {
            corpusTable = new TimeSeriesTable(corpusViewColNames, corpusMetaColNames, corpusDataColNames, false);
        }

        for (final MultiProtocolURL url: urls) {
            corpusTable.addValues(System.currentTimeMillis(),
                    new String[] {user_id, range, url.getHost()},
                    new String[] {collections.toString().replaceAll("\\[", "").replaceAll("\\]", "")},
                    new long[] {depth, size});
        }
        TableParser.storeCSV(this.cio, corpusPath, corpusTable.table.table());
    }

}
