/**
 *  HourSeriesTable
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

import tech.tablesaw.api.DateTimeColumn;
import tech.tablesaw.api.DoubleColumn;
import tech.tablesaw.api.LongColumn;
import tech.tablesaw.api.StringColumn;
import tech.tablesaw.api.Table;
import tech.tablesaw.plotly.traces.ScatterTrace;

public class HourSeriesTable {

    public IndexedTable table;

    public DateTimeColumn tshDateCol;
    public LongColumn tshTimeCol;
    public LongColumn tshYearCol;
    public LongColumn tshMonthCol;
    public LongColumn tshDayCol;
    public LongColumn tshHourCol;
    public StringColumn tshSYKWCol;
    public StringColumn tshCALDCol;
    public StringColumn[] viewCols;
    public StringColumn[] metaCols;
    public DoubleColumn[] dataCols;
    private double[] zeroData;

    public final static String TSH_DATE  = "tsh.date";
    public final static String TSH_TIME  = "tsh.time";
    public final static String TSH_YEAR  = "tsh.year";
    public final static String TSH_MONTH = "tsh.month";
    public final static String TSH_DAY   = "tsh.day";
    public final static String TSH_HOUR  = "tsh.hour";
    public final static String TSH_CALD  = "tsh.cald"; // LocalDate format yyy-mm-dd hh:mm

    private HourSeriesTable() {
        this.tshDateCol  = DateTimeColumn.create(TSH_DATE);
        this.tshTimeCol  = LongColumn.create(TSH_TIME);
        this.tshYearCol  = LongColumn.create(TSH_YEAR);
        this.tshMonthCol = LongColumn.create(TSH_MONTH);
        this.tshDayCol   = LongColumn.create(TSH_DAY);
        this.tshHourCol  = LongColumn.create(TSH_HOUR);
        this.tshCALDCol  = StringColumn.create(TSH_CALD);
    }

    private void initZeroes(final int len) {
        this.zeroData = new double[len];
        for (int i = 0; i < this.zeroData.length; i++) {
            this.zeroData[i] = 0.0d;
        }
    }

    public HourSeriesTable(final StringColumn[] viewCols, final StringColumn[] metaCols, final DoubleColumn[] dataCols) {
        this();
        this.viewCols = viewCols;
        this.metaCols = metaCols;
        this.dataCols = dataCols;
        final Table t = Table.create()
                .addColumns(this.tshDateCol, this.tshTimeCol, this.tshYearCol, this.tshMonthCol, this.tshDayCol, this.tshHourCol, this.tshSYKWCol, this.tshCALDCol)
                .addColumns(this.viewCols)
                .addColumns(this.metaCols);
        for (int i = 0; i < this.dataCols.length; i++) {
            t.addColumns(this.dataCols[i]);
        }
        this.table = new IndexedTable(t);
        initZeroes(dataCols.length);
    }

    public HourSeriesTable(final String[] viewColNames, final String[] metaColNames, final String[] dataColNames) {
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
                .addColumns(this.tshDateCol, this.tshTimeCol, this.tshYearCol, this.tshMonthCol, this.tshDayCol, this.tshHourCol, this.tshSYKWCol, this.tshCALDCol)
                .addColumns(this.viewCols)
                .addColumns(this.metaCols);
        for (int i = 0; i < this.dataCols.length; i++) {
            t.addColumns(this.dataCols[i]);
        }
        this.table = new IndexedTable(t);
        initZeroes(this.dataCols.length);
    }

    public HourSeriesTable(final IndexedTable table) {
        this.table = table;
        this.tshTimeCol = this.table.table().longColumn(TSH_TIME);
        this.tshYearCol = this.table.table().longColumn(TSH_YEAR);
        this.tshMonthCol = this.table.table().longColumn(TSH_MONTH);
        this.tshDayCol = this.table.table().longColumn(TSH_DAY);
        this.tshHourCol = this.table.table().longColumn(TSH_HOUR);
        this.tshCALDCol = this.table.table().stringColumn(TSH_CALD);
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
        for (int i = 0; i < this.tshYearCol.size(); i++) {
            final long j = this.tshYearCol.getLong(i);
            if (j >= jahr) {
                final Table t = this.table.table().emptyCopy();
                for (int k = i; k < this.table.rowCount(); k++) {
                    t.addRow(this.table.row(k));
                }
                this.tshDateCol = t.dateTimeColumn(TSH_DATE);
                this.tshTimeCol = t.longColumn(TSH_TIME);
                this.tshYearCol = t.longColumn(TSH_YEAR);
                this.tshMonthCol = t.longColumn(TSH_MONTH);
                this.tshDayCol = t.longColumn(TSH_DAY);
                this.tshHourCol = t.longColumn(TSH_HOUR);
                this.tshCALDCol = t.stringColumn(TSH_CALD);
                for (int k = 0; k < this.viewCols.length; k++) this.viewCols[k] = t.stringColumn(this.viewCols[k].name());
                for (int k = 0; k < this.metaCols.length; k++) this.metaCols[k] = t.stringColumn(this.metaCols[k].name());
                for (int k = 0; k < this.dataCols.length; k++) this.dataCols[k] = t.doubleColumn(this.dataCols[k].name());
                this.table = new IndexedTable(t);
                return;
            }
        }
    }

    public LocalDateTime getLocalDate(final int year, final int month, final int day, final int hour) {
        return LocalDateTime.now()
                .with(ChronoField.YEAR, year)
                .with(ChronoField.MONTH_OF_YEAR, month)
                .with(ChronoField.DAY_OF_MONTH, day)
                .with(ChronoField.HOUR_OF_DAY, hour)
                .with(ChronoField.MINUTE_OF_HOUR, 0)
                .with(ChronoField.SECOND_OF_MINUTE, 0)
                .with(ChronoField.MILLI_OF_SECOND, 0)
                .with(ChronoField.MICRO_OF_SECOND, 0)
                ;
    }

    public void addValues(final int year, final int month, final int day, final int hour, final String[] view, final String[] meta, final double[] data) {
        assert view.length == this.viewCols.length : "neue view.length = " + view.length + ", bestehende view.length = " + this.viewCols.length;
        assert meta.length == this.metaCols.length : "neue meta.length = " + meta.length + ", bestehende meta.length = " + this.metaCols.length;
        assert data.length == this.dataCols.length : "neue data.length = " + data.length + ", bestehende data.length = " + this.dataCols.length;
        final LocalDateTime date = getLocalDate(year, month, day, hour);
        final long time = date.toEpochSecond(ZoneOffset.UTC);
        this.tshDateCol.appendObj(LocalDateTime.ofEpochSecond(time / 1000, 0, ZoneOffset.ofHours(0)));
        this.tshTimeCol.append(time);
        this.tshYearCol.append(year);
        this.tshMonthCol.append(month);
        this.tshDayCol.append(day);
        this.tshHourCol.append(hour);
        this.tshCALDCol.append(date.toString()); // ISO-8601 format yyy-mm-dd
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
    public void setValuesWhere(final int year, final int month, final int day, final int hour, final String[] view, final String[] meta, final double[] data) {
        assert view.length == this.viewCols.length : "neue view.length = " + view.length + ", bestehende view.length = " + this.viewCols.length;
        assert meta.length == this.metaCols.length : "neue meta.length = " + meta.length + ", bestehende meta.length = " + this.metaCols.length;
        assert data.length == this.dataCols.length : "neue data.length = " + data.length + ", bestehende data.length = " + this.dataCols.length;
        rowloop: for (int r = 0; r < this.table.rowCount(); r++) {
            // try to match with time constraints
            if (this.tshYearCol.get(r) != year || this.tshMonthCol.get(r) != month || this.tshDayCol.get(r) != day || this.tshHourCol.get(r) != hour) continue;

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

    public double[] getValues(final int year, final int month, final int day, final int hour, final String[] view) {
        assert view == null || view.length == this.viewCols.length : "neue view.length = " + view.length + ", bestehende view.length = " + this.viewCols.length;

        // execute select
        search: for (int i = 0; i < this.table.size(); i++) {
            if (this.tshYearCol.getLong(i) == year && this.tshMonthCol.getLong(i) == month && this.tshDayCol.getLong(i) == day && this.tshHourCol.getLong(i) == hour) {
                if (view != null) for (int j = 0; j < view.length; j++) {
                    if (!view[j].equals(this.viewCols[j].getString(i))) continue search;
                }
                return getDouble(i);
            }
        }
        return null;
    }

    public long getYear(final int row) {
        return this.table.longColumn(TSH_YEAR).get(row);
    }

    public long getMonth(final int row) {
        return this.table.longColumn(TSH_MONTH).get(row);
    }

    public long getDay(final int row) {
        return this.table.longColumn(TSH_DAY).get(row);
    }

    public long getHour(final int row) {
        return this.table.longColumn(TSH_HOUR).get(row);
    }

    public LocalDateTime getFirstDate() {
        final long year = getYear(0);
        final long month = getMonth(0);
        final long day = getDay(0);
        final long hour = getHour(0);
        return getLocalDate((int) year, (int) month, (int) day, (int) hour);
    }

    public LocalDateTime getLastDate() {
        final int row = this.table.size() - 1;
        final long year = getYear(row);
        final long month = getMonth(row);
        final long day = getDay(row);
        final long hour = getHour(row);
        return getLocalDate((int) year, (int) month, (int) day, (int) hour);
    }

    public double[] getDouble(final int row) {
        final double[] d = new double[this.dataCols.length];
        int c = 0;
        for (int i = 0; i < this.dataCols.length; i++) {
            d[c] = this.dataCols[i].getDouble(row);
            if (!Double.isFinite(d[c]) || Double.isNaN(d[c])) d[c] = 0.0d;
            c++;
        }
        return d;
    }

    public void addValuesOverWeek(final int year, final int month, final int day, final int hour, final String[] view, final String[] meta, final double[] data) {
        assert view.length == this.viewCols.length : "neue view.length = " + view.length + ", bestehende view.length = " + this.viewCols.length;
        assert meta.length == this.metaCols.length : "neue meta.length = " + meta.length + ", bestehende meta.length = " + this.metaCols.length;
        assert data.length == this.dataCols.length : "neue data.length = " + data.length + ", bestehende data.length = " + this.dataCols.length;
        assert data.length == this.dataCols.length;
        //final long stop = start + weekLengthMilliseconds - 1;
        addValues(year, month, day, hour, view, meta, data);
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
        for (final String col: yscalecols) {
            tv.timeseries(this.table.table(), timecolname, 2, ScatterTrace.YAxis.Y, new TableViewer.GraphTypes(col));
        }
        for (final String col: y2scalecols) {
            tv.timeseries(this.table.table(), timecolname, 1, ScatterTrace.YAxis.Y2, new TableViewer.GraphTypes(col));
        }
        return tv;
    }

}
