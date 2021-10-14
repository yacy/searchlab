/**
 *  IndexedTable
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import eu.searchlab.storage.io.GenericIO;
import eu.searchlab.storage.io.IOPath;
import tech.tablesaw.api.ColumnType;
import tech.tablesaw.api.DateColumn;
import tech.tablesaw.api.IntColumn;
import tech.tablesaw.api.LongColumn;
import tech.tablesaw.api.NumericColumn;
import tech.tablesaw.api.Row;
import tech.tablesaw.api.StringColumn;
import tech.tablesaw.api.Table;
import tech.tablesaw.columns.Column;
import tech.tablesaw.index.LongIndex;
import tech.tablesaw.index.StringIndex;
import tech.tablesaw.io.csv.CsvReadOptions;
import tech.tablesaw.io.json.JsonWriteOptions;

/**
 * TableIndex
 * - Maintain a named set of tables (using tablesaw)
 * - provide indexes for each table to enhance select methods
 * - provide a microservice which hosts the tables (using undertow)
 * - integrate a client for hosted tables that provide access transparently for hosted and non-hosted tables
 */
public class IndexedTable {

    protected final Table table; // it is essential that this is final
    private final Map<String, StringIndex> namedStringIndex;
    private final Map<Integer, StringIndex> intrfStringIndex;
    private final Map<String, LongIndex> namedLongIndex;
    private final Map<Integer, LongIndex> intrfLongIndex;


    /**
     * Create a table with indexing of given columns
     * @param name
     * @param table
     * @param indexColumns
     * @return
     */
    public IndexedTable(Table table) {
        this.table = table;
        this.namedStringIndex = new HashMap<>();
        this.intrfStringIndex = new HashMap<>();
        this.namedLongIndex = new HashMap<>();
        this.intrfLongIndex = new HashMap<>();
    }

    /**
     * Initialize a table with content from a GenericIO path.
     * The storage format must be CSV.
     * @param io
     * @param iop
     * @param separator
     * @param charset
     * @throws IOException
     */
    public IndexedTable(GenericIO io, IOPath iop, char separator, Charset charset) throws IOException {
        IOPath iopgz = new IOPath(iop.getBucket(), iop.getPath() + ".gz");
        if (!io.exists(iop) && io.exists(iopgz)) iop = iopgz;
        final InputStream ris = iopgz.getPath().endsWith(".gz") ? new GZIPInputStream(io.read(iopgz)) : io.read(iopgz);
        final InputStream is = new InputStream() {
            @Override
            public int read() throws IOException {
                final int c = ris.read();
                if (c == ',') return '.';
                return c;
            }
        };

        final Reader reader = new BufferedReader(new InputStreamReader(is, charset));
        final List<ColumnType> colTypes = new ArrayList<>();
        colTypes.add(ColumnType.DOUBLE);
        colTypes.add(ColumnType.LONG);
        colTypes.add(ColumnType.STRING); // this is required to prevent that the reader detext TEXT column types where we are not able to create a column index
        final CsvReadOptions options =
                CsvReadOptions.builder(reader)
                    .separator(separator)
                    .locale(Locale.GERMAN)
                    .header(true)
                    .columnTypesToDetect(colTypes)
                    .build();
        try {
            this.table = Table.read().usingOptions(options);
        } catch (final IllegalArgumentException e) {
            throw new IOException(e.getMessage());
        } finally {
            is.close();
            ris.close();
        }

        this.namedStringIndex = new HashMap<>();
        this.intrfStringIndex = new HashMap<>();
        this.namedLongIndex = new HashMap<>();
        this.intrfLongIndex = new HashMap<>();
    }

    public int size() {
        return this.table.rowCount();
    }

    /**
     * Append a row to the table.
     * Unlike the Table.addRow method this method is also able to
     * add rows from other tables which have rows with same name but different order.
     * @param row to add with same name and types as this table
     */
    public void addRow(Row row) {
        final List<String> names = row.columnNames();
        for (int i = 0; i < row.columnCount(); i++) {
           final Column<?> c = this.table.column(names.get(i));
           c.appendObj(row.getObject(i));
        }
    }

