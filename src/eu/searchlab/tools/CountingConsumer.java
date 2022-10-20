/**
 *  CountingConsumer
 *  Copyright 20.10.2022 by Michael Peter Christen, @orbiterlab
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

package eu.searchlab.tools;

import java.util.function.Consumer;

public interface CountingConsumer<T> extends Consumer<T> {

    /**
     * increment the count: this shall be called once within the consumers accept implementation.
     */
    public void incCount();

    /**
     * get the number of accept operations
     * @return the number of accept operations
     */
    public long getCount();
}
