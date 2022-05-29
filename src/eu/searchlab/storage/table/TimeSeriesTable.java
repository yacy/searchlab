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
import java.text.ParseException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.Locale;

import eu.searchlab.storage.io.ConcurrentIO;
import eu.searchlab.storage.io.IOObject;
import eu.searchlab.storage.io.IOPath;
import eu.searchlab.tools.DateParser;
import eu.searchlab.tools.Logger;
import tech.tablesaw.api.DateColumn;
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
    public final static String TS_DATE   = "ts.date"; // ISO8601 format yyyy-MM-dd, no time, no timezone. timezone used is GMT

    private TimeSeriesTable() {
        this.tshTimeCol   = InstantColumn.create(TS_TIME);
        this.tshDateCol   = StringColumn.create(TS_DATE);
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
                .addColumns(this.tshTimeCol, this.tshDateCol)
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
                .addColumns(this.tshTimeCol, this.tshDateCol)
                .addColumns(this.viewCols)
                .addColumns(this.metaCols);
        for (int i = 0; i < this.dataCols.length; i++) {
            t.addColumns(this.dataCols[i]);
        }
        this.table = new IndexedTable(t);
    }

    public TimeSeriesTable(final IndexedTable table, final boolean dataIsDouble) throws IOException {
        initFromTable(table.table, dataIsDouble);
    }

    public TimeSeriesTable(final ConcurrentIO io, final IOPath iop, final boolean dataIsDouble) throws IOException {
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
        final Table xtable = Table.read().usingOptions(options);
        initFromTable(xtable, dataIsDouble);
    }

    private void initFromTable(final Table xtable, final boolean dataIsDouble) throws IOException {
        // create appropriate columns from given table
        // If those columes come from a parsed input, the types may be not correct
        final Column<?> ts_time_candidate = xtable.column(TS_TIME);
        if (ts_time_candidate instanceof InstantColumn) {
            this.tshTimeCol = (InstantColumn) ts_time_candidate;
        } else if (ts_time_candidate instanceof StringColumn) {
            // parse the dates to get the instant values
            this.tshTimeCol = InstantColumn.create(TS_TIME);
            for (final String ds: ((StringColumn) ts_time_candidate).asList()) {
                try {
                    final Date d = DateParser.iso8601MillisFormat.parse(ds);
                    final Instant i = Instant.ofEpochMilli(d.getTime());
                    this.tshTimeCol.append(i);
                } catch (final ParseException e) {
                    throw new IOException("cannot parse date: " + ds, e);
                }
            }
        } else {
            throw new IOException("could not parse time column");
        }

        final Column<?> ts_date_candidate = xtable.column(TS_DATE);
        if (ts_date_candidate instanceof StringColumn) {
            this.tshDateCol = (StringColumn) ts_date_candidate;
        } else if (ts_date_candidate instanceof DateColumn) {
            // parse the dates to get the instant values
            this.tshDateCol = StringColumn.create(TS_DATE);
            for (final LocalDate ld: ((DateColumn) ts_date_candidate).asList()) {
                final String s = ld.toString();
                this.tshDateCol.append(s);
            }
        } else {
            throw new IOException("could not parse time column");
        }

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
            if (name.startsWith("view.")) {
                final Column<?> view_candidate = xtable.column(col);
                if (view_candidate instanceof StringColumn) {
                    this.viewCols[viewColCount++] = (StringColumn) view_candidate;
                } else {
                    // parse the dates to get the instant values
                    this.viewCols[viewColCount] = StringColumn.create(name);
                    for (final Object o: view_candidate.asList()) {
                        this.viewCols[viewColCount].append(o.toString());
                    }
                    viewColCount++;
                }
            }
            if (name.startsWith("meta.")) this.metaCols[metaColCount++] = xtable.stringColumn(col);
            if (name.startsWith("data.")) {
                if (dataIsDouble) {
                    if (xtable.column(col) instanceof DoubleColumn) {
                        this.dataCols[dataColCount++] = xtable.column(col);
                    } else {
                        final DoubleColumn dc = DoubleColumn.create(name);
                        this.dataCols[dataColCount++] = dc;
                        for (final Object o: (xtable.column(col)).asList()) {
                            dc.append(Double.parseDouble(o.toString()));
                        }
                    }
                } else { // long
                    if (xtable.column(col) instanceof LongColumn) {
                        this.dataCols[dataColCount++] = xtable.column(col);
                    } else {
                        final LongColumn lc = LongColumn.create(name);
                        this.dataCols[dataColCount++] = lc;
                        for (final Object o: (xtable.column(col)).asList()) {
                            lc.append(Long.parseLong(o.toString()));
                        }
                    }
                }

            }
        }

        // insert columns into table
        final Table t = Table.create()
                .addColumns(this.tshTimeCol, this.tshDateCol)
                .addColumns(this.viewCols)
                .addColumns(this.metaCols);
        for (int i = 0; i < this.dataCols.length; i++) {
            t.addColumns(this.dataCols[i]);
        }
        this.table = new IndexedTable(t);
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
            Logger.info("wrote user audit " + iop.toString() + " in " + (stop - start) + " milliseconds");
        } catch (final IOException e) {
            Logger.warn("failed to write user audit to " + iop.toString(), e);
        }
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
        this.tshDateCol.append(DateParser.dayDateFormat.format(new Date(time)));
        for (int i = 0; i < view.length; i++) this.viewCols[i].append(view[i]);
        for (int i = 0; i < meta.length; i++) this.metaCols[i].append(meta[i]);
        for (int i = 0; i < data.length; i++) ((DoubleColumn) this.dataCols[i]).append(data[i]);
    }

    public void addValues(final long time, final String[] view, final String[] meta, final long[] data) {
        assert view.length == this.viewCols.length : "neue view.length = " + view.length + ", bestehende view.length = " + this.viewCols.length;
        assert meta.length == this.metaCols.length : "neue meta.length = " + meta.length + ", bestehende meta.length = " + this.metaCols.length;
        assert data.length == this.dataCols.length : "neue data.length = " + data.length + ", bestehende data.length = " + this.dataCols.length;

        this.tshTimeCol.append(Instant.ofEpochMilli(time));
        this.tshDateCol.append(DateParser.dayDateFormat.format(new Date(time)));
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