    public StringIndex addStringIndex(String colName) {
        final Column<?> col = this.table.column(colName);
        if (col instanceof StringColumn) {
            final StringColumn sc = (StringColumn) col;
            final StringIndex si = new StringIndex(sc);
            final int colIndex = this.table.columnIndex(colName);
            this.namedStringIndex.put(colName, si);
            this.intrfStringIndex.put(colIndex, si);
            return si;
        } else {
            System.out.println("StringIndex Error: column " + colName + " has type " + col.getClass().getName());
            return null;
        }
    }

    public StringIndex addStringIndex(int colIndex) {
        final Column<?> col = this.table.column(colIndex);
        if (col instanceof StringColumn) {
            final StringColumn sc = (StringColumn) col;
            final StringIndex si = new StringIndex(sc);
            this.intrfStringIndex.put(colIndex, si);
            return si;
        } else {
            System.out.println("StringIndex Error: column " + colIndex + " has type " + col.getClass().getName());
            return null;
        }
    }

    public LongIndex addLongIndex(String colName) {
        final Column<?> col = this.table.column(colName);
        if (col instanceof LongColumn) {
            final LongColumn lc = (LongColumn) col;
            final LongIndex li = new LongIndex(lc);
            final int colInt = this.table.columnIndex(colName);
            this.namedLongIndex.put(colName, li);
            this.intrfLongIndex.put(colInt, li);
            return li;
        } else {
            System.out.println("LongIndex Error: column " + colName + " has type " + col.getClass().getName());
            return null;
        }
    }

    public String getString(int r, String columnName) {
        return this.table.getString(r, columnName);
    }

    public IntColumn intColumn(String columnName) {
        return this.table.intColumn(columnName);
      }

    public IntColumn intColumn(int columnIndex) {
        return this.table.intColumn(columnIndex);
    }

    public DateColumn dateColumn(String columnName) {
        return this.table.dateColumn(columnName);
    }

    public NumericColumn<?> numberColumn(String columnName) {
        return this.table.numberColumn(columnName);
    }

    public StringColumn stringColumn(String columnName) {
        return this.table.stringColumn(columnName);
    }

    public StringColumn stringColumn(int columnIndex) {
        return this.table.stringColumn(columnIndex);
    }

    public boolean isEmpty() {
        return this.table.isEmpty();
    }

    public int rowCount() {
        return this.table.rowCount();
    }

    public int columnCount() {
        return this.table.columnCount();
    }

    public Row row(int i) {
        return this.table.row(i);
    }

    public IndexedTable dropRowsWithMissingValues() {
        return new IndexedTable(this.table.dropRowsWithMissingValues());
    }

    public IndexedTable addStringColumn(String colname) {
        this.table.addColumns(StringColumn.create(colname)); // this works only if table is empty
        return this;
    }

    /**
     * indexing of a column: create for each term ocurring in the column a list of row
     * numbers where the term occurrs.
     * @param column
     * @return an index for terms in the column
     */
    public static Map<String, List<Integer>> createFulltextIndex(StringColumn column) {
        final Map<String, List<Integer>> index = new LinkedHashMap<>();
        for (int r = 0; r < column.size(); r++) {
            final String s = column.getString(r);
            List<Integer> l = index.get(s);
            if (l == null) {
                l = new ArrayList<>();
                index.put(s, l);
            }
            l.add(r);
        }
        return index;
    }

    /**
     * using a fulltext index to retrieve a subtable with all matching rows
     * @param index
     * @param value
     * @return
     */
    public IndexedTable searchFulltext(Map<String, List<Integer>> index, String value) {
        final Table t = this.table.emptyCopy();
        final List<Integer> l = index.get(value);
        if (l == null) return new IndexedTable(t);
        l.forEach(r -> t.addRow(this.table.row(r)));
        return new IndexedTable(t);
    }

    public int aggregateInt(String column) {
        //Column<?> c = table.column(column);
        int a = 0;
        for (int r = 0; r < this.table.rowCount(); r++) {
            final Row row = this.table.row(r);
            a += row.getInt(column);
        }
        return a;
    }

    public double aggregateDouble(String column) {
        //Column<?> c = table.column(column);
        double a = 0;
        for (int r = 0; r < this.table.rowCount(); r++) {
            final Row row = this.table.row(r);
            final double d = row.getDouble(column);
            assert d >= 0;
            a += d;
        }
        return a;
    }

