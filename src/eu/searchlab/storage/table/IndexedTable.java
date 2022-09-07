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
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import eu.searchlab.storage.io.GenericIO;
import eu.searchlab.storage.io.IOPath;
import eu.searchlab.tools.Logger;
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
import tech.tablesaw.index.DoubleIndex;
import tech.tablesaw.index.Index;
import tech.tablesaw.index.LongIndex;
import tech.tablesaw.index.StringIndex;
import tech.tablesaw.io.csv.CsvReadOptions;
import tech.tablesaw.selection.Selection;

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
    private final Map<String, DoubleIndex> namedDoubleIndex;
    private final Map<Integer, DoubleIndex> intrfDoubleIndex;

    /**
     * Create a table with indexing of given columns
     * @param name
     * @param table
     * @param indexColumns
     * @return
     */
    public IndexedTable(final Table table) {
        this.table = table;
        this.namedStringIndex = new HashMap<>();
        this.intrfStringIndex = new HashMap<>();
        this.namedLongIndex = new HashMap<>();
        this.intrfLongIndex = new HashMap<>();
        this.namedDoubleIndex = new HashMap<>();
        this.intrfDoubleIndex = new HashMap<>();
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
    public IndexedTable(final GenericIO io, IOPath iop, final char separator, final Charset charset) throws IOException {
        final IOPath iopgz = new IOPath(iop.getBucket(), iop.getPath() + ".gz");
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
        this.namedDoubleIndex = new HashMap<>();
        this.intrfDoubleIndex = new HashMap<>();
    }

    /**
     * Initialize an indexed table from a json.
     * This is the reverse method from toJSON() in this class.
     * @param array
     * @throws IOException
     */
    public IndexedTable(final JSONArray array) throws IOException {
        if (array == null || array.length() == 0) throw new IOException("Initializing an inexed table with an array works only if at least one data record is present. This is required for a schema generation");
        this.table = Table.create();
        this.namedStringIndex = new HashMap<>();
        this.intrfStringIndex = new HashMap<>();
        this.namedLongIndex = new HashMap<>();
        this.intrfLongIndex = new HashMap<>();
        this.namedDoubleIndex = new HashMap<>();
        this.intrfDoubleIndex = new HashMap<>();


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

    private void addValue(final Column<?> column, final Object object) throws ParseException {
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

    private Column<?> getColumn(final String name, final Object object) {
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

    public JSONArray toJSON(final boolean asObjects) {
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

    public JSONObject row2JSON(final int row) {
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

    public IndexedTable append(final IndexedTable i) {
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
    public void addRow(final Row row) {
        final List<String> names = row.columnNames();
        for (int i = 0; i < row.columnCount(); i++) {
            final Column<?> c = this.table.column(names.get(i));
            c.appendObj(row.getObject(i));
        }
    }

    public StringIndex addStringIndex(final String colName) {
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

    public StringIndex addStringIndex(final int colIndex) {
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

    public LongIndex addLongIndex(final String colName) {
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

    public DoubleIndex addDoubleIndex(final String colName) {
        final Column<?> col = this.table.column(colName);
        if (col instanceof DoubleColumn) {
            final DoubleColumn dc = (DoubleColumn) col;
            final DoubleIndex di = new DoubleIndex(dc);
            final int colIndex = this.table.columnIndex(colName);
            this.namedDoubleIndex.put(colName, di);
            this.intrfDoubleIndex.put(colIndex, di);
            return di;
        } else {
            System.out.println("DoubleIndex Error: column " + colName + " has type " + col.getClass().getName());
            return null;
        }
    }

    public String getString(final int r, final String columnName) {
        return this.table.getString(r, columnName);
    }

    public IntColumn intColumn(final String columnName) {
        return this.table.intColumn(columnName);
    }

    public IntColumn intColumn(final int columnIndex) {
        return this.table.intColumn(columnIndex);
    }

    public LongColumn longColumn(final String columnName) {
        return this.table.longColumn(columnName);
    }

    public LongColumn longColumn(final int columnIndex) {
        return this.table.longColumn(columnIndex);
    }

    public DateColumn dateColumn(final String columnName) {
        return this.table.dateColumn(columnName);
    }

    public NumericColumn<?> numberColumn(final String columnName) {
        return this.table.numberColumn(columnName);
    }

    public StringColumn stringColumn(final String columnName) {
        return this.table.stringColumn(columnName);
    }

    public StringColumn stringColumn(final int columnIndex) {
        return this.table.stringColumn(columnIndex);
    }

    public DoubleColumn doubleColumn(final String columnName) {
        return this.table.doubleColumn(columnName);
    }

    public DoubleColumn doubleColumn(final int columnIndex) {
        return this.table.doubleColumn(columnIndex);
    }

    public Column<?> column(final int columnIndex) {
        return this.table.column(columnIndex);
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

    public Row row(final int i) {
        return this.table.row(i);
    }

    public IndexedTable dropRowsWithMissingValues() {
        return new IndexedTable(this.table.dropRowsWithMissingValues());
    }

    public IndexedTable addStringColumn(final String colname) {
        this.table.addColumns(StringColumn.create(colname)); // this works only if table is empty
        return this;
    }

    /**
     * indexing of a column: create for each term ocurring in the column a list of row
     * numbers where the term occurrs.
     * @param column
     * @return an index for terms in the column
     */
    public static Map<String, List<Integer>> createFulltextIndex(final StringColumn column) {
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
    public IndexedTable searchFulltext(final Map<String, List<Integer>> index, final String value) {
        final Table t = this.table.emptyCopy();
        final List<Integer> l = index.get(value);
        if (l == null) return new IndexedTable(t);
        l.forEach(r -> t.addRow(this.table.row(r)));
        return new IndexedTable(t);
    }

    public long aggregateLong(final String column) {
        final LongColumn c = this.table.longColumn(column);
        long a = 0L;
        for (int r = 0; r < c.size(); r++) {
            final long l = c.getLong(r);
            a += l;
        }
        return a;
    }

    public double aggregateDouble(final String column) {
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
        public SplitSelect(final String select) {
            final int p = select.indexOf(':');
            if (p < 0) throw new UnsupportedOperationException("no operator found: " + select);
            if (p == 0) throw new UnsupportedOperationException("no column found: " + select);
            //if (p == select.length() - 1) throw new UnsupportedOperationException("no value found: " + select);
            this.col = select.substring(0, p);
            this.val = select.substring(p + 1);
        }
    }

    private static Selection not(final Selection s) {
        return s.flip(0, s.size());
    }

    private Index getIndex(final String column) {
        Index i = this.namedStringIndex.get(column);
        if (i == null) i = this.namedLongIndex.get(column);
        if (i != null) return i;
        // create the index, but find out which kind
        final Column<?> c = this.table.column(column);
        if (c == null) {
            Logger.error("column " + column + " does not exist in " + this.table.name());
            return null;
        }
        if (c instanceof StringColumn) return this.addStringIndex(column);
        if (c instanceof LongColumn) return this.addLongIndex(column);
        if (c instanceof DoubleColumn) return this.addDoubleIndex(column);
        Logger.error("column " + column + " type in " + this.table.name() + " unknown: " + c.getClass().getName());
        return null;
    }

    private Selection whereSelection(final String columnName, String value) {
        // convert into one single selection
        final String[] values = value.split(";"); // this is considering a disjunction of values

        if (values.length == 1) {
            final Index i = getIndex(columnName);
            final boolean negation = value.startsWith("!");
            if (negation) value = value.substring(1);
            final Selection selection =
                    i instanceof StringIndex ? ((StringIndex) i).get(value) :
                        i instanceof LongIndex ? ((LongIndex) i).get(Long.parseLong(value)) :
                            i instanceof DoubleIndex ? ((DoubleIndex) i).get(Double.parseDouble(value)) :
                                null;
            return negation ? not(selection) : selection;
        } else {
            Selection dis = null;
            for (String v: values) {
                final Index i = getIndex(columnName);
                final boolean negation = v.startsWith("!");
                if (negation) v = v.substring(1);
                final Selection selection =
                        i instanceof StringIndex ? ((StringIndex) i).get(v) :
                            i instanceof LongIndex ? ((LongIndex) i).get(Long.parseLong(v)) :
                                i instanceof DoubleIndex ? ((DoubleIndex) i).get(Double.parseDouble(v)) :
                                    null;;
                                    if (negation) {
                                        if (dis == null) dis = not(selection); else dis.or(not(selection));
                                    } else {
                                        if (dis == null) dis = selection; else dis.or(selection);
                                    }
            }
            return dis;
        }
    }

    private Selection whereSelection(final String columnName, final long value) {
        return ((LongIndex) getIndex(columnName)).get(value);
    }

    private Selection whereSelection(final String columnName, final double value) {
        return ((DoubleIndex) getIndex(columnName)).get(value);
    }

    private Selection whereSelection(final String... selects) {
        if (selects.length == 0) return null;

        final SplitSelect[] ss = new SplitSelect[selects.length];
        for (int i = 0; i < selects.length; i++) ss[i] = new SplitSelect(selects[i]);

        // convert into one single selection
        Selection con = null;
        for (final SplitSelect s: ss) {
            final Selection selection = whereSelection(s.col, s.val);
            if (con == null) con = selection; else con.and(selection);
        }
        return con;
    }

    /**
     * Create a new table which is selected by a conjunctive set of select statements.
     * Each select statement is a string with syntax "<key>:<value>" that must match with all values <value>
     * within the column <key> of the table.
     * @param selects
     * @return new table with rows selected
     */
    public IndexedTable whereSelects(final String... selects) {
        if (selects.length == 0) return this;
        final Table select = this.table.where(whereSelection(selects));
        return new IndexedTable(select);
    }

    public IndexedTable whereSelects(final List<String> selects) {
        if (selects.size() == 0) return this;
        return whereSelects(selects.toArray(new String[selects.size()]));
    }

    public IndexedTable whereList(final String selects) {
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
        return this.table.toString();
    }

    public String printAll() {
        return this.table.printAll();
    }

    public IndexedTable head(final int count) {
        final Table t = this.table.emptyCopy();
        for (int r = 0; r < Math.min(count, this.table.rowCount()); r++) {
            t.addRow(this.table.row(r));
        }
        return new IndexedTable(t);
    }

    public IndexedTable where(final String columnName, final String value) {
        final Selection con = whereSelection(columnName, value);
        return new IndexedTable(this.table.where(con));
    }

    public IndexedTable where(final String columnName, final long value) {
        final Selection con = whereSelection(columnName, value);
        return new IndexedTable(this.table.where(con));
    }

    public IndexedTable whereNot(final String columnName, final long value) {
        final Selection con = whereSelection(columnName, value);
        return new IndexedTable(this.table.where(not(con)));
    }

    public String selectStringFrom(final String select, final String column, final long value) {
        final IndexedTable row = this.where(column, value);
        if (row == null || row.isEmpty()) return null;
        return row.stringColumn(select).get(0);
    }

    public String selectStringFrom(final String select, final String column, final String value) {
        final IndexedTable row = this.where(column, value);
        if (row == null || row.isEmpty()) return null;
        return row.stringColumn(select).get(0);
    }

    public double selectDoubleFrom(final String select, final String column, final String value) {
        final IndexedTable row = this.where(column, value);
        if (row == null || row.isEmpty()) return Double.NaN;
        return row.doubleColumn(select).get(0);
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
            return a;
        } else if (columnType == ColumnType.INTEGER) {
            final int a = row.getInt(columnName);
            return a;
        } else if (columnType == ColumnType.DOUBLE) {
            final double a = row.getDouble(columnName);
            return a;
        } else if (columnType == ColumnType.FLOAT) {
            final float a = row.getFloat(columnName);
            return a;
        }
        return 0.0d;
    }

    public Map<String, Integer> stringFacet(final String column) {
        final Map<String, Integer> facet = new LinkedHashMap<>();
        final Column<?> b = this.table.column(column);
        if (!(b instanceof StringColumn)) return facet;
        final StringColumn c = (StringColumn) b;
        final Set<String> s = c.asSet();
        for (final String a: s) {
            final int o = c.countOccurrences(a);
            facet.put(a, o);
        }
        return facet;
    }

    public Map<Long, Integer> longFacet(final String column) {
        final Map<Long, Integer> facet = new LinkedHashMap<>();
        final Column<?> b = this.table.column(column);
        if (!(b instanceof LongColumn)) return facet;
        final LongColumn c = (LongColumn) b;
        final Set<Long> s = new TreeSet<>();
        c.forEach(x -> s.add(x));
        for (final Long a: s) {
            final AtomicInteger o = new AtomicInteger(0);
            c.forEach(x -> {if (x == a) o.incrementAndGet();});
            facet.put(a, o.get());
        }
        return facet;
    }

}
