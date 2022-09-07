/**
 *  PersistentTables
 *  Copyright 09.10.2021 by Michael Peter Christen, @orbiterlab
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

package eu.searchlab.storage.table;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.json.JSONArray;
import org.json.JSONException;

import eu.searchlab.storage.io.ConcurrentIO;
import eu.searchlab.storage.io.IOObject;
import eu.searchlab.storage.io.IOPath;
import eu.searchlab.tools.Logger;
import tech.tablesaw.api.Table;
import tech.tablesaw.io.Source;
import tech.tablesaw.io.json.JsonReadOptions;
import tech.tablesaw.io.json.JsonReader;

/**
 * This implements a table repository with no intent to provide storage to the tables.
 * Tables may be injected into this repository which can be updated and stored as
 * a side effect.
 */
public class PersistentTables {

    private final ConcurrentHashMap<String, IndexedTable> indexes;
    private String urlstub;
    private ConcurrentIO io;
    private IOPath iop;

    /**
     * create an empty TableServer
     */
    public PersistentTables() {
        this.indexes = new ConcurrentHashMap<>();
        this.urlstub = null;
    }

    public void clear() {
        this.indexes.clear();
    }

    /**
     * Create access to a hosted TableServer
     * @param urlstub
     * @return this
     */
    public PersistentTables connect(final String urlstub) {
        this.urlstub = urlstub;
        return this;
    }

    /**
     * Create access to a GenericIO location
     * @param io
     * @param iop
     * @return this
     */
    public PersistentTables connect(final ConcurrentIO io, final IOPath iop) {
        this.io = io;
        this.iop = iop;
        return this;
    }

    /**
     * remove table, hosted and un-hosted
     * @param tablename
     * @return this
     */
    public PersistentTables removeTable(final String tablename) {
        final IOPath key = this.iop.append(tablename + ".json");
        try {this.io.removeForced(key);} catch (final IOException e) {}
        this.indexes.remove(tablename);
        return this;
    }

    /**
     * Add a non-hosted table
     * @param tablename
     * @param table
     * @return this
     */
    public PersistentTables appendTable(final String tablename, final IndexedTable table) {
        IndexedTable t = this.indexes.get(tablename);
        if (t == null) {
            t = table.emptyCopy();
            this.indexes.put(tablename, t);
        }
        t.append(table);
        return this;
    }

    /**
     * Add a non-hosted table
     * @param tablename
     * @param table
     * @return this
     */
    public PersistentTables setTable(final String tablename, final IndexedTable table) {
        final IndexedTable t = table.emptyCopy();
        this.indexes.put(tablename, t);
        t.append(table);
        return this;
    }

    public void storeTable(final String tablename) throws IOException {
        final IndexedTable t = this.indexes.get(tablename);
        if (t == null) return;
        if (this.io == null) throw new IOException("no io defined");
        if (this.iop == null) throw new IOException("no io path defined");
        final IOPath key = this.iop.append(tablename + ".json");
        try {
            this.io.writeForced(new IOObject(key, t.toJSON(true).toString(2).getBytes(StandardCharsets.UTF_8)));
        } catch (final JSONException e) {
            throw new IOException(e.getMessage());
        }
    }

    /**
     * Retrieve named table from index
     * In case the index is hosted, the resulting table may be altered but it would not alter the hosted table.
     * If the table is not hosted, altering the table will alter the table for all other requests as well.
     * @param tablename
     * @return
     * @throws IOException
     */
    public IndexedTable getTable(final String tablename) throws IOException {
        return where(tablename);
    }

    public Set<String> getTablenames() {
        return this.indexes.keySet();
    }

    public static Table head(final Table table, final int count) {
        final Table t = table.emptyCopy();
        for (int r = 0; r < Math.min(count, table.rowCount()); r++) {
            t.addRow(table.row(r));
        }
        return t;
    }

    /**
     * Client to the persisten table which is able to switch between locally hosted and remote-hosted tables.
     * Where select statement on top layer. It is important to use this as entry point
     * for all hosted tables because in case that the tables are backed with another server
     * it forwards the select statement over the network instead of pulling a whole table
     * and performing the select then.
     * @param tablename
     * @param selects a list of strings where each string is a "key:value" pair - or one string with such pairs concatenated with ','
     * @return
     */
    public IndexedTable where(final String tablename, String... selects) throws IOException {
        if (selects.length == 1 && selects[0].contains(",")) selects = selects[0].split(",");

        // try: load from remote server
        if (this.urlstub != null) {
            final StringBuilder sb = new StringBuilder();
            for (final String u: selects) sb.append(u).append(',');
            final String url = this.urlstub + tablename + ".json" + ((selects.length == 0) ? "" : "?where=" + sb.substring(0, sb.length() - 1));
                try {
                Logger.info("loading: " + url);
                final Source source = Source.fromUrl(url);
                final Table t = new JsonReader().read(JsonReadOptions.builder(source).sample(false).build());
                return new IndexedTable(t);
            } catch (final IOException e) {
                Logger.debug(e.getMessage(), e);
            }
        }

        // try: load from local copy
        IndexedTable table = this.indexes.get(tablename);
        if (table == null) try {
            // in case the table is not inside the index, load it now
            final IOPath key = this.iop.append(tablename + ".json");
            if (this.io.getIO().exists(key)) {
                final IOObject[] o = this.io.readForced(key);
                final JSONArray a = o[0].getJSONArray();
                table = new IndexedTable(a);
            }
        } catch (final IOException e) {
            Logger.error("could not load table " + tablename + " from " + this.iop.toString() + tablename + ".json");
        }
        // process where statements
        if (selects.length == 0) return table;
        return table.whereSelects(selects);
    }

}
