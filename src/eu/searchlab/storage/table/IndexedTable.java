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
import java.nio.charset.Charset;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import eu.searchlab.storage.io.GenericIO;
import eu.searchlab.storage.io.IOPath;
import tech.tablesaw.api.BooleanColumn;
import tech.tablesaw.api.ColumnType;
import tech.tablesaw.api.DateColumn;
import tech.tablesaw.api.DateTimeColumn;
import tech.tablesaw.api.DoubleColumn;
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

/**
 * TableIndex
 * - Maintain a named set of tables (using tablesaw)
 * - provide indexes for each table to enhance select methods
 * - provide a microservice which hosts the tables (using undertow)
 * - integrate a client for hosted tables that provide access transparently for hosted and non-hosted tables
 */
public class IndexedTable implements Iterable<JSONObject> {

    public final static String PATTERN_ISO8601_MINUTE = "yyyy-MM-dd'T'HH:mm";
    public final static SimpleDateFormat iso8601minute = new SimpleDateFormat(PATTERN_ISO8601_MINUTE, Locale.US);

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

    /**
     * Initialize an indexed table from a json.
     * This is the reverse method from toJSON() in this class.
     * @param array
     * @throws IOException
     */
    public IndexedTable(JSONArray array) throws IOException {
        if (array == null || array.length() == 0) throw new IOException("Initializing an inexed table with an array works only if at least one data record is present. This is required for a schema generation");
        this.table = Table.create();
        this.namedStringIndex = new HashMap<>();
        this.intrfStringIndex = new HashMap<>();
        this.namedLongIndex = new HashMap<>();
        this.intrfLongIndex = new HashMap<>();


        try {
            // identify column types using first row in array
            final Object obj = array.get(0);
            if (obj instanceof JSONObject) {
                // table was exported with asObjects = true
                // read column schema
                final JSONObject json = (JSONObject) obj;
                final Iterator<String> keyi = json.keys();
                while (keyi.hasNext()) {
                    final String key = keyi.next();
                    final Object value = json.opt(key);
                    assert value != null;
                    final Column<?> column = getColumn(key, value);
                    this.table.addColumns(column);
                }

                // add values
                loop: for (int i = 0; i < array.length(); i++) {
                    final JSONObject j = array.getJSONObject(i);
                    for (final String key: j.keySet()) {
                        try {
                            addValue(this.table.column(key), j.opt(key));
                        } catch (final ParseException e) {
                            e.printStackTrace();
                            continue loop;
                        }
                    }
                }
            } else {
                // table was exported with asObjects = false
                // read column schema
                final JSONArray header = (JSONArray) obj;
                JSONArray values = array.getJSONArray(1);
                assert header.length() == values.length();
                for (int i = 0; i < header.length(); i++) {
                    final String key = header.getString(i);
                    final Object value = values.get(i);
                    assert value != null;
                    final Column<?> column = getColumn(key, value);
                    this.table.addColumns(column);
                }

                // add values
                loop: for (int i = 0; i < array.length(); i++) {
                    values = array.getJSONArray(i);
                    for (int j = 0; j < header.length(); j++) {
                        try {
                            addValue(this.table.column(header.getString(j)), values.get(j));
                        } catch (final ParseException e) {
                            e.printStackTrace();
                            continue loop;
                        }
                    }
                }

            }
        } catch (final JSONException e) {
            throw new IOException(e.getMessage());
        }
    }

    public IndexedTable emptyCopy() {
        final Table e = this.table.emptyCopy();
        return new IndexedTable(e);
    }

