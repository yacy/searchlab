/**
 *  FrequencyTask
 *  Copyright 28.05.2022 by Michael Peter Christen, @orbiterlab
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

package eu.searchlab.operation;

/**
 * An AuditTask is a task which is executed in exact time slices
 * independently from the runtime of the task.
 * If the task takes longer than the defined time slice length, it is
 * started concurrently further on to ensure that it always runs in the correct slice
 * timeframe.
 */
public interface FrequencyTask {

    /**
     * the check method is called every time an audit taks shall perform a timed action
     */
    public void check();

}