    public static class SplitSelect {
        public final String col, val;
        /**
         * Splitting of a key/value pair as separated by operator symbol ':'
         * @param select
         */
        public SplitSelect(String select) {
            final int p = select.indexOf(':');
            if (p < 0) throw new UnsupportedOperationException("no operator found: " + select);
            if (p == 0) throw new UnsupportedOperationException("no column found: " + select);
            //if (p == select.length() - 1) throw new UnsupportedOperationException("no value found: " + select);
            this.col = select.substring(0, p);
            this.val = select.substring(p + 1);
        }
    }

    public IndexedTable whereSelects(String... selects) {
        if (selects.length == 0) return this;

        // handle first select (may use indexed where selection
        SplitSelect s = new SplitSelect(selects[0]);
        IndexedTable rtable = this.where(s.col, s.val);

        // handle remaining selects
        for (int i = 1; i < selects.length; i++) {
            s = new SplitSelect(selects[i]);
            if (s.val.length() > 0) rtable = rtable.where(s.col, s.val);
        }

        return rtable;
    }

    public IndexedTable whereList(String selects) {
        return this.whereSelects(selects.split(","));
    }

    public Table table() {
        return this.table;
    }

    public String print() {
        return this.table.print();
    }

    public String toJSON(boolean asObjects) throws IOException {
        final StringWriter writer = new StringWriter();
        this.table.write().usingOptions(JsonWriteOptions.builder(writer).asObjects(asObjects).header(true).build());
        return writer.getBuffer().toString();
    }



    @Override
    public String toString() {
        try {
            return this.toJSON(true);
        } catch (final IOException e) {
            return this.print();
        }
    }

    public IndexedTable head(int count) {
        final Table t = this.table.emptyCopy();
        for (int r = 0; r < Math.min(count, this.table.rowCount()); r++) {
            t.addRow(this.table.row(r));
        }
        return new IndexedTable(t);
    }

    public IndexedTable where(String columnName, String value) {
        final Column<?> c;
        try {
            c = this.table.column(columnName);
        } catch (final IllegalStateException e) {
            return new IndexedTable(this.table.emptyCopy());
        }
        if (c instanceof IntColumn) return this.where(columnName, Integer.parseInt(value));
        if (c instanceof LongColumn) return this.where(columnName, Long.parseLong(value));

        StringIndex si = this.namedStringIndex.get(columnName);
        if (si == null) {
            si = this.addStringIndex(columnName);
        }
        if (si != null) {
            return new IndexedTable(this.table.where(si.get(value)));
        }

        // for some reason (maybe column type not StringColumn) we cannot have an index.
        // do an iteration
        final Table t = this.table.emptyCopy();
        for (int r = 0; r < this.table.rowCount(); r++) {
            final Row row = this.table.row(r);
            if (value.equals(row.getString(columnName))) t.addRow(row);
        }
        return new IndexedTable(t);
    }

    public IndexedTable where(int columnNum, String value) {
        final Column<?> c;
        try {
            c = this.table.column(columnNum);
        } catch (final IllegalStateException e) {
            return new IndexedTable(this.table.emptyCopy());
        }
        if (c instanceof IntColumn) return this.where(columnNum, Integer.parseInt(value));
        if (c instanceof LongColumn) return this.where(columnNum, Long.parseLong(value));

        StringIndex si = this.intrfStringIndex.get(columnNum);
        if (si == null) {
            si = this.addStringIndex(columnNum);
        }
        if (si != null) {
            return new IndexedTable(this.table.where(si.get(value)));
        }

        // for some reason (maybe column type not StringColumn) we cannot have an index.
        // do an iteration
        final Table t = this.table.emptyCopy();
        for (int r = 0; r < this.table.rowCount(); r++) {
            final Row row = this.table.row(r);
            if (value.equals(row.getString(columnNum))) t.addRow(row);
        }
        return new IndexedTable(t);
    }

    public IndexedTable where(StringColumn column, String value) {
        final Table t = this.table.emptyCopy();
        for (int r = 0; r < this.table.rowCount(); r++) {
            final Row row = this.table.row(r);
            if (value.equals(column.getString(r))) t.addRow(row);
        }
        return new IndexedTable(t);
    }

    public IndexedTable where(int column, int value) {
        final Table t = this.table.emptyCopy();
        for (int r = 0; r < this.table.rowCount(); r++) {
            final Row row = this.table.row(r);
            if (value == row.getInt(column)) t.addRow(row);
        }
        return new IndexedTable(t);
    }

