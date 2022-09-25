/**
 *  MinuteSeriesTable
 *  Copyright 27.05.2022 by Michael Peter Christen, @orbiterlab
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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;

import eu.searchlab.storage.io.ConcurrentIO;
import eu.searchlab.storage.io.IOPath;
import eu.searchlab.tools.DateParser;
import eu.searchlab.tools.Logger;
import tech.tablesaw.api.DoubleColumn;
import tech.tablesaw.api.InstantColumn;
import tech.tablesaw.api.LongColumn;
import tech.tablesaw.api.Row;
import tech.tablesaw.api.StringColumn;
import tech.tablesaw.api.Table;
import tech.tablesaw.columns.Column;

/**
 * Time Series Table for by-minute time series:
 * every entry gets a time stamp with minute-precision.
 */
public class MinuteSeriesTable extends AbstractTimeSeriesTable implements TimeSeriesTable {

    public InstantColumn  tsTimeCol;
    public StringColumn   tsDateCol;

    public final static String TS_TIME   = "ts.time";
    public final static String TS_DATE   = "ts.date"; // ISO8601 format yyyy-MM-dd HH:mm:ss


    private MinuteSeriesTable() {
        this.tsTimeCol   = InstantColumn.create(TS_TIME);
        this.tsDateCol   = StringColumn.create(TS_DATE);
    }

    public MinuteSeriesTable(final StringColumn[] viewCols, final StringColumn[] metaCols, final Column<?>[] dataCols) throws UnsupportedOperationException {
        this();

        // validate columns
        for (int i = 0; i < viewCols.length; i++)  {
            if (!viewCols[i].name().startsWith("view.")) throw new UnsupportedOperationException("column " + viewCols[i].name() + ": unsupported name. must start with 'view.'");
        }
        for (int i = 0; i < metaCols.length; i++) {
            if (!metaCols[i].name().startsWith("meta.")) throw new UnsupportedOperationException("column " + viewCols[i].name() + ": unsupported name. must start with 'meta.'");
        }
        for (int i = 0; i < dataCols.length; i++) {
            if (!dataCols[i].name().startsWith("data.")) throw new UnsupportedOperationException("column " + viewCols[i].name() + ": unsupported name. must start with 'data.'");
        }

        // initialize
        this.viewCols = viewCols;
        this.metaCols = metaCols;
        this.dataCols = dataCols;
        final Table t = Table.create()
                .addColumns(this.tsTimeCol, this.tsDateCol)
                .addColumns(this.viewCols)
                .addColumns(this.metaCols);
        for (int i = 0; i < this.dataCols.length; i++) {
            t.addColumns(this.dataCols[i]);
        }
        this.table = new IndexedTable(t);
    }

    public MinuteSeriesTable(final String[] viewColNames, final String[] metaColNames, final String[] dataColNames, final boolean dataIsDouble) throws UnsupportedOperationException {
        this();
        this.viewCols = new StringColumn[viewColNames.length];
        for (int i = 0; i < viewColNames.length; i++) {
            if (!viewColNames[i].startsWith("view.")) throw new UnsupportedOperationException("column " + viewColNames[i] + ": unsupported name. must start with 'view.'");
            this.viewCols[i] = StringColumn.create(viewColNames[i]);
        }
        this.metaCols = new StringColumn[metaColNames.length];
        for (int i = 0; i < metaColNames.length; i++) {
            if (!metaColNames[i].startsWith("meta.")) throw new UnsupportedOperationException("column " + metaColNames[i] + ": unsupported name. must start with 'meta.'");
            this.metaCols[i] = StringColumn.create(metaColNames[i]);
        }
        this.dataCols = dataIsDouble ? new DoubleColumn[dataColNames.length] : new LongColumn[dataColNames.length];
        for (int i = 0; i < dataColNames.length; i++) {
            if (!dataColNames[i].startsWith("data.")) throw new UnsupportedOperationException("column " + dataColNames[i] + ": unsupported name. must start with 'data.'");
            this.dataCols[i] = dataIsDouble ? DoubleColumn.create(dataColNames[i]) : LongColumn.create(dataColNames[i]);
        }
        final Table t = Table.create()
                .addColumns(this.tsTimeCol, this.tsDateCol)
                .addColumns(this.viewCols)
                .addColumns(this.metaCols);
        for (int i = 0; i < this.dataCols.length; i++) {
            t.addColumns(this.dataCols[i]);
        }
        this.table = new IndexedTable(t);
    }

