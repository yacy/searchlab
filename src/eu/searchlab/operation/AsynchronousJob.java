/**
 *  AsynchronousJob
 *  Copyright 07.09.2022 by Michael Peter Christen, @orbiterlab
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
 * An CronJob is a task which is executed in sequence and can be stopped while
 * it is still running. It is also not expected that the task will ever end by
 * itself. However if that happens that will not change the expected runtime.
 * When the runtime is due, the job is startet again.
 *
 * The expected runtime is randomized within a certain time window. This shall
 * ensure that several running processes (i.e. in their own docker environment)
 * are running in the same frequency.
 *
 * The concept of randomized runtime shall prevent high IO that is produced by
 * processes which are running synchronously.
 */
public interface AsynchronousJob extends Runnable {

    public void stop();

}