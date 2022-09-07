/**
 *  AsynchronousSchedulerLoop
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

import java.util.Random;

import eu.searchlab.tools.Logger;

public class AsynchronousSchedulerLoop implements Runnable  {

    private final static Random random = new Random(System.currentTimeMillis());


    public final long cycleLen;
    public final int randomAddon;
    public final AsynchronousJob application;
    public Thread cronjobThreadInstance;
    public Thread loopThread;
    private boolean shallrun;

    public AsynchronousSchedulerLoop(final AsynchronousJob application, final long cycleLen, final int randomAddon) {
        this.application = application;
        this.cycleLen = cycleLen;
        this.randomAddon = randomAddon;
        this.cronjobThreadInstance = null;
        this.loopThread = null;
        this.shallrun = true;
    }

    public void startup() {
        this.loopThread = new Thread(this);
        this.loopThread.start();
    }

    public void shutdown() {
        this.shallrun = false;
        this.loopThread.interrupt();
    }

    @Override
    public void run() {
        Logger.info("Started CronCycle");
        int cycleCount = 0;

        // we first run a fresh instance of the cronjob before starting a cycle
        this.cronjobThreadInstance = new Thread(this.application);
        this.cronjobThreadInstance.start();

        // this loop is suppose to run forever until a kill event happens
        int randomInc = random.nextInt(this.randomAddon);
        long sleep = this.cycleLen + randomInc;
        Logger.info("Running cycle " + cycleCount + ", sleep = " + times(sleep) + ", including random: " + times(randomInc));

        while (this.shallrun) {
            try {
                Thread.sleep(sleep);
            } catch (final InterruptedException e) {
                Logger.info("job interrupted - 1");
            } finally {
                // after that long sleep time, the application must be stopped and restarted
                Logger.info("Stopping cycle " + cycleCount);
                this.application.stop();
                while (this.cronjobThreadInstance.isAlive()) {
                    try {
                        Thread.sleep(1000);
                    } catch (final InterruptedException e) {
                        Logger.info("job interrupted - 2");
                    }
                }
            }

            if (this.shallrun) {
                // restart
                this.cronjobThreadInstance = new Thread(this.application);
                this.cronjobThreadInstance.start();

                // calculate new sleep target
                cycleCount++;
                randomInc = random.nextInt(this.randomAddon);
                sleep = this.cycleLen + randomInc;
                Logger.info("Running cycle " + cycleCount + ", sleep = " + times(sleep) + ", including random: " + times(randomInc));
            }
        }
    }

    protected final static String times(long t) {
        if (t < 1000) return t + " milliseconds";
        t = t / 1000;
        if (t < 60) return t + " seconds";
        t = t / 60;
        if (t < 60) return t + " minutes";
        t = t / 60;
        if (t < 24) return t + " hours";
        t = t / 24;
        return t + " days";
    }
}