    public IndexedTable where(String column, int value) {
        final Table t = this.table.emptyCopy();
        for (int r = 0; r < this.table.rowCount(); r++) {
            final Row row = this.table.row(r);
            if (value == row.getInt(column)) t.addRow(row);
        }
        return new IndexedTable(t);
    }

    public IndexedTable where(int column, long value) {
        final Table t = this.table.emptyCopy();
        for (int r = 0; r < this.table.rowCount(); r++) {
            final Row row = this.table.row(r);
            if (value == row.getLong(column)) t.addRow(row);
        }
        return new IndexedTable(t);
    }

    public IndexedTable where(String column, long value) {
        final Table t = this.table.emptyCopy();
        for (int r = 0; r < this.table.rowCount(); r++) {
            final Row row = this.table.row(r);
            if (value == row.getLong(column)) t.addRow(row);
        }
        return new IndexedTable(t);
    }

    public String selectStringFrom(int select, int column, int value) {
        final IndexedTable row = this.where(column, value);
        if (row == null || row.isEmpty()) return null;
        return row.stringColumn(select).get(0);
    }

    public String selectStringFrom(String select, int column, int value) {
        final IndexedTable row = this.where(column, value);
        if (row == null || row.isEmpty()) return null;
        return row.stringColumn(select).get(0);
    }

    public String selectStringFrom(int select, String column, int value) {
        final IndexedTable row = this.where(column, value);
        if (row == null || row.isEmpty()) return null;
        return row.stringColumn(select).get(0);
    }

    public String selectStringFrom(String select, String column, int value) {
        final IndexedTable row = this.where(column, value);
        if (row == null || row.isEmpty()) return null;
        return row.stringColumn(select).get(0);
    }

    public String selectStringFrom(int select, int column, String value) {
        final IndexedTable row = this.where(column, value);
        if (row == null || row.isEmpty()) return null;
        return row.stringColumn(select).get(0);
    }

    public String selectStringFrom(String select, int column, String value) {
        final IndexedTable row = this.where(column, value);
        if (row == null || row.isEmpty()) return null;
        return row.stringColumn(select).get(0);
    }

    public String selectStringFrom(int select, String column, String value) {
        final IndexedTable row = this.where(column, value);
        if (row == null || row.isEmpty()) return null;
        return row.stringColumn(select).get(0);
    }

    public String selectStringFrom(String select, String column, String value) {
        final IndexedTable row = this.where(column, value);
        if (row == null || row.isEmpty()) return null;
        return row.stringColumn(select).get(0);
    }

    public Integer selectIntFrom(int select, int column, int value) {
        final IndexedTable row = this.where(column, value);
        if (row == null || row.isEmpty()) return null;
        return row.intColumn(select).get(0);
    }

    public Integer selectIntFrom(String select, int column, int value) {
        final IndexedTable row = this.where(column, value);
        if (row == null || row.isEmpty()) return null;
        return row.intColumn(select).get(0);
    }

    public Integer selectIntFrom(int select, String column, int value) {
        final IndexedTable row = this.where(column, value);
        if (row == null || row.isEmpty()) return null;
        return row.intColumn(select).get(0);
    }

    public Integer selectIntFrom(String select, String column, int value) {
        final IndexedTable row = this.where(column, value);
        if (row == null || row.isEmpty()) return null;
        return row.intColumn(select).get(0);
    }

    public Integer selectIntFrom(int select, int column, String value) {
        final IndexedTable row = this.where(column, value);
        if (row == null || row.isEmpty()) return null;
        return row.intColumn(select).get(0);
    }

    public Integer selectIntFrom(String select, int column, String value) {
        final IndexedTable row = this.where(column, value);
        if (row == null || row.isEmpty()) return null;
        return row.intColumn(select).get(0);
    }

    public Integer selectIntFrom(int select, String column, String value) {
        final IndexedTable row = this.where(column, value);
        if (row == null || row.isEmpty()) return null;
        return row.intColumn(select).get(0);
    }

    public Integer selectIntFrom(String select, String column, String value) {
        final IndexedTable row = this.where(column, value);
        if (row == null || row.isEmpty()) return null;
        return row.intColumn(select).get(0);
    }
}
