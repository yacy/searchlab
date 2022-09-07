/**
 *  FrequencySchedulerLoop
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

public class FrequencySchedulerLoop extends Thread implements Runnable {

    private final long time;
    private boolean shallrun;
    private final FrequencyTask auditTask;
    protected Thread auditThread;
    private boolean runConcurrently;

    public FrequencySchedulerLoop(final long time, final FrequencyTask auditTask) {
        this.time = time;
        this.shallrun = true;
        this.auditTask = auditTask;
        this.auditThread = null;
        this.runConcurrently = false;
    }

    public void shutdown() {
        this.shallrun = false;
        if (this.auditThread != null && this.auditThread.isAlive()) this.auditThread.interrupt();
    }

    @Override
    public void run() {
        taskrunner: while (this.shallrun) {
            // to schedule tasks in exact cycles, we remember the time to start this
            final long start = System.currentTimeMillis();

            // run all tasks
            final FrequencyTask auditTask = this.auditTask;
            if (this.runConcurrently) {
                // we run the tasks only concurrently if they take up too much time.
                // but this is dangerous, because we could start them
                if (this.auditThread != null && this.auditThread.isAlive()) {
                    try {
                        this.auditThread.join();
                    } catch (final InterruptedException e) {
                        this.shallrun = false;
                        break taskrunner;
                    }
                }
                this.auditThread = new Thread() {
                    @Override
                    public void run() {
                        auditTask.check();
                    }
                };
                this.auditThread.start();
            } else {
                // we could run this concurrently, but we would not win anything
                // becasue we measure the time this takes.
                // therefore we run this in a sequence to keep load low. For now.
                auditTask.check();
            }

            // find out how long we still have to wait to reach the next cycle
            final long remaining = this.time - System.currentTimeMillis() + start;
            if (remaining < this.time / 10) {
                // not much time left, run concurrently in the future
                this.runConcurrently = true;
            }
            if (remaining > 0) {
                // sleep remaining time - only if there is time left
                try {
                    Thread.sleep(remaining);
                } catch (final InterruptedException e) {
                    this.shallrun = false;
                    break taskrunner;
                }
            }
        }
    }

}
