/**
 *  TimeSeriesTable
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

import java.util.ArrayList;
import java.util.List;

import tech.tablesaw.api.DateTimeColumn;
import tech.tablesaw.api.LongColumn;
import tech.tablesaw.api.Table;
import tech.tablesaw.columns.Column;

public class TimeSeriesTable extends IndexedTable {

    public final static String DATE_COL_NAME = "date";
    public final static String TIME_COL_NAME = "time";

    protected final String date_col_name, time_col_name;
    protected DateTimeColumn dateCol;
    protected LongColumn timeCol;
    protected Column<?>[] xtraCols;

    /**
     * create an empty clone of a given time series table
     * @param t
     */
    protected TimeSeriesTable(TimeSeriesTable t) {
        super(t.table.emptyCopy());
        this.date_col_name = t.date_col_name;
        this.time_col_name = t.time_col_name;
        this.dateCol = this.table.dateTimeColumn(this.date_col_name);
        this.timeCol = this.table.longColumn(this.time_col_name);
        final List<Column<?>> xc = new ArrayList<>();
        for (final Column<?>c : t.table.columnArray()) {
            final String cn = c.name();
            if (!cn.equals(t.date_col_name) && !cn.equals(t.time_col_name)) xc.add(c);
        }
        this.xtraCols = xc.toArray(new Column<?>[xc.size()]);
    }

    public TimeSeriesTable(final String date_col_name, final String time_col_name, Column<?>... xtraCols) {
        super(Table.create().addColumns(DateTimeColumn.create(date_col_name), LongColumn.create(time_col_name)));
        this.date_col_name = date_col_name;
        this.time_col_name = time_col_name;
        this.dateCol = this.table.dateTimeColumn(date_col_name);
        this.timeCol = this.table.longColumn(time_col_name);
        for (final Column<?> xtraCol: xtraCols) {
            assert xtraCol.isEmpty(); // xtraCols must all be empty
            this.table.addColumns(xtraCol);
        }
        this.xtraCols = xtraCols;
    }

    public TimeSeriesTable(Column<?>... xtraCols) {
        this(DATE_COL_NAME, TIME_COL_NAME, xtraCols);
    }

    public String getDateColName() {
        return this.date_col_name;
    }

    public String getTimeColName() {
        return this.time_col_name;
    }

    /**
     * create a new TimeSeriesTable where all entries that are before the
     * given time is missing
     * @param time milliseconds since epoch
     * @return a new table with a subset of the entries according to constraint
     */
    public TimeSeriesTable deleteBeforeTime(long time) {
        final TimeSeriesTable t =  new TimeSeriesTable(this); // create empty clone
        for (int i = 0; i < this.size(); i++) {
            if (this.timeCol.getLong(i) >= time) t.addRow(this.table.row(i));
        }
        return t;
    }
}
