/**
 *  TimeSeriesTable
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
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Date;

import eu.searchlab.storage.io.ConcurrentIO;
import eu.searchlab.storage.io.IOPath;
import eu.searchlab.storage.table.TableViewer.GraphTypes;
import eu.searchlab.tools.DateParser;

import tech.tablesaw.api.DoubleColumn;
import tech.tablesaw.api.InstantColumn;
import tech.tablesaw.api.LongColumn;
import tech.tablesaw.api.StringColumn;
import tech.tablesaw.api.Table;
import tech.tablesaw.api.Row;
import tech.tablesaw.columns.Column;
import tech.tablesaw.plotly.traces.ScatterTrace;

public class TimeSeriesTable {

    public IndexedTable table;

    public InstantColumn  tsTimeCol;
    public StringColumn   tsDateCol;
    public StringColumn[] viewCols;
    public StringColumn[] metaCols;
    public Column<?>[] dataCols;

    public final static String TS_TIME   = "ts.time";
    public final static String TS_DATE   = "ts.date"; // ISO8601 format yyyy-MM-dd HH:mm:ss

    private TimeSeriesTable() {
        this.tsTimeCol   = InstantColumn.create(TS_TIME);
        this.tsDateCol   = StringColumn.create(TS_DATE);
    }

    public TimeSeriesTable(final StringColumn[] viewCols, final StringColumn[] metaCols, final Column<?>[] dataCols) throws UnsupportedOperationException {
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

    public TimeSeriesTable(final String[] viewColNames, final String[] metaColNames, final String[] dataColNames, final boolean dataIsDouble) throws UnsupportedOperationException {
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
    public TimeSeriesTable(Table xtable, final boolean dataIsDouble) throws IOException {
        // create appropriate columns from given table
        // If those columes come from a parsed input, the types may be not correct

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
                    d = DateParser.minuteDateFormat.format(new Date(t));
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
            final Date cut = DateParser.minuteDateFormat.parse("2022-05-29 00:00");
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
    public TimeSeriesTable(final ConcurrentIO io, final IOPath iop, final boolean dataIsDouble) throws IOException {
        this(TableParser.readCSV(io, iop), dataIsDouble);
    }

    /**
     * write a table to csv file
     * @param io
     * @param iop
     */
    public void storeCSV(final ConcurrentIO io, final IOPath iop) {
        TableParser.storeCSV(io, iop, this.table.table);
    }

    public int size() {
        return this.table.rowCount();
    }

    public void deleteBefore(final long time) {
        loop: for (int i = 0; i < this.tsTimeCol.size(); i++) try {
            final long t = this.tsTimeCol.get(i).toEpochMilli();
            if (t >= time) {
                final Table ntable = this.table.table().emptyCopy();
                for (int k = i; k < this.table.rowCount(); k++) {
                	Row row = this.table.row(k);
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
        } catch (DateTimeException e) {
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
        if (!checkShape(view, meta, data)) throw new RuntimeException("wrong shape");

        this.tsTimeCol.append(Instant.ofEpochMilli(time));
        this.tsDateCol.append(DateParser.minuteDateFormat.format(new Date(time)));
        for (int i = 0; i < view.length; i++) this.viewCols[i].append(view[i]);
        for (int i = 0; i < meta.length; i++) this.metaCols[i].append(meta[i]);
        for (int i = 0; i < data.length; i++) ((DoubleColumn) this.dataCols[i]).append(data[i]);
    }

    public void addValues(final long time, final String[] view, final String[] meta, final long[] data) {
        if (!checkShape(view, meta, data)) throw new RuntimeException("wrong shape");

        this.tsTimeCol.append(Instant.ofEpochMilli(time));
        this.tsDateCol.append(DateParser.minuteDateFormat.format(new Date(time)));
        for (int i = 0; i < view.length; i++) this.viewCols[i].append(view[i]);
        for (int i = 0; i < meta.length; i++) this.metaCols[i].append(meta[i]);
        for (int i = 0; i < data.length; i++) ((LongColumn) this.dataCols[i]).append(data[i]);
    }

    public void append(final TimeSeriesTable t) {
        this.table.append(t.table);
    }

    public void setValuesWhere(final long time, final String[] view, final String[] meta, final double[] data) {
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



    public String[] getMetaWhere(final String[] view) {
        rowloop: for (int r = 0; r < this.table.rowCount(); r++) {
            // try to match with view constraints
            for (int t = 0; t < view.length; t++) {
                if (!this.viewCols[t].get(r).equals(view[t])) continue rowloop;
            }

            final String[] meta = new String[this.metaCols.length];
            for (int i = 0; i < meta.length; i++) meta[i] = this.metaCols[i].get(r);
            return meta;
        }
        return null;
    }

    public double[] getDoubleValues(final long time, final String[] view) {
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

    public long[] getLongValues(final long time, final String[] view) {
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

    public long getTime(final int row) {
        return this.tsTimeCol.getLongInternal(row);
    }

    public long getFirstTime() {
        return getTime(0);
    }

    public long getLastTime() {
        final int row = this.table.size() - 1;
        return getTime(row);
    }

    public double[] getDouble(final int row) {
        final double[] d = new double[this.dataCols.length];
        int c = 0;
        for (int i = 0; i < this.dataCols.length; i++) {
            d[c] = ((DoubleColumn) this.dataCols[i]).getDouble(row);
            if (!Double.isFinite(d[c]) || Double.isNaN(d[c])) d[c] = 0.0d;
            c++;
        }
        return d;
    }

    public long[] getLong(final int row) {
        final long[] d = new long[this.dataCols.length];
        int c = 0;
        for (int i = 0; i < this.dataCols.length; i++) {
            d[c] = ((LongColumn) this.dataCols[i]).getLong(row);
            c++;
        }
        return d;
    }

    public void dropRowsWithMissingValues() {
        this.table.dropRowsWithMissingValues();
    }

    @Override
    public String toString() {
        return this.table.toString();
    }

    public String printAll() {
        return this.table.printAll();
    }

    /**
     * paint a graph from the time-series table
     * @param filename
     * @param title
     * @param xscalename
     * @param timecolname
     * @param yscalecols
     * @param y2scalecols
     * @return
     */
    public TableViewer getGraph(final String filename, final String title, final String xscalename, final String timecolname, final String[] yscalecols, final String[] y2scalecols) {
        return getGraph(this.table.table(), filename, title, xscalename, timecolname, yscalecols, y2scalecols);
    }

    public static TableViewer getGraph(final Table table, final String filename, final String title, final String xscalename, final String timecolname, final String[] yscalecols, final String[] y2scalecols) {
        final TableViewer tv = new TableViewer(filename, title, xscalename);
        expandGraph(tv, table, timecolname, yscalecols, y2scalecols);
        return tv;
    }

    public static void expandGraph(final TableViewer tv, final Table table, final String timecolname, final String[] yscalecols, final String[] y2scalecols) {
        for (final String col: yscalecols) {
            final GraphTypes gt = new TableViewer.GraphTypes(col);
            tv.timeseries(table, timecolname, 2, ScatterTrace.YAxis.Y, gt);
        }
        for (final String col: y2scalecols) {
            final GraphTypes gt = new TableViewer.GraphTypes(col);
            tv.timeseries(table, timecolname, 1, ScatterTrace.YAxis.Y2, gt);
        }
    }
}
