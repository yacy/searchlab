/**
 *  Week
 *  Copyright 09.10.2021 by Michael Peter Christen, @orbiterlab
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

package eu.searchlab.storage.table;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalField;
import java.time.temporal.WeekFields;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import eu.searchlab.tools.Logger;

/**
 * Weeks according to ISO 8601
 *
 */
public class CalendarWeek implements Comparable<CalendarWeek> {

    private final static TemporalField wowby = WeekFields.of(Locale.GERMANY).weekOfWeekBasedYear();
    private final static TemporalField yowby = WeekFields.of(Locale.GERMANY).weekBasedYear();
    private final static DateTimeFormatter KWFormatter = new DateTimeFormatterBuilder().appendPattern("YYYYww").parseDefaulting(WeekFields.ISO.dayOfWeek(), 1).toFormatter();

    private final static ZoneId homeZone = ZoneId.of("Europe/Paris");

    private final static Set<Integer> y53kw = new HashSet<>();
    private final static Map<Integer, Integer> eastersundayweek = new HashMap<>();
    private final static int[] easteroffsetweeks = new int[] {
            -10, -9, -8,
            -7, // women thursday
            -6, // rose monday
            -5, -4, -3, -2, -1,
            0,  // easter sunday
            1,  // easter monday
            2, 3, 4, 5,
            6,  // ascension of christ // 39 days after easter sunday
            7,  // pentecost sunday
            8,  // whit monday
            9,  // corpus christi
            10, 11, 12
    };

    private final static Map<Integer, int[]> prevWeekMap = new HashMap<>();
    static {
        y53kw.add(2004); y53kw.add(2009); y53kw.add(2015); y53kw.add(2020); y53kw.add(2026);
        y53kw.add(2032); y53kw.add(2037); y53kw.add(2043); y53kw.add(2048); y53kw.add(2054);

        eastersundayweek.put(2015, 14); eastersundayweek.put(2016, 12); eastersundayweek.put(2017, 15); eastersundayweek.put(2018, 13);
        eastersundayweek.put(2019, 16); eastersundayweek.put(2020, 15); eastersundayweek.put(2021, 13); eastersundayweek.put(2022, 15);
        eastersundayweek.put(2023, 14); eastersundayweek.put(2024, 13); eastersundayweek.put(2025, 16); eastersundayweek.put(2026, 14);
        eastersundayweek.put(2027, 12); eastersundayweek.put(2028, 15); eastersundayweek.put(2029, 13); eastersundayweek.put(2030, 16);
        eastersundayweek.put(2031, 15); eastersundayweek.put(2032, 13); eastersundayweek.put(2033, 15); eastersundayweek.put(2034, 14);

        // compute the prevWeekMap
        for (final int year: eastersundayweek.keySet()) {
            final int[] weeks = new int[y53kw.contains(year) ? 53 : 52];
            final int easterweek = eastersundayweek.get(year);
            for (int i = 0; i < weeks.length; i++) {
                final int week = i + 1;
                // calculate the week number for the year before
                int prevweek = 0; // 0 as poison number

                // lookup of easter-related week in last year
                if (eastersundayweek.containsKey(year - 1)) {
                    offsetlookup: for (final int offset: easteroffsetweeks) {
                        if (easterweek + offset == week) {
                            final int prevyeareasternkw = eastersundayweek.get(year - 1);
                            prevweek = prevyeareasternkw + offset;
                            break offsetlookup;
                        }
                    }
                }

                // alternative computation in case that this is not applicable
                if (prevweek == 0) prevweek = decYearBusinessInternal(year, week).week;
                weeks[i] = prevweek;
            }
            prevWeekMap.put(year, weeks);
        }
    }

    public static int weekNumberPerYear(final int year) {
        return y53kw.contains(year) ? 53 : 52;
    }

    private int year, week;

    public CalendarWeek(final int jahr, final int woche) {
        this.year = jahr;
        this.week = woche;
    }

    public CalendarWeek(final String yyyyww) {
        try {
            final LocalDate ld = LocalDate.parse(yyyyww, KWFormatter); // this might fail for unknown reasons ("Text '201501' could not be parsed at index 0")
            this.year = ld.get(yowby);
            this.week = ld.get(wowby);
        } catch(final DateTimeParseException e) {
            Logger.warn("Parser exception for " + yyyyww, e);
            this.year = Integer.parseInt(yyyyww.substring(0, 4));
            this.week = Integer.parseInt(yyyyww.substring(4));
        }
    }

    public CalendarWeek(final LocalDate ld) {
        this(ld.get(yowby), ld.get(wowby));
    }

    public long getTime() {
        final Instant instant = getFirstDayOfWeek().atStartOfDay(homeZone).toInstant();
        return instant.toEpochMilli();
    }

    public int getYear() {
        return this.year;
    }

    public int getWeek() {
        return this.week;
    }

