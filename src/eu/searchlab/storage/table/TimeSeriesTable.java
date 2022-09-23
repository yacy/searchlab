/**
 *  TimeSeriesTable
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

public interface TimeSeriesTable {

    public int size();

    public String[] getView(final int row);

    public String[] getMeta(final int row);

    public String[] getMetaWhere(final String[] view);

    public double[] getDouble(final int row);

    public long[] getLong(final int row);

    public void dropRowsWithMissingValues();

    @Override
    public String toString();

    public String printAll();

    public TableViewer getGraph(final String filename, final String title, final String xscalename, final String timecolname, final String[] yscalecols, final String[] y2scalecols);
}
