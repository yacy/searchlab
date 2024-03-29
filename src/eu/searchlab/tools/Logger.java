/**
 *  Logger
 *  Copyright 02.01.2018 by Michael Peter Christen, @orbiterlab
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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;


public class Logger {

    private static int maxlines = 10000;
    private static final ConcurrentLinkedQueue<LogEntry> lines = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger a = new AtomicInteger(0);
    private static final Map<String, org.slf4j.Logger> logger = new ConcurrentHashMap<>();
    private static final org.slf4j.Logger dfltLogger = org.slf4j.LoggerFactory.getLogger("default");
    private static final String logFormat = "%1$-7s [%2$s] %3$-48s %4$s%5$s%n";
    private static final SimpleDateFormat iso8601MillisParser = DateParser.iso8601MillisParser();

    static {
        final StreamHandler sh = new ConsoleHandler();
        java.util.logging.Logger javaLogger = java.util.logging.Logger.getGlobal();
        while (javaLogger.getParent() != null) javaLogger = javaLogger.getParent();
        final Handler[] dfltHandlers = javaLogger.getHandlers();
        for (final Handler handler: dfltHandlers) javaLogger.removeHandler(handler);
        sh.setFormatter(new LineFormatter());
        javaLogger.addHandler(sh);
        javaLogger.setLevel(Level.FINE);
        javaLogger.log(java.util.logging.Level.INFO, "configured logging, loglevel " + javaLogger.getLevel());
    }

    /**
     * all logging is redirected to java logging and is handled by this console handler.
     */
    public final static class ConsoleHandler extends StreamHandler {

        public ConsoleHandler() {
            super(System.out, new SimpleFormatter());
        }

        @Override
        public void publish(final LogRecord record) {
            super.publish(record);
            super.flush();
        }
    }

    public final static class LogEntry {
        public final long time;
        public final String levelName;
        public final String className;
        public final String message;
        public final Throwable t;
        public LogEntry(final String levelName, final String className, final String message, final Throwable t) {
            this.levelName = levelName;
            this.time = System.currentTimeMillis();
            this.className = className;
            this.message = message;
            this.t = t;
        }
    }

    public static class LineFormatter extends Formatter {

        // format string for printing the log record
        private final Date dat = new Date();

        @Override
        public synchronized String format(final LogRecord record) {
            this.dat.setTime(record.getMillis());
            StringBuilder source = null;
            if (record.getSourceClassName() != null) {
                source = new StringBuilder(record.getSourceClassName());
                if (record.getSourceMethodName() != null) {
                   source.append('.').append(record.getSourceMethodName());
                }
            } else {
                source = new StringBuilder(record.getLoggerName());
            }
            String message = formatMessage(record);
            final int p = message.indexOf('$');
            if (p >= 0) {
                source = new StringBuilder(message.substring(0, p));
                message = message.substring(p + 1);
            }
            final String levelname = record.getLevel().getName();
            final String line = formatLine(levelname, this.dat, source.toString(), message, record.getThrown());
            return line;
        }
    }

    private final static String formatLine(final String levelName, final Date dat, final String className, final String message, final Throwable t) {
        String throwable = "";
        if (t != null) {
            final StringWriter sw = new StringWriter();
            final PrintWriter pw = new PrintWriter(sw);
            pw.println();
            t.printStackTrace(pw);
            pw.close();
            throwable = sw.toString();
        }

        synchronized(iso8601MillisParser) {
            final String line = String.format(logFormat,
                levelName,
                iso8601MillisParser.format(dat), // this is date_optional_time in elastic
                className,
                message,
                throwable);
            return line;
        }
    }

    public static void setMaxLines(final int m) {
        maxlines = m;
    }

    public static ArrayList<LogEntry> getEntries(final int max) {
        final Object[] a = lines.toArray();
        final ArrayList<LogEntry> l = new ArrayList<>();
        final int start = Math.max(0, a.length - max);
        for (int i = start; i < a.length; i++) {
            final LogEntry logEntry = (LogEntry) a[i];
            l.add(logEntry);
        }
        return l;
    }

    public static ArrayList<String> getLines(final int max) {
        final Object[] a = lines.toArray();
        final ArrayList<String> l = new ArrayList<>();
        final int start = Math.max(0, a.length - max);
        for (int i = start; i < a.length; i++) {
            final LogEntry logEntry = (LogEntry) a[i];
            final String line = formatLine(logEntry.levelName, new Date(logEntry.time), logEntry.className, logEntry.message, logEntry.t);
            l.add(line);
        }
        return l;
    }

    private static void append(final String levelName, final String className, final String line, final Throwable t) {
        if (line != null) lines.add(new LogEntry(levelName, className, line, t));
        if (a.incrementAndGet() % 100 == 0) {
            clean(maxlines);
            a.set(0);
        }
    }

    public static void clean(final int remaining) {
        int c = lines.size() - remaining;
        while (c-- > 0) lines.poll();
    }

    private static org.slf4j.Logger getLogger(final String className) {
        if (className == null || className.length() == 0) return dfltLogger;
        org.slf4j.Logger l = logger.get(className);
        if (l == null) {
            l = org.slf4j.LoggerFactory.getLogger(className);
            logger.put(className, l);
        }
        return l;
    }

    private final static String loggerClassName = Logger.class.getCanonicalName();

    private static String getCallerClassName() {
        final StackTraceElement[] stElements = Thread.currentThread().getStackTrace();
        for (int i = 1; i < stElements.length; i++) {
            final StackTraceElement ste = stElements[i];
            final String cn = ste.getClassName();
            if (!cn.equals(loggerClassName) && cn.indexOf("java.lang") < 0) {
                final int p = cn.indexOf('$');
                return p < 0 ? cn : cn.substring(0, p);
            }
        }
        return null;
    }

    private static void debug(final String className, final String msg) {
        getLogger(className).debug(className + '$' + msg);
        append("DEBUG", className, msg, null);
    }

    private static void debug(final String className, final String msg, final Throwable t) {
        getLogger(className).debug(className + '$' + msg, t);
        append("DEBUG", className, msg, t);
    }

    public static void debug(final String msg) {
        debug(getCallerClassName(), msg);
    }

    public static void debug(final Throwable t) {
        debug(getCallerClassName(), "", t);
    }

    public static void debug(final String msg, final Throwable t) {
        debug(getCallerClassName(), msg, t);
    }

    public static void debug(final Class<?> cls, final String msg) {
        debug(cls.getCanonicalName(), msg);
    }

    public static void debug(final Class<?> cls, final Throwable t) {
        debug(cls.getCanonicalName(), "", t);
    }

    public static void debug(final Class<?> cls, final String msg, final Throwable t) {
        debug(cls.getCanonicalName(), msg, t);
    }


    private static void info(final String className, final String msg) {
        getLogger(className).info(className + '$' + msg);
        append("INFO", className, msg, null);
    }

    public static void info(final String msg) {
        info(getCallerClassName(), msg);
    }

    public static void info(final Class<?> cls, final String msg) {
        info(cls.getCanonicalName(), msg);
    }


    private static void warn(final String className, final String msg) {
        getLogger(className).warn(className + '$' + msg);
        append("WARN", className, msg, null);
    }

    private static void warn(final String className, final String msg, final Throwable t) {
        getLogger(className).warn(className + '$' + msg, t);
        append("WARN",className, msg, t);
    }

    public static void warn(final String msg) {
        warn(getCallerClassName(), msg);
    }

    public static void warn(final Throwable t) {
        warn(getCallerClassName(), "", t);
    }

    public static void warn(final String msg, final Throwable t) {
        warn(getCallerClassName(), msg, t);
    }

    public static void warn(final Class<?> cls, final String msg) {
        warn(cls.getCanonicalName(), msg);
    }

    public static void warn(final Class<?> cls, final Throwable t) {
        warn(cls.getCanonicalName(), "", t);
    }

    public static void warn(final Class<?> cls, final String msg, final Throwable t) {
        warn(cls.getCanonicalName(), msg, t);
    }


    private static void error(final String className, final String msg) {
        getLogger(className).error(className + '$' + msg);
        append("ERROR", className, msg, null);
    }

    private static void error(final String className, final String msg, final Throwable t) {
        getLogger(className).error(className + '$' + msg, t);
        append("ERROR", className, msg, t);
    }

    public static void error(final String msg) {
        error(getCallerClassName(), msg);
    }

    public static void error(final Throwable t) {
        error(getCallerClassName(), "", t);
    }

    public static void error(final String msg, final Throwable t) {
        error(getCallerClassName(), msg, t);
    }

    public static void error(final Class<?> cls, final String msg) {
        error(cls.getCanonicalName(), msg);
    }

    public static void error(final Class<?> cls, final Throwable t) {
        error(cls.getCanonicalName(), "", t);
    }

    public static void error(final Class<?> cls, final String msg, final Throwable t) {
        error(cls.getCanonicalName(), msg, t);
    }

}