    public String getYYYYWW() {
        return Integer.toString(this.year) + getWW();
    }

    public String getWW() {
        return (this.week < 10 ? "0" : "") + this.week;
    }

    public LocalDate getFirstDayOfWeek() {
        return LocalDate.now().with(DayOfWeek.MONDAY).with(yowby, this.year).with(wowby, this.week);
    }

    public LocalDate getLastDayOfWeek() {
        return LocalDate.now().with(DayOfWeek.SUNDAY).with(yowby, this.year).with(wowby, this.week);
    }

    public LocalDate getSaturdayDayOfWeek() {
        return LocalDate.now().with(DayOfWeek.SATURDAY).with(yowby, this.year).with(wowby, this.week);
    }

    public CalendarWeek inc() {
        final int nextWoche = this.week + 1;
        if (nextWoche > weekNumberPerYear(this.year)) return new CalendarWeek(this.year + 1, 1);
        return new CalendarWeek(this.year , nextWoche);
    }

    public CalendarWeek dec() {
        final int prevWoche = this.week - 1;
        if (prevWoche < 1) return new CalendarWeek(this.year - 1, weekNumberPerYear(this.year - 1));
        return new CalendarWeek(this.year , prevWoche);
    }

    public CalendarWeek decYearNumeric() {
        final int prevYear = this.year - 1;
        if (this.week == 53 && !y53kw.contains(prevYear)) return new CalendarWeek(prevYear, 52);
        if (this.week == 52 && y53kw.contains(prevYear)) return new CalendarWeek(prevYear, 53);
        return new CalendarWeek(prevYear, this.week);
    }

    public CalendarWeek decYearBusiness() {
        final int[] prevYearWeeks = prevWeekMap.get(this.year);
        if (prevYearWeeks == null) {
            // fail-over method if we have no prev-computed year present
            return decYearBusinessInternal();
        }
        final CalendarWeek kw0 = new CalendarWeek(this.year - 1, prevYearWeeks[this.week - 1]); // ACHTUNG "this.woche - 1" ist richtig weil wir die Woche 1 auf Indexposition 0 speichern!
        assert kw0.equals(this.decYearBusinessInternal());
        return kw0;
    }

    public CalendarWeek decYearBusinessInternal() {
        return decYearBusinessInternal(this.year, this.week);
    }

    public static CalendarWeek decYearBusinessInternal(final int y1, final int w1) {

        if (!eastersundayweek.containsKey(y1 - 1)) return new CalendarWeek(y1 - 1, w1 == 53 ? 52 : w1);

        final int easterweek1 = eastersundayweek.get(y1);
        final int easterweek0 = eastersundayweek.get(y1 - 1);

        // calculate the week number for the year before
        int w0 = 0; // 0 as poison number

        // lookup of easter-related week in last year
        offsetlookup: for (final int offset: easteroffsetweeks) {
            if (easterweek1 + offset == w1) {
                w0 = easterweek0 + offset; // week of previous year with same offset
                break offsetlookup;
            }
        }

        if (w0 != 0) return new CalendarWeek(y1 - 1, w0);

        // find calendary previous week
        final LocalDate d1 = LocalDate.now().with(yowby, y1).with(wowby, w1).with(DayOfWeek.SATURDAY);
        assert d1.getDayOfWeek() == DayOfWeek.SATURDAY;
        final int day1 = d1.getDayOfMonth();
        final int month1 = d1.getMonthValue();
        final int year1 = d1.getYear();
        final int year0 = year1 - 1;
        try {
            final LocalDate d0 = LocalDate.of(year0, month1, day1); // danger of DateTimeException: Invalid date 'February 29' as '2019' is not a leap year
            w0 = d0.get(wowby);
            final int y0 = d0.get(yowby);
            if (y0 == y1 - 1) return new CalendarWeek(y0, w0);
            return new CalendarWeek(y1 - 1, w1 == 53 ? 52 : w1);
        } catch (final DateTimeException e) {
            assert false;
            return new CalendarWeek(y1 - 1, w1 == 53 ? 52 : w1);
        }
    }

    /**
     * Compares this object with the specified object for order.  Returns a
     * negative integer, zero, or a positive integer as this object is less
     * than, equal to, or greater than the specified object.
     *
     * @param otherKW
     * @return -1 if this < other; 0 if this == other, 1 if this > other
     */
    @Override
    public int compareTo(final CalendarWeek otherKW) {
        if (this.year < otherKW.year) return -1;
        if (this.year > otherKW.year) return 1;
        // now: jahr == otherKW.jahr
        if (this.week < otherKW.week) return -1;
        if (this.week > otherKW.week) return 1;
        return 0;
    }

    @Override
    public int hashCode() {
        return this.year * 100 + this.week;
    }

    @Override
    public String toString() {
        return this.getYYYYWW();
    }

}
