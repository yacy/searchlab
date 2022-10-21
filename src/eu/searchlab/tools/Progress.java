/**
 *  Progress
 *  Copyright 21.10.2022 by Michael Peter Christen, @orbiterlab
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

import java.util.concurrent.Callable;

/**
 * A progress is a process where we actually know the computational
 * result of the future in advance. We also know that it will take some time
 * until the result is computed. We also want to know how much progress we made
 * meanthile to reach the expected outcome. Therefore we need a class interface
 * which describes how to obtain a result status while it is computed.
 *
 * Expected use of this interface is in the implementation of a progress bar.
 * @param <T> most certainly a numeric cardinal, like a long
 */
public interface Progress<T extends Number> extends Callable<T>, Comparable<Progress<T>> {

    /**
     * get the expected target value for the future result
     * @return the target of the computation
     */
    public T getTarget();

    /**
     * get the progress of the computation by computation of the current value of the progress
     * @return the actuall progress, mostly a count (like byte transferred etc.)
     */
    public T getProgress();

    /**
     * compute the progress as percent, maximum is 100
     * @return percentage of completion of the computation
     */
    public int getPercent();

    /**
     * get the time when the computation has started in milliseconds since epoch
     * @return the start time
     */
    public long getStartTime();

    /**
     * get the number of progress steps per second at this time
     * @return the average progress delta per second
     */
    public double getProgressPerSecond();

    /**
     * get the remaining time for the computation in milliseconds since epoch
     * @return the start time
     */
    public long getRemainingTime();

    /**
     * get the time when the computation will probably end in milliseconds since epoch
     * @return the start time
     */
    public long getTargetTime();

}
