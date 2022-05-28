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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Locale;

import eu.searchlab.storage.io.ConcurrentIO;
import eu.searchlab.storage.io.IOObject;
import eu.searchlab.storage.io.IOPath;
import eu.searchlab.tools.DateParser;
import eu.searchlab.tools.Logger;
import tech.tablesaw.api.DoubleColumn;
import tech.tablesaw.api.InstantColumn;
import tech.tablesaw.api.LongColumn;
import tech.tablesaw.api.StringColumn;
import tech.tablesaw.api.Table;
import tech.tablesaw.columns.Column;
import tech.tablesaw.io.csv.CsvReadOptions;
import tech.tablesaw.io.csv.CsvWriteOptions;
import tech.tablesaw.io.csv.CsvWriter;
import tech.tablesaw.plotly.traces.ScatterTrace;

public class TimeSeriesTable {

    public IndexedTable table;

    public InstantColumn  tshTimeCol;
    public StringColumn   tshDateCol;
    public StringColumn[] viewCols;
    public StringColumn[] metaCols;
    public Column<?>[] dataCols;

    public final static String TS_TIME   = "ts.time";
    public final static String TS_DATE   = "ts.date"; // ISO8601 format yyyy-MM-dd'T'HH:mm:ss'Z'

    private TimeSeriesTable() {
        this.tshTimeCol   = InstantColumn.create(TS_TIME);
        this.tshDateCol   = StringColumn.create(TS_DATE);
    }

    public TimeSeriesTable(final StringColumn[] viewCols, final StringColumn[] metaCols, final Column<?>[] dataCols) {
        this();
        this.viewCols = viewCols;
        this.metaCols = metaCols;
        this.dataCols = dataCols;
        final Table t = Table.create()
                .addColumns(this.tshTimeCol, this.tshDateCol)
                .addColumns(this.viewCols)
                .addColumns(this.metaCols);
        for (int i = 0; i < this.dataCols.length; i++) {
            t.addColumns(this.dataCols[i]);
        }
        this.table = new IndexedTable(t);
    }

    public TimeSeriesTable(final String[] viewColNames, final String[] metaColNames, final String[] dataColNames, final boolean dataIsDouble) {
        this();
        this.viewCols = new StringColumn[viewColNames.length];
        for (int i = 0; i < viewColNames.length; i++) this.viewCols[i] = StringColumn.create(viewColNames[i]);
        this.metaCols = new StringColumn[metaColNames.length];
        for (int i = 0; i < metaColNames.length; i++) this.metaCols[i] = StringColumn.create(metaColNames[i]);
        this.dataCols = dataIsDouble ? new DoubleColumn[dataColNames.length] : new LongColumn[dataColNames.length];
        for (int i = 0; i < dataColNames.length; i++) {
            this.dataCols[i] = dataIsDouble ? DoubleColumn.create(dataColNames[i]) : LongColumn.create(dataColNames[i]);
        }
        final Table t = Table.create()
                .addColumns(this.tshTimeCol, this.tshDateCol)
                .addColumns(this.viewCols)
                .addColumns(this.metaCols);
        for (int i = 0; i < this.dataCols.length; i++) {
            t.addColumns(this.dataCols[i]);
        }
        this.table = new IndexedTable(t);
    }

    public TimeSeriesTable(final IndexedTable table) {
        this.table = table;
        initFromTable();
    }

    public TimeSeriesTable(final ConcurrentIO io, final IOPath iop) throws IOException {
        final IOObject[] ioo = io.readForced(iop);
        assert ioo.length == 1;
        final byte[] b = ioo[0].getObject();
        final ByteArrayInputStream bais = new ByteArrayInputStream(b);
        final CsvReadOptions options =
                CsvReadOptions.builder(bais)
                    .separator(';')
                    .locale(Locale.ENGLISH)
                    .header(true)
                    .build();
        this.table = new IndexedTable(Table.read().usingOptions(options));
        initFromTable();
    }

