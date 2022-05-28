/**
 *  AuditScheduler
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

package eu.searchlab.audit;

public class AuditScheduler extends Thread implements Runnable {

    private final long time;
    private boolean shallrun;
    private final AuditTask[] auditTasks;
    private final Thread[] auditThreads;
    private boolean runConcurrently;

    public AuditScheduler(final long time, final AuditTask... auditTasks) {
        this.time = time;
        this.shallrun = true;
        this.auditTasks = auditTasks;
        this.auditThreads = new Thread[this.auditTasks.length];
        this.runConcurrently = false;
    }

    @Override
    public void run() {
        while (this.shallrun) {
            try {
                // to schedule tasks in exact cycles, we remember the time to start this
                final long start = System.currentTimeMillis();

                // run all tasks
                for (int i = 0; i < this.auditTasks.length; i++) {
                    final AuditTask auditTask = this.auditTasks[i];
                    if (this.runConcurrently) {
                        // we run the tasks only concurrently if they take up too much time.
                        // but this is dangerous, because we could start them
                        if (this.auditThreads[i] != null && this.auditThreads[i].isAlive()) {
                            this.auditThreads[i].join();
                        }
                        this.auditThreads[i] = new Thread() {
                            @Override
                            public void run() {
                                auditTask.check();
                            }
                        };
                        this.auditThreads[i].start();
                    } else {
                        // we could run this concurrently, but we would not win anything
                        // becasue we measure the time this takes.
                        // therefore we run this in a sequence to keep load low. For now.
                        auditTask.check();
                    }
                }

                // find out how long we still have to wait to reach the next cycle
                final long remaining = this.time - System.currentTimeMillis() + start;
                if (remaining < 0) {
                    // Tasks take up too much time, run them concurrently next time
                    this.runConcurrently = true;
                    // do not sleep!
                } else {
                    // sleep remaining time
                    Thread.sleep(remaining);
                }
            } catch (final InterruptedException e) {
                this.shallrun = false;
            }
        }
    }

    public void close() {
        this.shallrun = false;
    }
}
