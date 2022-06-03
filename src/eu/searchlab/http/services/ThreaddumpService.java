/**
 *  ThreaddumpService
 *  Copyright 05.05.2022 by Michael Peter Christen, @orbiterlab
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

package eu.searchlab.http.services;

import java.io.IOException;
import java.lang.Thread.State;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.text.DecimalFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.json.JSONObject;

import eu.searchlab.http.Service;
import eu.searchlab.http.ServiceResponse;
import eu.searchlab.tools.Memory;

public class ThreaddumpService extends AbstractService implements Service {

    private static final long startupTime = System.currentTimeMillis();
    private static final String multiDumpFilter = ".*((java.net.DatagramSocket.receive)|(java.lang.Thread.getAllStackTraces)|(java.net.SocketInputStream.read)|(java.net.ServerSocket.accept)|(java.net.Socket.connect)).*";
    private static final Pattern multiDumpFilterPattern = Pattern.compile(multiDumpFilter);

    private static final Thread.State[] ORDERED_STATES = new Thread.State[]{
        Thread.State.BLOCKED, Thread.State.RUNNABLE, Thread.State.TIMED_WAITING,
        Thread.State.WAITING, Thread.State.NEW, Thread.State.TERMINATED};

    @Override
    public String[] getPaths() {
        return new String[] {"/api/threaddump.txt"};
    }

    @Override
    public ServiceResponse serve(final JSONObject post) throws IOException {

        final int multi = post.optInt("multi", post.optInt("count", 0));
        final StringBuilder buffer = new StringBuilder(1000);

        // Thread dump
        final Date dt = new Date();

        final int keylen = 30;
        bufferappend(buffer, "************* Start Thread Dump " + dt + " *******************");
        bufferappend(buffer, "");
    	final MemoryUsage memoryUsage = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        bufferappend(buffer, keylen, "Xms        Memory", memoryUsage.getInit());
        bufferappend(buffer, keylen, "Xmx        Memory", memoryUsage.getMax());
        bufferappend(buffer, keylen, "Assigned   Memory", Memory.assigned());
        bufferappend(buffer, keylen, "Used       Memory", Memory.used());
        bufferappend(buffer, keylen, "Available  Memory", Memory.available());
        bufferappend(buffer, keylen, "Short Status", Memory.shortStatus() ? "true" : "false");
        bufferappend(buffer, keylen, "Short Threshold", Float.toString(Memory.shortmemthreshold));
        bufferappend(buffer, keylen, "Cores", Memory.cores());
        bufferappend(buffer, keylen, "Active Thread Count", Thread.activeCount());
        bufferappend(buffer, keylen, "Total Started Thread Count", Memory.threadBean.getTotalStartedThreadCount());
        bufferappend(buffer, keylen, "Peak Thread Count", Memory.threadBean.getPeakThreadCount());
        bufferappend(buffer, keylen, "System Load Average", Memory.osBean.getSystemLoadAverage());
        long runtimeseconds = (System.currentTimeMillis() - startupTime) / 1000;
        int runtimeminutes = (int) (runtimeseconds / 60); runtimeseconds = runtimeseconds % 60;
        final int runtimehours = runtimeminutes / 60; runtimeminutes = runtimeminutes % 60;
        bufferappend(buffer, keylen, "Runtime", runtimehours + "h " + runtimeminutes + "m " + runtimeseconds + "s");
        // print system beans
        for (final Method method : Memory.osBean.getClass().getDeclaredMethods()) try {
            method.setAccessible(true);
            if (method.getName().startsWith("get") && Modifier.isPublic(method.getModifiers())) {
                bufferappend(buffer, keylen, method.getName(), method.invoke(Memory.osBean));
            }
        } catch (final Throwable e) {}

        bufferappend(buffer, "");
        bufferappend(buffer, "");

        if (multi > 0) {
            // generate multiple dumps
            final Map<String, Integer> dumps = new HashMap<>();
            for (int i = 0; i < multi; i++) {
                try {
                    final ThreadDump dump = new ThreadDump(ThreadDump.getAllStackTraces(), Thread.State.RUNNABLE);
                    for (final Map.Entry<StackTrace, SortedSet<String>> e: dump.entrySet()) {
                        if (multiDumpFilterPattern.matcher(e.getKey().text).matches()) continue;
                        Integer c = dumps.get(e.getKey().text);
                        if (c == null) dumps.put(e.getKey().text, Integer.valueOf(e.getValue().size()));
                        else {
                            c = Integer.valueOf(c.intValue() + e.getValue().size());
                            dumps.put(e.getKey().text, c);
                        }
                    }
                } catch (final OutOfMemoryError e) {
                    break;
                }
            }

            // write dumps
            while (!dumps.isEmpty()) {
                final Map.Entry<String, Integer> e = removeMax(dumps);
                bufferappend(buffer, "Occurrences: " + e.getValue());
                bufferappend(buffer, e.getKey());
                bufferappend(buffer, "");
            }
            bufferappend(buffer, "");
        } else {
            // generate a single thread dump
            final Map<Thread, StackTraceElement[]> stackTraces = ThreadDump.getAllStackTraces();
            // write those ordered into the stackTrace list
            for (final Thread.State state: ORDERED_STATES) new ThreadDump(stackTraces, state).appendStackTraces(buffer, state);
        }

        final ThreadMXBean threadbean = ManagementFactory.getThreadMXBean();
        bufferappend(buffer, "");
        bufferappend(buffer, "THREAD LIST FROM ThreadMXBean, " + threadbean.getThreadCount() + " threads:");
        bufferappend(buffer, "");
        final ThreadInfo[] threadinfo = threadbean.dumpAllThreads(true, true);
        for (final ThreadInfo ti: threadinfo) {
            bufferappend(buffer, ti.getThreadName());
        }

        return new ServiceResponse(buffer.toString());
    }


    private static class StackTrace {
        private final String text;
        private StackTrace(final String text) {
            this.text = text;
        }
        @Override
        public boolean equals(final Object a) {
            return (a != null && a instanceof StackTrace && this.text.equals(((StackTrace) a).text));
        }
        @Override
        public int hashCode() {
            return this.text.hashCode();
        }
        @Override
        public String toString() {
            return this.text;
        }
    }

    private static Map.Entry<String, Integer> removeMax(final Map<String, Integer> result) {
        Map.Entry<String, Integer> max = null;
        for (final Map.Entry<String, Integer> e: result.entrySet()) {
            if (max == null || e.getValue().intValue() > max.getValue().intValue()) {
                max = e;
            }
        }
        result.remove(max.getKey());
        return max;
    }

    private static void bufferappend(final StringBuilder buffer, final int keylen, final String key, final Object value) {
        if (value instanceof Double)
            bufferappend(buffer, keylen, key, ((Double) value).toString());
        else if (value instanceof Number)
            bufferappend(buffer, keylen, key, ((Number) value).longValue());
        else
            bufferappend(buffer, keylen, key, value.toString());
    }

    private static final DecimalFormat cardinalFormatter = new DecimalFormat("###,###,###,###,###");
    private static void bufferappend(final StringBuilder buffer, final int keylen, final String key, final long value) {
        bufferappend(buffer, keylen, key, cardinalFormatter.format(value));
    }

    private static void bufferappend(final StringBuilder buffer, final int keylen, final String key, final String value) {
        String a = key;
        while (a.length() < keylen) a += " ";
        a += "=";
        for (int i = value.length(); i < 20; i++) a += " ";
        a += value;
        bufferappend(buffer, a);
    }

    private static void bufferappend(final StringBuilder buffer, final String a) {
        buffer.append(a);
        buffer.append('\n');
    }

    private static class ThreadDump extends HashMap<StackTrace, SortedSet<String>> implements Map<StackTrace, SortedSet<String>> {

        private static final long serialVersionUID = -5587850671040354397L;

        private static Map<Thread, StackTraceElement[]> getAllStackTraces() {
            return Thread.getAllStackTraces();
        }

        private ThreadDump(
                final Map<Thread, StackTraceElement[]> stackTraces,
                final Thread.State stateIn) {
            super();

            Thread thread;
            // collect single dumps
            for (final Map.Entry<Thread, StackTraceElement[]> entry: stackTraces.entrySet()) {
                thread = entry.getKey();
                final StackTraceElement[] stackTraceElements = entry.getValue();
                StackTraceElement ste;
                final String tracename = "";
                final State threadState = thread.getState();
                final ThreadInfo info = Memory.threadBean.getThreadInfo(thread.getId());
                if (threadState != null && info != null && (stateIn == null || stateIn.equals(threadState)) && stackTraceElements.length > 0) {
                    final StringBuilder sb = new StringBuilder(3000);
                    final String threadtitle = tracename + "THREAD: " + thread.getName() + " " + (thread.isDaemon()?"daemon":"") + " id=" + thread.getId() + " " + threadState.toString() + (info.getLockOwnerId() >= 0 ? " lock owner =" + info.getLockOwnerId() : "");
                    boolean cutcore = true;
                    for (int i = 0; i < stackTraceElements.length; i++) {
                        ste = stackTraceElements[i];
                        final String className = ste.getClassName();
                        final String classString = ste.toString();
                        if (cutcore && (className.startsWith("java.") || className.startsWith("sun."))) {
                            sb.setLength(0);
                            bufferappend(sb, tracename + "at " + classString);
                        } else {
                            cutcore = false;
                            bufferappend(sb, tracename + "at " + classString);
                        }
                    }
                    final StackTrace stackTrace = new StackTrace(sb.toString());
                    SortedSet<String> threads = get(stackTrace);
                    if (threads == null) {
                        threads = new TreeSet<>();
                        put(stackTrace, threads);
                    }
                    threads.add(threadtitle);
                }
            }
        }

        private void appendStackTraces(
                final StringBuilder buffer,
                final Thread.State stateIn) {
            bufferappend(buffer, "THREADS WITH STATES: " + stateIn.toString());
            bufferappend(buffer, "");

            // write dumps
            for (final Map.Entry<StackTrace, SortedSet<String>> entry: entrySet()) {
                final SortedSet<String> threads = entry.getValue();
                for (final String t: threads) bufferappend(buffer, t);
                bufferappend(buffer, entry.getKey().text);
                bufferappend(buffer, "");
            }
            bufferappend(buffer, "");
        }

    }
}