    private void addValue(Column<?> column, Object object) throws ParseException {
        if (column instanceof LongColumn) {
            if (object instanceof Integer) {((LongColumn) column).append(((Integer) object).longValue()); return;}
            if (object instanceof Long)  {((LongColumn) column).append(((Long) object).longValue()); return;}
            if (object instanceof Float)  {((LongColumn) column).append(((Float) object).longValue()); return;}
            if (object instanceof Double) {((LongColumn) column).append(((Double) object).longValue()); return;}
            if (object instanceof Boolean) {((LongColumn) column).append(((Boolean) object).booleanValue() ? 1L : 0L); return;}
            if (object instanceof String) {((LongColumn) column).append(Long.parseLong((String) object)); return;}
        }
        if (column instanceof DoubleColumn) {
            if (object instanceof Integer) {((DoubleColumn) column).append(((Integer) object).doubleValue()); return;}
            if (object instanceof Long)  {((DoubleColumn) column).append(((Long) object).doubleValue()); return;}
            if (object instanceof Float)  {((DoubleColumn) column).append(((Float) object).doubleValue()); return;}
            if (object instanceof Double) {((DoubleColumn) column).append(((Double) object).doubleValue()); return;}
            if (object instanceof Boolean) {((DoubleColumn) column).append(((Boolean) object).booleanValue() ? 1.0d : 0.0d); return;}
            if (object instanceof String) {((DoubleColumn) column).append(Double.parseDouble((String) object)); return;}
        }
        if (column instanceof BooleanColumn) {
            if (object instanceof Integer) {((BooleanColumn) column).append(((Integer) object).intValue() != 0); return;}
            if (object instanceof Long)  {((BooleanColumn) column).append(((Long) object).longValue() != 0); return;}
            if (object instanceof Float)  {((BooleanColumn) column).append(((Float) object).floatValue() != 0.0); return;}
            if (object instanceof Double) {((BooleanColumn) column).append(((Double) object).doubleValue() != 0.0); return;}
            if (object instanceof Boolean) {((BooleanColumn) column).append(((Boolean) object).booleanValue()); return;}
            if (object instanceof String) {((BooleanColumn) column).append(((String) object).toLowerCase().equals("true")); return;}
        }
        if (column instanceof StringColumn) {
            if (object instanceof Integer) {((StringColumn) column).append(((Integer) object).toString()); return;}
            if (object instanceof Long)  {((StringColumn) column).append(((Long) object).toString()); return;}
            if (object instanceof Float)  {((StringColumn) column).append(((Float) object).toString()); return;}
            if (object instanceof Double) {((StringColumn) column).append(((Double) object).toString()); return;}
            if (object instanceof Boolean) {((StringColumn) column).append(((Boolean) object).toString()); return;}
            if (object instanceof String) {((StringColumn) column).append(((String) object)); return;}
        }
        if (column instanceof DateTimeColumn) {
            if (object instanceof String) {
                String date = (String) object; // shape: "2018-07-08T00:00"
                final int p = date.indexOf(":");
                if (p > 0 && date.length() > p + 3) date = date.substring(0, p + 3);
                final Date d = iso8601minute.parse(date);
                final DateTimeColumn dtc = (DateTimeColumn) column;
                final LocalDateTime dateTime = LocalDateTime.ofEpochSecond(d.getTime() / 1000, 0, ZoneOffset.UTC);
                dtc.append(dateTime);
                return;
            }
        }
    }

    private Column<?> getColumn(String name, Object object) {
        // attribute definitions in time-series data tables
        if (name.startsWith("view.")) return StringColumn.create(name);
        if (name.startsWith("meta.")) return StringColumn.create(name);
        if (name.startsWith("data.")) return DoubleColumn.create(name);
        if (name.startsWith("unit.")) return StringColumn.create(name);

        // time definitions in time-series data tables
        if (name.endsWith(".date")) return DateTimeColumn.create(name);
        if (name.endsWith(".time")) return LongColumn.create(name);
        if (name.endsWith(".year")) return LongColumn.create(name);
        if (name.endsWith(".week")) return LongColumn.create(name);

        // all other tables: they would possible require a full table scan for this decision
        if (object instanceof Integer) {
            return LongColumn.create(name);
        }
        if (object instanceof Long) {
            return LongColumn.create(name);
        }
        if (object instanceof Float) {
            return DoubleColumn.create(name);
        }
        if (object instanceof Double) {
            return DoubleColumn.create(name);
        }
        if (object instanceof Boolean) {
            return BooleanColumn.create(name);
        }
        if (object instanceof String && (name.equals("tscw.date") || name.equals("tsd.date") || name.endsWith(".date"))) {
            return DateTimeColumn.create(name);
        }
        return StringColumn.create(name);
    }