    private void initFromTable() {
        this.tshTimeCol = this.table.table().instantColumn(TS_TIME);
        this.tshDateCol = this.table.table().stringColumn(TS_DATE);
        int viewColCount = 0, metaColCount = 0, dataColCount = 0;
        for (int col = 0; col < this.table.columnCount(); col++) {
            final String name = this.table.table().columnArray()[col].name();
            if (name.startsWith("view.")) viewColCount++;
            if (name.startsWith("meta.")) metaColCount++;
            if (name.startsWith("data.")) dataColCount++;
        }
        this.viewCols = new StringColumn[viewColCount];
        this.metaCols = new StringColumn[metaColCount];
        this.dataCols = new DoubleColumn[dataColCount];
        viewColCount = 0; metaColCount = 0; dataColCount = 0;
        for (int col = 0; col < this.table.columnCount(); col++) {
            final String name = this.table.table().columnArray()[col].name();
            if (name.startsWith("view.")) this.viewCols[viewColCount++] = this.table.table().stringColumn(col);
            if (name.startsWith("meta.")) this.metaCols[metaColCount++] = this.table.table().stringColumn(col);
            if (name.startsWith("data.")) this.dataCols[dataColCount++] = this.table.table().column(col);
        }
    }

    public void storeCSV(final ConcurrentIO io, final IOPath iop) {
        final long start = System.currentTimeMillis();
        try {
            // prepare document
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            final OutputStreamWriter osw = new OutputStreamWriter(baos, StandardCharsets.UTF_8);
            final CsvWriteOptions options = CsvWriteOptions.builder(osw)
                    .separator(';')
                    .header(true)
                    .build();
            new CsvWriter().write(this.table.table(), options);
            osw.close();

            // write to io
            io.writeForced(new IOObject(iop, baos.toByteArray()));
            final long stop = System.currentTimeMillis();
            Logger.info("wrote user audit " + io.toString() + " in " + (stop - start) + " milliseconds");
        } catch (final IOException e) {
            Logger.warn("failed to write user audit to " + io.toString(), e);
        }
    }

    public static TimeSeriesTable readCSV(final ConcurrentIO io, final IOPath iop) throws IOException {
        return new TimeSeriesTable(io, iop);
    }

    public int size() {
        return this.table.rowCount();
    }

    public void deleteBefore(final long time) {
        for (int i = 0; i < this.tshTimeCol.size(); i++) {
            final long t = this.tshTimeCol.getLongInternal(i);
            if (t >= time) {
                final Table ntable = this.table.table().emptyCopy();
                for (int k = i; k < this.table.rowCount(); k++) {
                    ntable.addRow(this.table.row(k));
                }
                this.tshTimeCol = ntable.instantColumn(TS_TIME);
                this.tshDateCol = ntable.stringColumn(TS_DATE);
                for (int k = 0; k < this.viewCols.length; k++) this.viewCols[k] = ntable.stringColumn(this.viewCols[k].name());
                for (int k = 0; k < this.metaCols.length; k++) this.metaCols[k] = ntable.stringColumn(this.metaCols[k].name());
                for (int k = 0; k < this.dataCols.length; k++) this.dataCols[k] = ntable.column(this.dataCols[k].name());
                this.table = new IndexedTable(ntable);
                return;
            }
        }
    }

    public void addValues(final long time, final String[] view, final String[] meta, final double[] data) {
        assert view.length == this.viewCols.length : "neue view.length = " + view.length + ", bestehende view.length = " + this.viewCols.length;
        assert meta.length == this.metaCols.length : "neue meta.length = " + meta.length + ", bestehende meta.length = " + this.metaCols.length;
        assert data.length == this.dataCols.length : "neue data.length = " + data.length + ", bestehende data.length = " + this.dataCols.length;

        this.tshTimeCol.append(Instant.ofEpochMilli(time));
        this.tshDateCol.append(DateParser.iso8601Format.format(new Date(time)));
        for (int i = 0; i < view.length; i++) this.viewCols[i].append(view[i]);
        for (int i = 0; i < meta.length; i++) this.metaCols[i].append(meta[i]);
        for (int i = 0; i < data.length; i++) ((DoubleColumn) this.dataCols[i]).append(data[i]);
    }