    /**
     * convert an existing table to TimeSeriesTable
     * @param table
     * @param dataIsDouble
     * @throws IOException
     */
    public MinuteSeriesTable(Table xtable, final boolean dataIsDouble) throws IOException {
        // create appropriate columns from given table
        // If those columes come from a parsed input, the types may be not correct

        // verify column names
        final Set<String> colnames = new LinkedHashSet<>();
        colnames.addAll(xtable.columnNames());
        if (!xtable.column(0).name().equals(TS_TIME) && !colnames.contains(TS_TIME)) {
            // considert that the first column is TS_TIME
            xtable.column(0).setName(TS_TIME);
        }
        if (!xtable.column(1).name().equals(TS_DATE) && !colnames.contains(TS_DATE)) {
            // considert that the first column is TS_TIME
            xtable.column(0).setName(TS_DATE);
        }

        // verify consistency and order
        xtable = xtable.sortAscendingOn(TS_TIME);
        this.tsTimeCol = TableParser.asInstant(xtable.column(TS_TIME));
        this.tsDateCol = TableParser.asString(xtable.column(TS_DATE));

        // sort out the columns in the import to look like our schema
        int viewColCount = 0, metaColCount = 0, dataColCount = 0;
        for (int col = 0; col < xtable.columnCount(); col++) {
            final String name = xtable.columnArray()[col].name();
            if (name.startsWith("view.")) viewColCount++;
            if (name.startsWith("meta.")) metaColCount++;
            if (name.startsWith("data.")) dataColCount++;
        }
        this.viewCols = new StringColumn[viewColCount];
        this.metaCols = new StringColumn[metaColCount];
        this.dataCols = dataIsDouble ? new DoubleColumn[dataColCount] : new LongColumn[dataColCount];
        viewColCount = 0; metaColCount = 0; dataColCount = 0;
        for (int col = 0; col < xtable.columnCount(); col++) {
            final String name = xtable.columnArray()[col].name();
            if (name.startsWith("view.")) this.viewCols[viewColCount++] = TableParser.asString(xtable.column(col));
            if (name.startsWith("meta.")) this.metaCols[metaColCount++] = TableParser.asString(xtable.column(col));
            if (name.startsWith("data.")) this.dataCols[dataColCount++] = dataIsDouble ? TableParser.asDouble(xtable.column(col)) : TableParser.asLong(xtable.column(col));
        }

        // patch old date values to new one
        String lastd = "";
        long lastt = 0;
        final long now = System.currentTimeMillis();
        final SimpleDateFormat minuteDateFormatParser = DateParser.minuteDateFormatParser();
        for (int i = 0; i < this.tsDateCol.size(); i++) {
            String d = this.tsDateCol.get(i);
            long t = this.tsTimeCol.get(i).toEpochMilli();
            if (!d.startsWith("202")) {
                this.tsTimeCol.set(i, lastt);
                this.tsDateCol.set(i, lastd);
                continue;
            }

            if (d.length() == 10) {
                if (t < 0 || t > now) {
                    d = lastd;
                    t = lastt;
                } else {
                    d = minuteDateFormatParser.format(new Date(t));
                }
                this.tsTimeCol.set(i, Instant.ofEpochMilli(t));
                this.tsDateCol.set(i, d);
            }
            lastd = d;
            lastt = this.tsTimeCol.get(i).toEpochMilli();
        }

        // create a new table with the columns at the position where we want them
        final Table t = Table.create()
                .addColumns(this.tsTimeCol, this.tsDateCol)
                .addColumns(this.viewCols)
                .addColumns(this.metaCols);
        for (int i = 0; i < this.dataCols.length; i++) {
            t.addColumns(this.dataCols[i]);
        }
        this.table = new IndexedTable(t);

        // clean up old rows
        try {
            final Date cut = minuteDateFormatParser.parse("2022-05-29 00:00");
            this.deleteBefore(cut.getTime());
        } catch (final ParseException e) {
        }
    }

    /**
     * read a TimeSeriesTable from csv
     * @param io
     * @param iop
     * @param dataIsDouble
     * @throws IOException
     */
    public MinuteSeriesTable(final ConcurrentIO io, final IOPath iop, final boolean dataIsDouble) throws IOException {
        this(TableParser.readCSV(io, iop), dataIsDouble);
    }

    /**
     * make an empty clone of this TimeSeriesTable
     * @return a new TimeSeriesTable with the same column types as this table
     */
    public MinuteSeriesTable emptyClone() {
        final String[] viewColNames = new String[this.viewCols.length], metaColNames = new String[this.metaCols.length], dataColNames = new String[this.dataCols.length];
        for (int i = 0; i < viewColNames.length; i++) viewColNames[i] = this.viewCols[i].name();
        for (int i = 0; i < metaColNames.length; i++) metaColNames[i] = this.metaCols[i].name();
        for (int i = 0; i < dataColNames.length; i++) dataColNames[i] = this.dataCols[i].name();
        final boolean dataIsDouble = this.dataCols[0] instanceof DoubleColumn;
        final MinuteSeriesTable clone = new MinuteSeriesTable(viewColNames, metaColNames, dataColNames, dataIsDouble);
        return clone;
    }

