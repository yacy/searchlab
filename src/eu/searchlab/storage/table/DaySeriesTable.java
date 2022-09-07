/**
 *  DaySeriesTable
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

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoField;

import eu.searchlab.storage.table.TableViewer.GraphTypes;
import tech.tablesaw.api.DateTimeColumn;
import tech.tablesaw.api.DoubleColumn;
import tech.tablesaw.api.LongColumn;
import tech.tablesaw.api.StringColumn;
import tech.tablesaw.api.Table;
import tech.tablesaw.plotly.traces.ScatterTrace;

public class DaySeriesTable {

    public IndexedTable table;

    public DateTimeColumn tsdDateCol;
    public LongColumn tsdTimeCol;
    public LongColumn tsdYearCol;
    public LongColumn tsdMonthCol;
    public LongColumn tsdDayCol;
    public StringColumn tsdCALDCol;
    public StringColumn[] viewCols;
    public StringColumn[] metaCols;
    public DoubleColumn[] dataCols;
    private double[] zeroData;

    public final static String TSD_DATE  = "tsd.date";
    public final static String TSD_TIME  = "tsd.time";
    public final static String TSD_YEAR  = "tsd.year";
    public final static String TSD_MONTH = "tsd.month";
    public final static String TSD_DAY   = "tsd.day";
    public final static String TSD_CALD  = "tsd.cald"; // LocalDate format yyy-mm-dd hh:mm

    private DaySeriesTable() {
        this.tsdDateCol  = DateTimeColumn.create(TSD_DATE);
        this.tsdTimeCol  = LongColumn.create(TSD_TIME);
        this.tsdYearCol  = LongColumn.create(TSD_YEAR);
        this.tsdMonthCol = LongColumn.create(TSD_MONTH);
        this.tsdDayCol   = LongColumn.create(TSD_DAY);
        this.tsdCALDCol  = StringColumn.create(TSD_CALD);
    }

    private void initZeroes(final int len) {
        this.zeroData = new double[len];
        for (int i = 0; i < this.zeroData.length; i++) {
            this.zeroData[i] = 0.0d;
        }
    }

    /**
     * Time Series for a sequence of days
     * @param viewCols facets to reduce the time series to a subset. To get a specific time series, all viewCols must be fixed to a specific facet.
     * @param metaCols context information to the dataset, not to be used as a facet. It might create a lot of redundancy if used, thats expected.
     * @param dataCols this is the payload of the time series sequence
     */
    public DaySeriesTable(final StringColumn[] viewCols, final StringColumn[] metaCols, final DoubleColumn[] dataCols) {
        this();
        this.viewCols = viewCols;
        this.metaCols = metaCols;
        this.dataCols = dataCols;
        final Table t = Table.create()
                .addColumns(this.tsdDateCol, this.tsdTimeCol, this.tsdYearCol, this.tsdMonthCol, this.tsdDayCol, this.tsdCALDCol)
                .addColumns(this.viewCols)
                .addColumns(this.metaCols);
        for (int i = 0; i < this.dataCols.length; i++) {
            t.addColumns(this.dataCols[i]);
        }
        this.table = new IndexedTable(t);
        initZeroes(dataCols.length);
    }

    /**
     * Time Series for a sequence of days
     * @param viewCols facets to reduce the time series to a subset. To get a specific time series, all viewCols must be fixed to a specific facet.
     * @param metaCols context information to the dataset, not to be used as a facet. It might create a lot of redundancy if used, thats expected.
     * @param dataCols this is the payload of the time series sequence
     */
    public DaySeriesTable(final String[] viewColNames, final String[] metaColNames, final String[] dataColNames) {
        this();
        this.viewCols = new StringColumn[viewColNames.length];
        for (int i = 0; i < viewColNames.length; i++) this.viewCols[i] = StringColumn.create(viewColNames[i]);
        this.metaCols = new StringColumn[metaColNames.length];
        for (int i = 0; i < metaColNames.length; i++) this.metaCols[i] = StringColumn.create(metaColNames[i]);
        this.dataCols = new DoubleColumn[dataColNames.length];
        for (int i = 0; i < dataColNames.length; i++) {
            this.dataCols[i] = DoubleColumn.create(dataColNames[i]);
        }
        final Table t = Table.create()
                .addColumns(this.tsdDateCol, this.tsdTimeCol, this.tsdYearCol, this.tsdMonthCol, this.tsdDayCol, this.tsdCALDCol)
                .addColumns(this.viewCols)
                .addColumns(this.metaCols);
        for (int i = 0; i < this.dataCols.length; i++) {
            t.addColumns(this.dataCols[i]);
        }
        this.table = new IndexedTable(t);
        initZeroes(this.dataCols.length);
    }

    /**
     * Create a time series table from an indexed table which must have the following properties:
     * - It must have the long-fields tsd.time, tsd.year, tsd.month, tsd.day and tsd.cald,
     * - all other column names must have prefixes "view", "meta", "data" or "unit".
     * @param table
     */
    public DaySeriesTable(final IndexedTable table) {
        this.table = table;
        this.tsdTimeCol = this.table.table().longColumn(TSD_TIME);
        this.tsdYearCol = this.table.table().longColumn(TSD_YEAR);
        this.tsdMonthCol = this.table.table().longColumn(TSD_MONTH);
        this.tsdDayCol = this.table.table().longColumn(TSD_DAY);
        this.tsdCALDCol = this.table.table().stringColumn(TSD_CALD);
        int viewColCount = 0, metaColCount = 0, dataColCount = 0;
        for (int col = 0; col < table.columnCount(); col++) {
            final String name = table.table().columnArray()[col].name();
            if (name.startsWith("view.")) viewColCount++;
            if (name.startsWith("meta.")) metaColCount++;
            if (name.startsWith("data.")) dataColCount++;
        }
        this.viewCols = new StringColumn[viewColCount];
        this.metaCols = new StringColumn[metaColCount];
        this.dataCols = new DoubleColumn[dataColCount];
        viewColCount = 0; metaColCount = 0; dataColCount = 0;
        for (int col = 0; col < table.columnCount(); col++) {
            final String name = table.table().columnArray()[col].name();
            if (name.startsWith("view.")) this.viewCols[viewColCount++] = table.table().stringColumn(col);
            if (name.startsWith("meta.")) this.metaCols[metaColCount++] = table.table().stringColumn(col);
            if (name.startsWith("data.")) this.dataCols[dataColCount++] = table.table().doubleColumn(col);
        }
        initZeroes(this.dataCols.length);
    }

    public int size() {
        return this.table.rowCount();
    }

    public void deleteBefore(final long jahr) {
        for (int i = 0; i < this.tsdYearCol.size(); i++) {
            final long j = this.tsdYearCol.getLong(i);
            if (j >= jahr) {
                final Table t = this.table.table().emptyCopy();
                for (int k = i; k < this.table.rowCount(); k++) {
                    t.addRow(this.table.row(k));
                }
                this.tsdDateCol = t.dateTimeColumn(TSD_DATE);
                this.tsdTimeCol = t.longColumn(TSD_TIME);
                this.tsdYearCol = t.longColumn(TSD_YEAR);
                this.tsdMonthCol = t.longColumn(TSD_MONTH);
                this.tsdDayCol = t.longColumn(TSD_DAY);
                this.tsdCALDCol = t.stringColumn(TSD_CALD);
                for (int k = 0; k < this.viewCols.length; k++) this.viewCols[k] = t.stringColumn(this.viewCols[k].name());
                for (int k = 0; k < this.metaCols.length; k++) this.metaCols[k] = t.stringColumn(this.metaCols[k].name());
                for (int k = 0; k < this.dataCols.length; k++) this.dataCols[k] = t.doubleColumn(this.dataCols[k].name());
                this.table = new IndexedTable(t);
                return;
            }
        }
    }

    public LocalDateTime getLocalDate(final int year, final int month, final int day) {
        return LocalDateTime.now()
                .with(ChronoField.YEAR, year)
                .with(ChronoField.MONTH_OF_YEAR, month)
                .with(ChronoField.DAY_OF_MONTH, day)
                .with(ChronoField.HOUR_OF_DAY, 0)
                .with(ChronoField.MINUTE_OF_HOUR, 0)
                .with(ChronoField.SECOND_OF_MINUTE, 0)
                .with(ChronoField.MILLI_OF_SECOND, 0)
                .with(ChronoField.MICRO_OF_SECOND, 0)
                ;
    }

    public void addValues(final int year, final int month, final int day, final String[] view, final String[] meta, final double[] data) {
        assert view.length == this.viewCols.length : "neue view.length = " + view.length + ", bestehende view.length = " + this.viewCols.length;
        assert meta.length == this.metaCols.length : "neue meta.length = " + meta.length + ", bestehende meta.length = " + this.metaCols.length;
        assert data.length == this.dataCols.length : "neue data.length = " + data.length + ", bestehende data.length = " + this.dataCols.length;
        final LocalDateTime date = getLocalDate(year, month, day);
        final long time = date.toEpochSecond(ZoneOffset.UTC) * 1000;
        this.tsdDateCol.appendObj(LocalDateTime.ofEpochSecond(time / 1000, 0, ZoneOffset.ofHours(0)));
        this.tsdTimeCol.append(time);
        this.tsdYearCol.append(year);
        this.tsdMonthCol.append(month);
        this.tsdDayCol.append(day);
        this.tsdCALDCol.append(date.toString().substring(0, 10)); // ISO-8601 format yyy-mm-dd
        for (int i = 0; i < view.length; i++) this.viewCols[i].append(view[i]);
        for (int i = 0; i < meta.length; i++) this.metaCols[i].append(meta[i]);
        for (int i = 0; i < data.length; i++) this.dataCols[i].append(data[i]);
    }

    public void append(final WeekSeriesTable t) {
        this.table.append(t.table);
    }

    /**
     * find a row which matches with given values year, week an view.
     * There all values from meta and data are overwritten
     * @param year
     * @param week
     * @param view
     * @param meta
     * @param data
     */
    public void setValuesWhere(final int year, final int month, final int day, final String[] view, final String[] meta, final double[] data) {
        assert view.length == this.viewCols.length : "neue view.length = " + view.length + ", bestehende view.length = " + this.viewCols.length;
        assert meta.length == this.metaCols.length : "neue meta.length = " + meta.length + ", bestehende meta.length = " + this.metaCols.length;
        assert data.length == this.dataCols.length : "neue data.length = " + data.length + ", bestehende data.length = " + this.dataCols.length;
        rowloop: for (int r = 0; r < this.table.rowCount(); r++) {
            // try to match with time constraints
            if (this.tsdYearCol.get(r) != year || this.tsdMonthCol.get(r) != month || this.tsdDayCol.get(r) != day) continue;

            // try to match with view constraints
            for (int t = 0; t < view.length; t++) {
                if (!this.viewCols[t].get(r).equals(view[t])) continue rowloop;
            }

            // overwrite values and finish. We consider that this hit is unique
            for (int i = 0; i < meta.length; i++) this.metaCols[i].set(r, meta[i]);
            for (int i = 0; i < data.length; i++) this.dataCols[i].set(r, data[i]);
            return;
        }
    }

    public double[] getDataColsRow(final int row) {
        final double[] d = new double[this.dataCols.length];
        int c = 0;
        for (int i = 0; i < this.dataCols.length; i++) {
            d[c] = this.dataCols[i].getDouble(row);
            if (!Double.isFinite(d[c]) || Double.isNaN(d[c])) d[c] = 0.0d;
            c++;
        }
        return d;
    }

    public double[] getDataColsRow(final int year, final int month, final int day, final String[] view) {
        assert view == null || view.length == this.viewCols.length : "neue view.length = " + view.length + ", bestehende view.length = " + this.viewCols.length;

        // execute select
        search: for (int i = 0; i < this.table.size(); i++) {
            if (this.tsdYearCol.getLong(i) == year && this.tsdMonthCol.getLong(i) == month && this.tsdDayCol.getLong(i) == day) {
                if (view != null) for (int j = 0; j < view.length; j++) {
                    if (!view[j].equals(this.viewCols[j].getString(i))) continue search;
                }
                return getDataColsRow(i);
            }
        }
        return null;
    }

    public int getYear(final int row) {
        return this.table.longColumn(TSD_YEAR).get(row).intValue();
    }

    public int getMonth(final int row) {
        return this.table.longColumn(TSD_MONTH).get(row).intValue();
    }

    public int getDay(final int row) {
        return this.table.longColumn(TSD_DAY).get(row).intValue();
    }

    public LocalDateTime getFirstDate() {
        final long year = getYear(0);
        final long month = getMonth(0);
        final long day = getDay(0);
        return getLocalDate((int) year, (int) month, (int) day);
    }

    public LocalDateTime getLastDate() {
        final int row = this.table.size() - 1;
        final long year = getYear(row);
        final long month = getMonth(row);
        final long day = getDay(row);
        return getLocalDate((int) year, (int) month, (int) day);
    }

    public void addValuesOverWeek(final int year, final int month, final int day, final String[] view, final String[] meta, final double[] data) {
        assert view.length == this.viewCols.length : "neue view.length = " + view.length + ", bestehende view.length = " + this.viewCols.length;
        assert meta.length == this.metaCols.length : "neue meta.length = " + meta.length + ", bestehende meta.length = " + this.metaCols.length;
        assert data.length == this.dataCols.length : "neue data.length = " + data.length + ", bestehende data.length = " + this.dataCols.length;
        assert data.length == this.dataCols.length;
        //final long stop = start + weekLengthMilliseconds - 1;
        addValues(year, month, day, view, meta, data);
        //addValues(stop, year, week, view, meta, data, unit);
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
        final Table table = this.table.table();
        for (final String col: yscalecols) {
            final GraphTypes gt = new TableViewer.GraphTypes(col);
            tv.timeseries(table, timecolname, 2, ScatterTrace.YAxis.Y, gt);
        }
        for (final String col: y2scalecols) {
            final GraphTypes gt = new TableViewer.GraphTypes(col);
            tv.timeseries(table, timecolname, 1, ScatterTrace.YAxis.Y2, gt);
        }
        return tv;
    }

}
