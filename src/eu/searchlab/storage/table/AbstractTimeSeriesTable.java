/**
 *  AbstractTimeSeriesTable
 *  Copyright 23.09.2022 by Michael Peter Christen, @orbiterlab
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

import eu.searchlab.storage.table.TableViewer.GraphTypes;
import tech.tablesaw.api.DoubleColumn;
import tech.tablesaw.api.LongColumn;
import tech.tablesaw.api.StringColumn;
import tech.tablesaw.api.Table;
import tech.tablesaw.columns.Column;
import tech.tablesaw.plotly.traces.ScatterTrace;

public class AbstractTimeSeriesTable implements TimeSeriesTable {

    public IndexedTable table;

    public StringColumn[] viewCols;
    public StringColumn[] metaCols;
    public Column<?>[] dataCols;


    @Override
    public int size() {
        return this.table.rowCount();
    }

    @Override
    public String[] getView(final int row) {
        final String[] s = new String[this.viewCols.length];
        int c = 0;
        for (int i = 0; i < this.viewCols.length; i++) {
            s[c] = ((StringColumn) this.viewCols[i]).getString(row);
            c++;
        }
        return s;
    }

    @Override
    public String[] getMeta(final int row) {
        final String[] s = new String[this.metaCols.length];
        int c = 0;
        for (int i = 0; i < this.metaCols.length; i++) {
            s[c++] = ((StringColumn) this.metaCols[i]).getString(row);
        }
        return s;
    }

    @Override
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

    @Override
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

    @Override
    public long[] getLong(final int row) {
        final long[] d = new long[this.dataCols.length];
        int c = 0;
        for (int i = 0; i < this.dataCols.length; i++) {
            d[c] = ((LongColumn) this.dataCols[i]).getLong(row);
            c++;
        }
        return d;
    }

    @Override
    public void dropRowsWithMissingValues() {
        this.table.dropRowsWithMissingValues();
    }

    @Override
    public String toString() {
        return this.table.toString();
    }

    @Override
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
    @Override
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