    public void sort() {
        this.table = this.table.sort();
        this.tsTimeCol = (InstantColumn) this.table.column(0);
        this.tsDateCol = (StringColumn) this.table.column(1);
        for (int i = 0; i < this.viewCols.length; i++) this.viewCols[i] = (StringColumn) this.table.column(i + 2);
        for (int i = 0; i < this.metaCols.length; i++) this.metaCols[i] = (StringColumn) this.table.column(i + 2 + this.viewCols.length);
        for (int i = 0; i < this.dataCols.length; i++) this.dataCols[i] = this.table.column(i + 2 + this.viewCols.length + this.metaCols.length);
    }

    /**
     * make a copy of this TimeSeriesTable where all values are aggregated over time
     * @return
     */
    public MinuteSeriesTable aggregation() {
        final MinuteSeriesTable aggregation = emptyClone();
        final boolean dataIsDouble = this.dataCols[0] instanceof DoubleColumn;
        if (dataIsDouble) {
            final double[] a = new double[this.dataCols.length];
            for (int i = 0; i < a.length; i++) a[i] = 0.0d;
            for (int row = 0; row < this.size(); row++) {
                final long time = getTime(row);
                final String[] view = getView(row);
                final String[] meta = getMeta(row);
                final double[] data = getDouble(row);
                for (int i = 0; i < a.length; i++) a[i] += data[i];
                aggregation.addValues(time, view, meta, a);
            }
        } else {
            final long[] a = new long[this.dataCols.length];
            for (int i = 0; i < a.length; i++) a[i] = 0L;
            for (int row = 0; row < this.size(); row++) {
                final long time = getTime(row);
                final String[] view = getView(row);
                final String[] meta = getMeta(row);
                final long[] data = getLong(row);
                for (int i = 0; i < a.length; i++) a[i] += data[i];
                aggregation.addValues(time, view, meta, a);
            }
        }
        return aggregation;
    }

    /**
     * write a table to csv file
     * @param io
     * @param iop
     */
    public void storeCSV(final ConcurrentIO io, final IOPath iop) {
        TableParser.storeCSV(io, iop, this.table.table);
    }

    public void deleteBefore(final long time) {
        loop: for (int i = 0; i < this.tsTimeCol.size(); i++) try {
            final long t = this.tsTimeCol.get(i).toEpochMilli();
            if (t >= time) {
                final Table ntable = this.table.table().emptyCopy();
                for (int k = i; k < this.table.rowCount(); k++) {
                    final Row row = this.table.row(k);
                    ntable.append(row);
                }
                this.tsTimeCol = ntable.instantColumn(TS_TIME);
                this.tsDateCol = ntable.stringColumn(TS_DATE);
                for (int k = 0; k < this.viewCols.length; k++) this.viewCols[k] = ntable.stringColumn(this.viewCols[k].name());
                for (int k = 0; k < this.metaCols.length; k++) this.metaCols[k] = ntable.stringColumn(this.metaCols[k].name());
                for (int k = 0; k < this.dataCols.length; k++) this.dataCols[k] = ntable.column(this.dataCols[k].name());
                this.table = new IndexedTable(ntable);
                return;
            }
        } catch (final DateTimeException e) {
            continue loop;
        }
    }

    public boolean checkShape(final String[] view, final String[] meta, final long[] data) {
        assert view.length == this.viewCols.length : "neue view.length = " + view.length + ", bestehende view.length = " + this.viewCols.length;
        assert meta.length == this.metaCols.length : "neue meta.length = " + meta.length + ", bestehende meta.length = " + this.metaCols.length;
        assert data.length == this.dataCols.length : "neue data.length = " + data.length + ", bestehende data.length = " + this.dataCols.length;
        return view.length == this.viewCols.length && meta.length == this.metaCols.length&& data.length == this.dataCols.length;
    }
    public boolean checkShape(final String[] view, final String[] meta, final double[] data) {
        assert view.length == this.viewCols.length : "neue view.length = " + view.length + ", bestehende view.length = " + this.viewCols.length;
        assert meta.length == this.metaCols.length : "neue meta.length = " + meta.length + ", bestehende meta.length = " + this.metaCols.length;
        assert data.length == this.dataCols.length : "neue data.length = " + data.length + ", bestehende data.length = " + this.dataCols.length;
        return view.length == this.viewCols.length && meta.length == this.metaCols.length&& data.length == this.dataCols.length;
    }

