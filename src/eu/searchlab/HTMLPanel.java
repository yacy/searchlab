/**
 *  HTMLPanel
 *  Copyright 14.10.2021 by Michael Peter Christen, @orbiterlab
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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import eu.searchlab.storage.table.TableViewer;

public class HTMLPanel {

    private final Map<String, String> htmls;
    private final int width, height;

    /**
     * create a HTML Panel with a given rendering size
     * @param width
     * @param height
     */
    public HTMLPanel(final int width, final int height) {
        this.htmls = new ConcurrentHashMap<>();
        this.width = width;
        this.height = height;
    }

    /**
     * Add a table viewer to the html panel
     * The table will be stored in a pre-compiled html version
     * @param name
     * @param tv
     */
    public void put(final String name, final TableViewer tv) {
        this.htmls.put(name, tv.render2html(this.width, this.height, true));
    }

    public boolean has(final String name) {
        return this.htmls.containsKey(name);
    }

    public String get(final String name) {
        return this.htmls.get(name);
    }

}
