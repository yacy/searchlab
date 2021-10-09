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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import tech.tablesaw.api.LongColumn;
import tech.tablesaw.api.StringColumn;
import tech.tablesaw.columns.Column;

public class DaySeriesTable extends TimeSeriesTable {

    public final static String DATES_COL_NAME = "dates";
    public final static String YEAR_COL_NAME  = "year";
    public final static String MONTH_COL_NAME = "month";
    public final static String DAY_COL_NAME   = "day";

    public final static SimpleDateFormat dayparser = new SimpleDateFormat("yyyy-mm-dd");
    public final static long daylength = 1000 * 60 * 60 * 24; // millisenconds of a day

    protected final String dates_col_name, year_col_name, month_col_name, day_col_name;
    public StringColumn datesCol;
    public LongColumn yearCol;
    public LongColumn monthCol;
    public LongColumn dayCol;

    private static Column<?>[] xtraColsA(
            final String dates_col_name,
            final String year_col_name, final String month_col_name, final String day_col_name,
            Column<?>... xtraCols) {
        final List<Column<?>> c = new ArrayList<>();
        c.add(StringColumn.create(dates_col_name));
        c.add(LongColumn.create(year_col_name));
        c.add(LongColumn.create(month_col_name));
        c.add(LongColumn.create(day_col_name));
        for (final Column<?> x: xtraCols) c.add(x);
        return c.toArray(new Column<?>[c.size()]);
    }

    public DaySeriesTable(DaySeriesTable t) {
        super(t);
        this.dates_col_name = t.dates_col_name;
        this.year_col_name = t.year_col_name;
        this.month_col_name = t.month_col_name;
        this.day_col_name = t.day_col_name;
        this.datesCol = this.table.stringColumn(this.dates_col_name);
        this.yearCol = this.table.longColumn(this.year_col_name);
        this.monthCol = this.table.longColumn(this.month_col_name);
        this.dayCol = this.table.longColumn(this.day_col_name);
    }

    public DaySeriesTable(
            final String date_col_name, final String time_col_name,
            final String dates_col_name,
            final String year_col_name, final String month_col_name, final String day_col_name,
            Column<?>... xtraCols) {
        super(date_col_name, time_col_name, xtraColsA(dates_col_name, year_col_name, month_col_name, day_col_name, xtraCols));
        this.dates_col_name = dates_col_name;
        this.year_col_name = year_col_name;
        this.month_col_name = month_col_name;
        this.day_col_name = day_col_name;
        this.datesCol = this.table.stringColumn(this.dates_col_name);
        this.yearCol = this.table.longColumn(this.year_col_name);
        this.monthCol = this.table.longColumn(this.month_col_name);
        this.dayCol = this.table.longColumn(this.day_col_name);
    }

    public DaySeriesTable(Column<?>... xtraCols) {
        this(
                DATE_COL_NAME, TIME_COL_NAME,
                DATES_COL_NAME, YEAR_COL_NAME, MONTH_COL_NAME, DAY_COL_NAME,
                xtraCols);
    }

    /**
     * create a new DaySeriesTable where all entries that are before the
     * given year is missing
     * @param year
     * @return a new table with a subset of the entries according to constraint
     */
    public DaySeriesTable deleteBeforeYear(int year) {
        final long l = time(year, 1, 1);
        final DaySeriesTable t =  new DaySeriesTable(this); // create empty clone
        for (int i = 0; i < this.size(); i++) {
            if (this.timeCol.getLong(i) >= l) t.addRow(this.table.row(i));
        }
        return t;
    }

    public static long time(int year, int month, int day) {
        long time = 0; try { final Date d = dayparser.parse(year  + "-" + month + "-" + day); time = d.getTime(); } catch (final ParseException e) {}
        return time;
    }

}
