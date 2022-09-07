/**
 *  FrequencyScheduler
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

import java.util.ArrayList;
import java.util.List;

public class FrequencyScheduler {

    private final List<FrequencySchedulerLoop> loops;


    public FrequencyScheduler() {
        this.loops = new ArrayList<>();
    }

    public void addJob(final FrequencyTask auditTask, final long time) {
        final FrequencySchedulerLoop loop = new FrequencySchedulerLoop(time, auditTask);
        this.loops.add(loop);
        loop.start();
    }

    public void shutdown() {
        for (final FrequencySchedulerLoop loop: this.loops) {
            loop.shutdown();
        }
    }

    public static void main(final String[] args) {
        final FrequencyTask task1 = new FrequencyTask() {
            @Override
            public void check() {System.out.println("task 1");}
        };
        final FrequencyTask task2 = new FrequencyTask() {
            @Override
            public void check() {System.out.println("task 2");}
        };
        final FrequencyTask task3 = new FrequencyTask() {
            @Override
            public void check() {System.out.println("task 3");}
        };
        final FrequencyScheduler cron = new FrequencyScheduler();
        cron.addJob(task1, 2000);
        cron.addJob(task2, 3000);
        cron.addJob(task3, 4000);

        System.out.println("waiting 10 seconds until shutdown");
        try {Thread.sleep(10000);} catch (final InterruptedException e) {}
        System.out.println("shutdown");
        cron.shutdown();
    }

}
