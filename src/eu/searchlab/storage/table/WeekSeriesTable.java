/**
 *  WeekSeriesTable
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoField;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import tech.tablesaw.api.DateTimeColumn;
import tech.tablesaw.api.DoubleColumn;
import tech.tablesaw.api.LongColumn;
import tech.tablesaw.api.StringColumn;
import tech.tablesaw.api.Table;
import tech.tablesaw.plotly.traces.ScatterTrace;

public class WeekSeriesTable {

    public IndexedTable table;

    public DateTimeColumn tscwDateCol;
    public LongColumn tscwTimeCol;
    public LongColumn tscwYearCol;
    public LongColumn tscwWeekCol;
    public StringColumn tscwSYKWCol;
    public StringColumn tscwCALDCol;
    public StringColumn tscwPRNTCol;
    public StringColumn[] viewCols;
    public StringColumn[] metaCols;
    public DoubleColumn[] dataCols;
    private double[] zeroData;
    private Map<String, Integer> dataColNameIndex;

    public final static String TSCW_DATE = "tscw.date";
    public final static String TSCW_TIME = "tscw.time";
    public final static String TSCW_YEAR = "tscw.year";
    public final static String TSCW_WEEK = "tscw.week";
    public final static String TSCW_SYKW = "tscw.sykw"; // SAP Kalenderwoche
    public final static String TSCW_CALD = "tscw.cald"; // LocalDate format yyyy-mm-dd
    public final static String TSCW_PRNT = "tscw.prnt"; // Print format yyyy "KW"kw, dd.mm-dd.mm

    private WeekSeriesTable() {
        this.tscwDateCol = DateTimeColumn.create(TSCW_DATE);
        this.tscwTimeCol = LongColumn.create(TSCW_TIME);
        this.tscwYearCol = LongColumn.create(TSCW_YEAR);
        this.tscwWeekCol = LongColumn.create(TSCW_WEEK);
        this.tscwSYKWCol = StringColumn.create(TSCW_SYKW);
        this.tscwCALDCol = StringColumn.create(TSCW_CALD);
        this.tscwPRNTCol = StringColumn.create(TSCW_PRNT);
        this.dataColNameIndex = new ConcurrentHashMap<>();
    }

    private void initZeroes(final int len) {
        this.zeroData = new double[len];
        for (int i = 0; i < this.zeroData.length; i++) {
            this.zeroData[i] = 0.0d;
        }
    }

    /**
     * Kalenderwochen - Zeitserie erzeugen
     * @param viewCols views sind potentielle Suchfacetten im Datensatz zur Eingrenzung auf Teil-Zeitserien. Um eine Zeitserie zu erhalten, müssen alle Facetten fixiert werden.
     * @param metaCols metas sind Kontextinformationen zum Datensatz
     * @param dataCols datas sind Nutzwert der Zeitserie um einen Datenwert darstellen zu können.
     */
    public WeekSeriesTable(final StringColumn[] viewCols, final StringColumn[] metaCols, final DoubleColumn[] dataCols) {
        this();
        this.viewCols = viewCols;
        this.metaCols = metaCols;
        this.dataCols = dataCols;
        final Table t = Table.create()
                .addColumns(this.tscwDateCol, this.tscwTimeCol, this.tscwYearCol, this.tscwWeekCol, this.tscwSYKWCol, this.tscwCALDCol, this.tscwPRNTCol)
                .addColumns(this.viewCols)
                .addColumns(this.metaCols);
        for (int i = 0; i < this.dataCols.length; i++) {
            t.addColumns(this.dataCols[i]);
            this.dataColNameIndex.put(this.dataCols[i].name(), i);
        }
        this.table = new IndexedTable(t);
        initZeroes(dataCols.length);
    }

    /**
     * Kalenderwochen - Zeitserie erzeugen
     * @param viewCols views sind potentielle Suchfacetten im Datensatz zur Eingrenzung auf Teil-Zeitserien. Um eine Zeitserie zu erhalten, müssen alle Facetten fixiert werden.
     * @param metaCols metas sind Kontextinformationen zum Datensatz
     * @param dataCols datas sind Nutzwert der Zeitserie um einen Datenwert darstellen zu können.
     */
    public WeekSeriesTable(final String[] viewColNames, final String[] metaColNames, final String[] dataColNames) {
        this();
        this.viewCols = new StringColumn[viewColNames.length];
        for (int i = 0; i < viewColNames.length; i++) this.viewCols[i] = StringColumn.create(viewColNames[i]);
        this.metaCols = new StringColumn[metaColNames.length];
        for (int i = 0; i < metaColNames.length; i++) this.metaCols[i] = StringColumn.create(metaColNames[i]);
        this.dataCols = new DoubleColumn[dataColNames.length];
        for (int i = 0; i < dataColNames.length; i++) {
            this.dataCols[i] = DoubleColumn.create(dataColNames[i]);
            this.dataColNameIndex.put(dataColNames[i], i);
        }
        final Table t = Table.create()
                .addColumns(this.tscwDateCol, this.tscwTimeCol, this.tscwYearCol, this.tscwWeekCol, this.tscwSYKWCol, this.tscwCALDCol, this.tscwPRNTCol)
                .addColumns(this.viewCols)
                .addColumns(this.metaCols);
        for (int i = 0; i < this.dataCols.length; i++) {
            t.addColumns(this.dataCols[i]);
        }
        this.table = new IndexedTable(t);
        initZeroes(this.dataCols.length);
    }

    /**
     * Aus einer Indexed Table eine Zeitserie erzeugen. Dabei müssen folgende Eigenschaften der Tabelle bestehen:
     * - Es muss die long-Felder TSCW_TIME, TSCW_YEAR, TSCW_WEEK, tscwSYKWCol und tscwCALDCol haben,
     * - Weitere Tabellennamen müssen Prefixe "view", "meta", "data" und "unit" haben.
     * @param table
     */
    public WeekSeriesTable(final IndexedTable table) {
        this.table = table;
        this.tscwTimeCol = this.table.table().longColumn(TSCW_TIME);
        this.tscwYearCol = this.table.table().longColumn(TSCW_YEAR);
        this.tscwWeekCol = this.table.table().longColumn(TSCW_WEEK);
        this.tscwSYKWCol = this.table.table().stringColumn(TSCW_SYKW);
        this.tscwCALDCol = this.table.table().stringColumn(TSCW_CALD);
        this.tscwPRNTCol = this.table.table().stringColumn(TSCW_PRNT);
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
        for (int i = 0; i < this.tscwYearCol.size(); i++) {
            final long j = this.tscwYearCol.getLong(i);
            if (j >= jahr) {
                final Table t = this.table.table().emptyCopy();
                for (int k = i; k < this.table.rowCount(); k++) {
                    t.addRow(this.table.row(k));
                }
                this.tscwDateCol = t.dateTimeColumn(TSCW_DATE);
                this.tscwTimeCol = t.longColumn(TSCW_TIME);
                this.tscwYearCol = t.longColumn(TSCW_YEAR);
                this.tscwWeekCol = t.longColumn(TSCW_WEEK);
                this.tscwSYKWCol = t.stringColumn(TSCW_SYKW);
                this.tscwCALDCol = t.stringColumn(TSCW_CALD);
                this.tscwPRNTCol = t.stringColumn(TSCW_PRNT);
                for (int k = 0; k < this.viewCols.length; k++) this.viewCols[k] = t.stringColumn(this.viewCols[k].name());
                for (int k = 0; k < this.metaCols.length; k++) this.metaCols[k] = t.stringColumn(this.metaCols[k].name());
                for (int k = 0; k < this.dataCols.length; k++) this.dataCols[k] = t.doubleColumn(this.dataCols[k].name());
                this.table = new IndexedTable(t);
                return;
            }
        }
    }

    public void init(final String[] view, final String[] meta, final int jahrVonInkl, final int jahrBisExkl) {
        for (int jahr = jahrVonInkl; jahr < jahrBisExkl; jahr++) {
            for (int woche = 1; woche <= CalendarWeek.weekNumberPerYear(jahr); woche++) {
                addValues(jahr, woche, view, meta, this.zeroData);
            }
        }
    }

    public void addValues(final long year, final long week, final String[] view, final String[] meta, final double[] data) {
        assert view.length == this.viewCols.length : "neue view.length = " + view.length + ", bestehende view.length = " + this.viewCols.length;
        assert meta.length == this.metaCols.length : "neue meta.length = " + meta.length + ", bestehende meta.length = " + this.metaCols.length;
        assert data.length == this.dataCols.length : "neue data.length = " + data.length + ", bestehende data.length = " + this.dataCols.length;
        final CalendarWeek kw = new CalendarWeek((int) year, (int) week);
        final long time = kw.getTime();
        final LocalDate firstDay = kw.getFirstDayOfWeek();
        final LocalDate lastDay = kw.getLastDayOfWeek();
        this.tscwDateCol.appendObj(LocalDateTime.ofEpochSecond(time / 1000, 0, ZoneOffset.ofHours(0)));
        this.tscwTimeCol.append(time);
        this.tscwYearCol.append(year);
        this.tscwWeekCol.append(week);
        this.tscwSYKWCol.append(kw.getYYYYWW());
        this.tscwCALDCol.append(firstDay.toString()); // ISO-8601 format yyy-mm-dd
        this.tscwPRNTCol.append(kw.getYear() + " KW" + kw.getWW() + " " + firstDay.get(ChronoField.DAY_OF_MONTH) + "." + firstDay.get(ChronoField.MONTH_OF_YEAR) + ".-" + lastDay.get(ChronoField.DAY_OF_MONTH) + "." + lastDay.get(ChronoField.MONTH_OF_YEAR) + "."); // Print format yyyy "KW"kw, dd.mm.-dd.mm.
        for (int i = 0; i < view.length; i++) this.viewCols[i].append(view[i]);
        for (int i = 0; i < meta.length; i++) this.metaCols[i].append(meta[i]);
        for (int i = 0; i < data.length; i++) this.dataCols[i].append(data[i]);
    }

    public void append(final WeekSeriesTable t) {
        this.table.append(t.table);
    }

    public int getIndex(final long year, final long week, final String[] view) {
        assert view == null || view.length == this.viewCols.length : "neue view.length = " + view.length + ", bestehende view.length = " + this.viewCols.length;
        search: for (int idx = 0; idx < this.table.rowCount(); idx++) {
            // try to match with time constraints
            if (this.tscwYearCol.get(idx) != year || this.tscwWeekCol.get(idx) != week) continue;

            // try to match with view constraints
            if (view != null) for (int v = 0; v < view.length; v++) {
                if (!this.viewCols[v].get(idx).equals(view[v])) continue search;
            }

            return idx;
        }
        return -1;
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
    public void setValuesWhere(final int year, final int week, final String[] view, final String[] meta, final double[] data) {
        assert view.length == this.viewCols.length : "neue view.length = " + view.length + ", bestehende view.length = " + this.viewCols.length;
        assert meta.length == this.metaCols.length : "neue meta.length = " + meta.length + ", bestehende meta.length = " + this.metaCols.length;
        assert data.length == this.dataCols.length : "neue data.length = " + data.length + ", bestehende data.length = " + this.dataCols.length;
        final int idx = getIndex(year, week, view);
        if (idx >= 0) {
            // overwrite values and finish. We consider that this hit is unique
            for (int i = 0; i < meta.length; i++) this.metaCols[i].set(idx, meta[i]);
            for (int i = 0; i < data.length; i++) this.dataCols[i].set(idx, data[i]);
            return;
        }
    }

    public double[] getValues(final int year, final int week, final String[] view) {
        assert view == null || view.length == this.viewCols.length : "neue view.length = " + view.length + ", bestehende view.length = " + this.viewCols.length;

        final int idx = getIndex(year, week, view);
        if (idx >= 0) {
            return getDouble(idx);
        }
        return null;
    }

    public WeekData getWeekData(final long year, final long week) {
        final int idx = getIndex(year, week, null);
        if (idx < 0) return null;
        return new WeekData(idx);
    }

    /**
     * WeekData
     * is a "row" in a WeekSeriesTable
     */
    public class WeekData {

        public final String[] view;
        public final String[] meta;
        public final double[] data;

        /**
         * get row by given date
         * @param year
         * @param week
         */
        public WeekData(final long year, final long week) {
            this(getIndex(year, week, null));
        }

        /**
         * get a row by given index
         * @param idx
         */
        public WeekData(final int idx) {
            this.view = new String[WeekSeriesTable.this.viewCols.length];
            this.meta = new String[WeekSeriesTable.this.metaCols.length];
            this.data = new double[WeekSeriesTable.this.dataCols.length];
            for (int i = 0; i < this.view.length; i++) this.view[i] = WeekSeriesTable.this.viewCols[i].get(idx);
            for (int i = 0; i < this.meta.length; i++) this.meta[i] = WeekSeriesTable.this.metaCols[i].get(idx);
            for (int i = 0; i < this.data.length; i++) this.data[i] = WeekSeriesTable.this.dataCols[i].get(idx); // NPE??
        }

        public double getData(final String colname) {
            return this.data[WeekSeriesTable.this.dataColNameIndex.get(colname)];
        }
    }

    public long getYear(final int row) {
        return this.table.longColumn(TSCW_YEAR).get(row);
    }

    public long getWeek(final int row) {
        return this.table.longColumn(TSCW_WEEK).get(row);
    }

    public LocalDate getFirstDate() {
        final long year = getYear(0);
        final long week = getWeek(0);
        return new CalendarWeek((int) year, (int) week).getFirstDayOfWeek();
    }

    public LocalDate getLastDate() {
        return getLastWeek().getFirstDayOfWeek();
    }

    public CalendarWeek getLastWeek() {
        final long year = getYear(this.table.size() - 1);
        final long week = getWeek(this.table.size() - 1);
        return new CalendarWeek((int) year, (int) week);
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

    public void addValuesOverWeek(final int year, final int week, final String[] view, final String[] meta, final double[] data) {
        assert view.length == this.viewCols.length : "neue view.length = " + view.length + ", bestehende view.length = " + this.viewCols.length;
        assert meta.length == this.metaCols.length : "neue meta.length = " + meta.length + ", bestehende meta.length = " + this.metaCols.length;
        assert data.length == this.dataCols.length : "neue data.length = " + data.length + ", bestehende data.length = " + this.dataCols.length;
        assert data.length == this.dataCols.length;
        //final long stop = start + weekLengthMilliseconds - 1;
        addValues(year, week, view, meta, data);
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
        expandGraph(tv, this.table.table, timecolname, yscalecols, y2scalecols);
        return tv;
    }

    public static void expandGraph(final TableViewer tv, final Table table, final String timecolname, final String[] yscalecols, final String[] y2scalecols) {
        for (final String col: yscalecols) {
            tv.timeseries(table, timecolname, 2, ScatterTrace.YAxis.Y, new TableViewer.GraphTypes(col));
        }
        for (final String col: y2scalecols) {
            tv.timeseries(table, timecolname, 1, ScatterTrace.YAxis.Y2, new TableViewer.GraphTypes(col));
        }
    }

}