    public void addValues(final long time, final String[] view, final String[] meta, final long[] data) {
        assert view.length == this.viewCols.length : "neue view.length = " + view.length + ", bestehende view.length = " + this.viewCols.length;
        assert meta.length == this.metaCols.length : "neue meta.length = " + meta.length + ", bestehende meta.length = " + this.metaCols.length;
        assert data.length == this.dataCols.length : "neue data.length = " + data.length + ", bestehende data.length = " + this.dataCols.length;

        this.tshTimeCol.append(Instant.ofEpochMilli(time));
        this.tshDateCol.append(DateParser.iso8601Format.format(new Date(time)));
        for (int i = 0; i < view.length; i++) this.viewCols[i].append(view[i]);
        for (int i = 0; i < meta.length; i++) this.metaCols[i].append(meta[i]);
        for (int i = 0; i < data.length; i++) ((LongColumn) this.dataCols[i]).append(data[i]);
    }

    public void append(final TimeSeriesTable t) {
        this.table.append(t.table);
    }

    public void setValuesWhere(final long time, final String[] view, final String[] meta, final double[] data) {
        assert view.length == this.viewCols.length : "neue view.length = " + view.length + ", bestehende view.length = " + this.viewCols.length;
        assert meta.length == this.metaCols.length : "neue meta.length = " + meta.length + ", bestehende meta.length = " + this.metaCols.length;
        assert data.length == this.dataCols.length : "neue data.length = " + data.length + ", bestehende data.length = " + this.dataCols.length;
        rowloop: for (int r = 0; r < this.table.rowCount(); r++) {
            // try to match with time constraints
            if (this.tshTimeCol.getLongInternal(r) != time) continue;

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
        assert view.length == this.viewCols.length : "neue view.length = " + view.length + ", bestehende view.length = " + this.viewCols.length;
        assert meta.length == this.metaCols.length : "neue meta.length = " + meta.length + ", bestehende meta.length = " + this.metaCols.length;
        assert data.length == this.dataCols.length : "neue data.length = " + data.length + ", bestehende data.length = " + this.dataCols.length;
        rowloop: for (int r = 0; r < this.table.rowCount(); r++) {
            // try to match with time constraints
            if (this.tshTimeCol.getLongInternal(r) != time) continue;

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

    public double[] getDoubleValues(final long time, final String[] view) {
        assert view == null || view.length == this.viewCols.length : "neue view.length = " + view.length + ", bestehende view.length = " + this.viewCols.length;

        // execute select
        search: for (int i = 0; i < this.table.size(); i++) {
            if (this.tshTimeCol.getLongInternal(i) == time) {
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
            if (this.tshTimeCol.getLongInternal(i) == time) {
                if (view != null) for (int j = 0; j < view.length; j++) {
                    if (!view[j].equals(this.viewCols[j].getString(i))) continue search;
                }
                return getLong(i);
            }
        }
        return null;
    }

    public long getTime(final int row) {
        return this.tshTimeCol.getLongInternal(row);
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
        final TableViewer tv = new TableViewer(filename, title, xscalename);
        for (final String col: yscalecols) {
            tv.timeseries(this.table.table(), timecolname, 2, ScatterTrace.YAxis.Y, new TableViewer.GraphTypes(col));
        }
        for (final String col: y2scalecols) {
            tv.timeseries(this.table.table(), timecolname, 1, ScatterTrace.YAxis.Y2, new TableViewer.GraphTypes(col));
        }
        return tv;
    }

}