    public JSONArray toJSON(boolean asObjects) {
        final JSONArray array = new JSONArray();

        if (asObjects) {
            for (int row = 0; row < this.table.rowCount(); row++) {
                array.put(row2JSON(row));
            }
        } else {
            final List<String> colnames = this.table.columnNames();
            JSONArray a = new JSONArray();
            for (final String name: colnames) a.put(name);
            array.put(a);

            for (int row = 0; row < this.table.rowCount(); row++) {
                a = new JSONArray();
                for (int column = 0; column < colnames.size(); column++) {
                    final Column<?> c = this.table.column(colnames.get(column));
                    final Object val = c.get(row);
                    a.put(val);
                }
                array.put(a);
            }
        }

        return array;
    }

    public JSONObject row2JSON(int row) {
        final JSONObject json = new JSONObject(true);
        for (int column = 0; column < this.table.columnCount(); column++) {
            final Column<?> c = this.table.column(column);
            final Object val = c.get(row);
            try {json.put(c.name(), val);} catch (final JSONException e) {}
        }
        return json;
    }

    @Override
    public Iterator<JSONObject> iterator() {
        final AtomicInteger row = new AtomicInteger();
        return new Iterator<JSONObject>() {

            @Override
            public boolean hasNext() {
                return row.get() < eu.searchlab.storage.table.IndexedTable.this.table.rowCount();
            }

            @Override
            public JSONObject next() {
                if (row.get() >= eu.searchlab.storage.table.IndexedTable.this.table.rowCount()) throw new NoSuchElementException();
                return row2JSON(row.incrementAndGet() - 1);
            }
        };
    }

    private void clearIndex() {
        this.namedStringIndex.clear();
        this.intrfStringIndex.clear();
        this.namedLongIndex.clear();
        this.intrfLongIndex.clear();
    }

    public int size() {
        return this.table.rowCount();
    }

    public List<String> columnNames() {
        return this.table.columnNames();
    }

    public IndexedTable append(IndexedTable i) {
        clearIndex();
        this.table.append(i.table);
        return this;
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

    public IndexedTable whereSelects(List<String> selects) {
        if (selects.size() == 0) return this;
        return whereSelects(selects.toArray(new String[selects.size()]));
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

    @Override
    public String toString() {
        try {
            return this.toJSON(true).toString(2);
        } catch (final JSONException e) {
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

    public final static long getCellLong(final Row row, final String columnName) {
        final ColumnType columnType = row.getColumnType(columnName);
        if (columnType == ColumnType.STRING) {
            final String as = row.getString(columnName);
            final long a = as == null || as.length() == 0 ? 0 : Long.parseLong(as);
            return a;
        } else if (columnType == ColumnType.LONG) {
            final long a = row.getLong(columnName);
            return a;
        } else if (columnType == ColumnType.INTEGER) {
            final int a = row.getInt(columnName);
            return a;
        } else if (columnType == ColumnType.DOUBLE) {
            final double a = row.getDouble(columnName);
            return (long) a;
        } else if (columnType == ColumnType.FLOAT) {
            final float a = row.getFloat(columnName);
            return (long) a;
        }
        return 0;
    }

    public final static double getCellDouble(final Row row, final String columnName) {
        final ColumnType columnType = row.getColumnType(columnName);
        if (columnType == ColumnType.STRING) {
            final String as = row.getString(columnName);
            final double a = as == null || as.length() == 0 ? 0.0d : Double.parseDouble(as);
            return a;
        } else if (columnType == ColumnType.LONG) {
            final long a = row.getLong(columnName);
            return (double) a;
        } else if (columnType == ColumnType.INTEGER) {
            final int a = row.getInt(columnName);
            return (double) a;
        } else if (columnType == ColumnType.DOUBLE) {
            final double a = row.getDouble(columnName);
            return a;
        } else if (columnType == ColumnType.FLOAT) {
            final float a = row.getFloat(columnName);
            return a;
        }
        return 0.0d;
    }

    public Map<String, Integer> facet(String column) {
        final Map<String, Integer> facet = new LinkedHashMap<>();
        final StringColumn c = this.table.stringColumn(column);
        final Set<String> s = c.asSet();
        for (final String a: s) {
            final int o = c.countOccurrences(a);
            facet.put(a, o);
        }
        return facet;
    }

}
