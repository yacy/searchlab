/**
 *  DateParser
 *  Copyright 09.04.2015 by Michael Peter Christen, @orbiterlab
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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class DateParser {

    public final static long HOUR_MILLIS = 60 * 60 * 1000;
    public final static long DAY_MILLIS = HOUR_MILLIS * 24;
    public final static long WEEK_MILLIS = DAY_MILLIS * 7;

    public final static String PATTERN_ISO8601       = "yyyy-MM-dd'T'HH:mm:ss'Z'"; // pattern for a W3C datetime variant of a non-localized ISO8601 date
    public final static String PATTERN_ISO8601MILLIS = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"; // same with milliseconds, called date_optimal_time in elastic
    public final static String PATTERN_MONTHDAY                 = "yyyy-MM-dd"; // the twitter search modifier format
    public final static String PATTERN_MONTHDAYHOURMINUTE       = "yyyy-MM-dd HH:mm"; // this is the format which morris.js understands for date-histogram graphs
    public final static String PATTERN_MONTHDAYHOURMINUTESECOND = "yyyy-MM-dd HH:mm:ss";
    public final static String YEARTOSECONDFILENAME = "yyyyMMddHHmmss";
    public final static String PATTERN_RFC1123 = "EEE, dd MMM yyyy HH:mm:ss Z"; // with numeric time zone indicator as defined in RFC5322

    /** Date formatter/non-sloppy parser for W3C datetime (ISO8601) in GMT/UTC */
    public final static SimpleDateFormat iso8601Format = new SimpleDateFormat(PATTERN_ISO8601, Locale.US);
    public final static SimpleDateFormat dayDateFormat = new SimpleDateFormat(PATTERN_MONTHDAY, Locale.US);
    public final static SimpleDateFormat FORMAT_RFC1123 = new SimpleDateFormat(PATTERN_RFC1123, Locale.US);


    public final static Calendar UTCCalendar = Calendar.getInstance();
    public final static TimeZone UTCtimeZone = TimeZone.getTimeZone("UTC");
    static {
        UTCCalendar.setTimeZone(UTCtimeZone);
        iso8601Format.setCalendar(UTCCalendar);
        dayDateFormat.setCalendar(UTCCalendar);
        FORMAT_RFC1123.setCalendar(UTCCalendar);
    }

    /**
     * parse a date string for a given time zone
     * @param dateString in format "yyyy-MM-dd", "yyyy-MM-dd HH:mm" or "yyyy-MM-dd_HH:mm"
     * @param timezoneOffset number of minutes, must be negative for locations east of UTC and positive for locations west of UTC
     * @return a calender object representing the parsed date
     * @throws ParseException if the format of the date string is not well-formed
     */
    public static Calendar parse(String dateString, final int timezoneOffset) throws ParseException, NumberFormatException {
        final Calendar cal = Calendar.getInstance(UTCtimeZone);
        if ("now".equals(dateString)) return cal;
        if ("hour".equals(dateString)) {cal.setTime(oneHourAgo()); return cal;}
        if ("day".equals(dateString)) {cal.setTime(oneDayAgo()); return cal;}
        if ("week".equals(dateString)) {cal.setTime(oneWeekAgo()); return cal;}
        dateString = dateString.replaceAll("_", " ");
        int p = -1;
        if ((p = dateString.indexOf(':')) > 0) {
            if (dateString.indexOf(':', p + 1) > 0) {
                    cal.setTime(secondDateFormatParser().parse(dateString));
                } else {
                    cal.setTime(minuteDateFormatParser().parse(dateString));
                }
        } else synchronized (dayDateFormat) {
            cal.setTime(dayDateFormat.parse(dateString));
        }
        cal.add(Calendar.MINUTE, timezoneOffset); // add a correction; i.e. for UTC+1 -60 minutes is added to patch a time given in UTC+1 to the actual time at UTC
        return cal;
    }

    public static SimpleDateFormat iso8601MillisParser() {
        final SimpleDateFormat iso8601MillisFormat = new SimpleDateFormat(PATTERN_ISO8601MILLIS, Locale.US);
        iso8601MillisFormat.setCalendar(UTCCalendar);
        return iso8601MillisFormat;
    }

    public static SimpleDateFormat minuteDateFormatParser() {
        final SimpleDateFormat minuteDateFormat = new SimpleDateFormat(PATTERN_MONTHDAYHOURMINUTE, Locale.US);
        minuteDateFormat.setCalendar(UTCCalendar);
        return minuteDateFormat;
    }

    public static SimpleDateFormat secondDateFormatParser() {
        final SimpleDateFormat secondDateFormat = new SimpleDateFormat(PATTERN_MONTHDAYHOURMINUTESECOND, Locale.US);
        secondDateFormat.setCalendar(UTCCalendar);
        return secondDateFormat;
    }

    public static String toPostDate(final Date d) {
        return secondDateFormatParser().format(d).replace(' ', '_');
    }

    public static int getTimezoneOffset() {
        final Calendar calendar = new GregorianCalendar();
        final TimeZone timeZone = calendar.getTimeZone();
        return - (int) TimeUnit.MILLISECONDS.toMinutes(timeZone.getRawOffset()); // we negate the offset because thats the value which is provided by the browser as well
    }

    public static Date oneHourAgo() {
        return new Date(System.currentTimeMillis() - HOUR_MILLIS);
    }

    public static Date oneDayAgo() {
        return new Date(System.currentTimeMillis() - DAY_MILLIS);
    }

    public static Date oneWeekAgo() {
        return new Date(System.currentTimeMillis() - WEEK_MILLIS);
    }

    private static long lastRFC1123long = 0;
    private static String lastRFC1123string = "";

    public static final String formatRFC1123(final Date date) {
        if (date == null) return "";
        if (Math.abs(date.getTime() - lastRFC1123long) < 1000) {
            //System.out.println("date cache hit - " + lastRFC1123string);
            return lastRFC1123string;
        }
        synchronized (FORMAT_RFC1123) {
            final String s = FORMAT_RFC1123.format(date);
            lastRFC1123long = date.getTime();
            lastRFC1123string = s;
            return s;
        }
    }

    /**
     * Format date for GSA (short form of ISO8601 date format)
     * @param date
     * @return datestring "yyyy-mm-dd"
     * @see ISO8601Formatter
     */
    public static final String formatGSAFS(final Date date) {
        if (date == null) return "";
        synchronized (dayDateFormat) {
            final String s = dayDateFormat.format(date);
            return s;
        }
    }

    /**
     * Parse GSA date string (short form of ISO8601 date format)
     * @param datestring
     * @return date or null
     * @see ISO8601Formatter
     */
    public static final Date parseGSAFS(final String datestring) {
        synchronized (dayDateFormat) { try {
            return dayDateFormat.parse(datestring);
        } catch (final ParseException | NumberFormatException e) {
            return null;
        }}
    }
}