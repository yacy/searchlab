package eu.searchlab.storage.table;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
/**
 *  TableParser
 *  Copyright 29.05.2022 by Michael Peter Christen, @orbiterlab
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
import java.util.Date;
import java.util.Locale;

import eu.searchlab.storage.io.ConcurrentIO;
import eu.searchlab.storage.io.IOObject;
import eu.searchlab.storage.io.IOPath;
import eu.searchlab.tools.DateParser;
import eu.searchlab.tools.Logger;
import tech.tablesaw.api.DateColumn;
import tech.tablesaw.api.DoubleColumn;
import tech.tablesaw.api.InstantColumn;
import tech.tablesaw.api.LongColumn;
import tech.tablesaw.api.StringColumn;
import tech.tablesaw.api.Table;
import tech.tablesaw.columns.Column;
import tech.tablesaw.io.csv.CsvReadOptions;
import tech.tablesaw.io.csv.CsvWriteOptions;
import tech.tablesaw.io.csv.CsvWriter;

public class TableParser {

    public static Table readCSV(final ConcurrentIO io, final IOPath iop) throws IOException {
        final IOObject[] ioo = io.readForced(iop);
        assert ioo.length == 1;
        final byte[] b = ioo[0].getObject();
        String s = new String(b, StandardCharsets.UTF_8);

        // patch the file to wipe out unparsable objects
        s = s.replaceAll("0000-", "2022-");

        // use tablesaw to parse the csv
        final StringReader sr = new StringReader(s);
        final CsvReadOptions options =
                CsvReadOptions.builder(sr)
                    .separator(';')
                    .locale(Locale.ENGLISH)
                    .header(true)
                    .dateFormat(DateParser.minuteDateFormatter)
                    .build();
        final Table table = Table.read().usingOptions(options);
        return table;
    }

    public static void storeCSV( final ConcurrentIO io, final IOPath iop, final Table table) {
        final long start = System.currentTimeMillis();
        try {
            // prepare document
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            final OutputStreamWriter osw = new OutputStreamWriter(baos, StandardCharsets.UTF_8);
            final CsvWriteOptions options = CsvWriteOptions.builder(osw)
                    .separator(';')
                    .header(true)
                    .build();
            new CsvWriter().write(table, options);
            osw.close();

            // write to io
            io.writeForced(new IOObject(iop, baos.toByteArray()));
            final long stop = System.currentTimeMillis();
            Logger.info("wrote user audit " + iop.toString() + " in " + (stop - start) + " milliseconds");
        } catch (final IOException e) {
            Logger.warn("failed to write user audit to " + iop.toString(), e);
        }
    }

    public static InstantColumn asInstant(final Column<?> column) throws IOException {
        if (column instanceof InstantColumn) {
            return (InstantColumn) column;
        } else if (column instanceof DateColumn) {
            final InstantColumn ic = InstantColumn.create(column.name());
            for (final LocalDate ld: ((DateColumn) column).asList()) {
                ic.append(Instant.ofEpochMilli(ld.toEpochDay()));
            }
            return ic;
        } else if (column instanceof LongColumn) {
            final InstantColumn ic = InstantColumn.create(column.name());
            for (final long l: ((LongColumn) column).asList()) {
                ic.append(Instant.ofEpochMilli(l));
            }
            return ic;
        } else {
            final InstantColumn ic = InstantColumn.create(column.name());
            final SimpleDateFormat iso8601MillisParser = DateParser.iso8601MillisParser();
            for (final Object o: column.asList()) {
                try {
                    final Date d = iso8601MillisParser.parse(o.toString());
                    final Instant i = Instant.ofEpochMilli(d.getTime());
                    ic.append(i);
                } catch (final ParseException e) {
                    throw new IOException("cannot parse date: " + o.toString(), e);
                }
            }
            return ic;
        }
    }

    public static StringColumn asString(final Column<?> column) {
        if (column instanceof StringColumn) {
            return (StringColumn) column;
        } else if (column instanceof DateColumn) {
            final StringColumn sc = StringColumn.create(column.name());
            for (final LocalDate ld: ((DateColumn) column).asList()) {
                sc.append(ld.toString());
            }
            return sc;
        } else {
            final StringColumn sc = StringColumn.create(column.name());
            for (final Object o: column.asList()) {
                sc.append(o.toString());
            }
            return sc;
        }
    }

    public static DoubleColumn asDouble(final Column<?> column) {
        if (column instanceof DoubleColumn) {
            return (DoubleColumn) column;
        } else {
            final DoubleColumn dc = DoubleColumn.create(column.name());
            for (final Object o: column.asList()) {
                dc.append(Double.parseDouble(o.toString()));
            }
            return dc;
        }
    }

    public static LongColumn asLong(final Column<?> column) {
        if (column instanceof LongColumn) {
            return (LongColumn) column;
        } else {
            final LongColumn lc = LongColumn.create(column.name());
            for (final Object o: column.asList()) {
                lc.append(Long.parseLong(o.toString()));
            }
            return lc;
        }
    }

}
