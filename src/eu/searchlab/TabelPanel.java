/**
 *  TabelPanel
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

package eu.searchlab;

import eu.searchlab.storage.table.IndexedTable;
import eu.searchlab.storage.table.PersistentTables;
import tech.tablesaw.api.StringColumn;
import tech.tablesaw.api.Table;

/**
 * A TablePanel is a static class which holds data available to servlets and other methods.
 *
 *
 */
public class TabelPanel {

    public static PersistentTables tables;

    static {
        tables = new PersistentTables();

        Table testTable = Table.create(StringColumn.create("a"), StringColumn.create("b"), StringColumn.create("c"));
        tables.addTable("test", new IndexedTable(testTable));
    }

}