    public void addValues(final long time, final String[] view, final String[] meta, final double[] data) {
        assert time > 0 : "time = " + time;
        if (!checkShape(view, meta, data)) throw new RuntimeException("wrong shape");
        addValues(time, view, meta);
        for (int i = 0; i < data.length; i++) ((DoubleColumn) this.dataCols[i]).append(data[i]);
    }

    public void addValues(final long time, final String[] view, final String[] meta, final long[] data) {
        assert time > 0 : "time = " + time;
        if (!checkShape(view, meta, data)) throw new RuntimeException("wrong shape");
        addValues(time, view, meta);
        for (int i = 0; i < data.length; i++) ((LongColumn) this.dataCols[i]).append(data[i]);
    }

    private void addValues(final long time, final String[] view, final String[] meta) {
        assert time > 0 : "time = " + time;
        this.tsTimeCol.append(Instant.ofEpochMilli(time));
        try {
            this.tsDateCol.append(DateParser.minuteDateFormatParser().format(new Date(time)));
        } catch (final Exception e) {
            Logger.warn("date formatting problem with time = " + time, e);
            throw e;
        }
        for (int i = 0; i < view.length; i++) this.viewCols[i].append(view[i]);
        for (int i = 0; i < meta.length; i++) this.metaCols[i].append(meta[i]);
    }

    public void append(final MinuteSeriesTable t) {
        this.table.append(t.table);
    }

    public void setValuesWhere(final long time, final String[] view, final String[] meta, final double[] data) {
        assert time > 0 : "time = " + time;
        if (!checkShape(view, meta, data)) throw new RuntimeException("wrong shape");
        rowloop: for (int r = 0; r < this.table.rowCount(); r++) {
            // try to match with time constraints
            if (this.tsTimeCol.getLongInternal(r) != time) continue;

            // try to match with view constraints
            for (int t = 0; t < view.length; t++) {
                if (!this.viewCols[t].get(r).equals(view[t])) continue rowloop;
            }

            // overwrite values and finish. We consider that this hit is unique
            for (int i = 0; i < meta.length; i++) this.metaCols[i].set(r, meta[i]);
            for (int i = 0; i < data.length; i++) ((DoubleColumn) this.dataCols[i]).set(r, data[i]);
            return;
        }
    }

    public void setValuesWhere(final long time, final String[] view, final String[] meta, final long[] data) {
        assert time > 0 : "time = " + time;
        if (!checkShape(view, meta, data)) throw new RuntimeException("wrong shape");
        rowloop: for (int r = 0; r < this.table.rowCount(); r++) {
            // try to match with time constraints
            if (this.tsTimeCol.getLongInternal(r) != time) continue;

            // try to match with view constraints
            for (int t = 0; t < view.length; t++) {
                if (!this.viewCols[t].get(r).equals(view[t])) continue rowloop;
            }

            // overwrite values and finish. We consider that this hit is unique
            for (int i = 0; i < meta.length; i++) this.metaCols[i].set(r, meta[i]);
            for (int i = 0; i < data.length; i++) ((LongColumn) this.dataCols[i]).set(r, data[i]);
            return;
        }
    }

    public double[] getDoubleWhere(final long time, final String[] view) {
        assert view == null || view.length == this.viewCols.length : "neue view.length = " + view.length + ", bestehende view.length = " + this.viewCols.length;

        // execute select
        search: for (int i = 0; i < this.table.size(); i++) {
            if (this.tsTimeCol.getLongInternal(i) == time) {
                if (view != null) for (int j = 0; j < view.length; j++) {
                    if (!view[j].equals(this.viewCols[j].getString(i))) continue search;
                }
                return getDouble(i);
            }
        }
        return null;
    }

    public long[] getLongWhere(final long time, final String[] view) {
        assert view == null || view.length == this.viewCols.length : "neue view.length = " + view.length + ", bestehende view.length = " + this.viewCols.length;

        // execute select
        search: for (int i = 0; i < this.table.size(); i++) {
            if (this.tsTimeCol.getLongInternal(i) == time) {
                if (view != null) for (int j = 0; j < view.length; j++) {
                    if (!view[j].equals(this.viewCols[j].getString(i))) continue search;
                }
                return getLong(i);
            }
        }
        return null;
    }

    public long getFirstTime() {
        return getTime(0);
    }

    public long getLastTime() {
        final int row = this.table.size() - 1;
        return getTime(row);
    }

    public long getTime(final int row) {
        return this.tsTimeCol.get(row).getEpochSecond() * 1000L;
    }

}
