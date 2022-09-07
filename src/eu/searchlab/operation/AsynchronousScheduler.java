/**
 *  AsynchronousScheduler
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

import java.util.ArrayList;
import java.util.List;

import eu.searchlab.tools.Logger;

public class AsynchronousScheduler {

    private final List<AsynchronousSchedulerLoop> loops;

    public AsynchronousScheduler() {
        this.loops = new ArrayList<>();
    }

    public void addJob(final AsynchronousJob application, final long cycleLen, final int randomAddon) {
        Logger.info("Added CronEngine Cronjob with cycleLen = " + AsynchronousSchedulerLoop.times(cycleLen) + ", randomAddon = " + AsynchronousSchedulerLoop.times(randomAddon));
        final AsynchronousSchedulerLoop cronjobControlLoop = new AsynchronousSchedulerLoop(application, cycleLen, randomAddon);
        this.loops.add(cronjobControlLoop);
        cronjobControlLoop.startup();
    }

    public void shutdown() {
        for (final AsynchronousSchedulerLoop loop: this.loops) {
            loop.shutdown();
        }
    }

    public static void main(final String[] args) {
        final AsynchronousJob job1 = new AsynchronousJob() {
            @Override
            public void run() {System.out.println("job 1 run");}
            @Override
            public void stop() {System.out.println("job 1 stop");}
        };
        final AsynchronousJob job2 = new AsynchronousJob() {
            @Override
            public void run() {System.out.println("job 2 run");}
            @Override
            public void stop() {System.out.println("job 2 stop");}
        };
        final AsynchronousJob job3 = new AsynchronousJob() {
            @Override
            public void run() {System.out.println("job 3 run");}
            @Override
            public void stop() {System.out.println("job 3 stop");}
        };
        final AsynchronousScheduler cron = new AsynchronousScheduler();
        cron.addJob(job1, 2000, 700);
        cron.addJob(job2, 3000, 200);
        cron.addJob(job3, 4000, 1000);

        System.out.println("waiting 10 seconds until shutdown");
        try {Thread.sleep(10000);} catch (final InterruptedException e) {}
        System.out.println("shutdown");
        cron.shutdown();
    }

}
