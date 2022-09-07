/**
 *  TableViewer
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import tech.tablesaw.api.Table;
import tech.tablesaw.columns.Column;
import tech.tablesaw.plotly.components.Axis;
import tech.tablesaw.plotly.components.Figure;
import tech.tablesaw.plotly.components.Layout;
import tech.tablesaw.plotly.components.Layout.LayoutBuilder;
import tech.tablesaw.plotly.components.Line;
import tech.tablesaw.plotly.components.Marker;
import tech.tablesaw.plotly.components.Page;
import tech.tablesaw.plotly.display.Browser;
import tech.tablesaw.plotly.traces.ScatterTrace;
import tech.tablesaw.plotly.traces.ScatterTrace.ScatterBuilder;
import tech.tablesaw.plotly.traces.Trace;

public class TableViewer {

    public final static File outpath = new File("data/graph");
    static {
        outpath.mkdirs();
    }

    private final List<Trace> traces;
    private final List<Axis.Type> axistypes;
    private final String filename, title, xScaleName;

    public static class GraphTypes {
        private final String[] yaxis;
        public GraphTypes(final String... yaxis) {
            this.yaxis = yaxis;
        }
        public int count() {
            return this.yaxis.length;
        }
        public String getAxisName(final int i) {
            final String s = this.yaxis[i];
            final int p = s.lastIndexOf(' ');
            if (p < 0) return s;
            return s.substring(0, p);
        }
        public String getGraphColor(final int i) {
            final String s = this.yaxis[i];
            final int p = s.lastIndexOf(' ');
            if (p < 0) return "black";
            return s.substring(p + 1);
        }
    }

    public TableViewer(final String filename, final String title, final String xScaleName) {
        this.traces = new ArrayList<>();
        this.axistypes = new ArrayList<>();
        this.filename = filename;
        this.title = title;
        this.xScaleName = xScaleName;
    }

    public static Marker colorMarker(final String color) {
        return Marker.builder().color(color).build();
    }

    public void timeseries(final Table table, final String timecolname, final double linewidth, final ScatterTrace.YAxis yaxis, final GraphTypes scale) {
        for (int x = 0; x < scale.count(); x++) {
            final String yColName = scale.getAxisName(x);
            final Column<?> timecol = table.column(timecolname);
            final Column<?> valcol = table.column(yColName);
            final ScatterBuilder traceBuilder =
                    ScatterTrace.builder(timecol, valcol)
                    .mode(ScatterTrace.Mode.LINE)
                    .name(yColName)
                    .marker(colorMarker(scale.getGraphColor(x)))
                    .line(Line.builder().width(linewidth).build())
                    .yAxis(yaxis);
            this.traces.add(traceBuilder.build());
        }
        this.axistypes.add(Axis.Type.LINEAR);
    }

    public void chart(final Table table, final double linewidth, final ScatterTrace.YAxis yaxis, final GraphTypes scale) {
        for (int x = 0; x < scale.count(); x++) {
            final String yColName = scale.getAxisName(x);
            final ScatterBuilder traceBuilder =
                    ScatterTrace.builder(table.intColumn(0), table.doubleColumn(yColName))
                    .mode(ScatterTrace.Mode.LINE)
                    .name(yColName)
                    .marker(colorMarker(scale.getGraphColor(x)))
                    .line(Line.builder().width(linewidth).build())
                    .yAxis(yaxis);
            this.traces.add(traceBuilder.build());
        }
        this.axistypes.add(Axis.Type.LINEAR);
    }

    public void charts(final Table table, final double linewidth, final ScatterTrace.YAxis yaxis, final GraphTypes scale) {
        for (int x = 0; x < scale.count(); x++) {
            final String yColName = scale.getAxisName(x);
            final ScatterBuilder traceBuilder =
                    ScatterTrace.builder(table.stringColumn(0), table.doubleColumn(yColName))
                    .mode(ScatterTrace.Mode.LINE)
                    .name(yColName)
                    .marker(colorMarker(scale.getGraphColor(x)))
                    .line(Line.builder().width(linewidth).build())
                    .yAxis(yaxis);
            this.traces.add(traceBuilder.build());
        }
        this.axistypes.add(Axis.Type.LINEAR);
    }

    public static File renderFile(final String filename) {
        final File outputFile = new File(outpath, filename + ".html");
        return outputFile;
    }

    public String render2html(final int width, final int height, final boolean served) {
        final Axis[] a = new Axis[4];
        a[0] = Axis.builder().type(this.axistypes.size() > 0 ? this.axistypes.get(0): Axis.Type.CATEGORY).autoRange(Axis.AutoRange.TRUE).side(Axis.Side.left).build();
        a[1] = Axis.builder().type(this.axistypes.size() > 1 ? this.axistypes.get(1): Axis.Type.CATEGORY).autoRange(Axis.AutoRange.TRUE).side(Axis.Side.right).overlaying(ScatterTrace.YAxis.Y).build();
        a[2] = Axis.builder().type(this.axistypes.size() > 2 ? this.axistypes.get(2): Axis.Type.CATEGORY).autoRange(Axis.AutoRange.TRUE).side(Axis.Side.left).overlaying(ScatterTrace.YAxis.Y2).build();
        a[3] = Axis.builder().type(this.axistypes.size() > 3 ? this.axistypes.get(3): Axis.Type.CATEGORY).autoRange(Axis.AutoRange.TRUE).side(Axis.Side.right).overlaying(ScatterTrace.YAxis.Y3).build();
        final Axis xaxis = Axis.builder().title(this.xScaleName).showGrid(true).build();
        final LayoutBuilder lb = Layout.builder(this.title)
                .xAxis(xaxis)
                .width(width).height(height)
                .plotBgColor("#000").paperBgColor("#000");
        for (int y = 0; y < this.axistypes.size(); y++) {
            if (y == 0) lb.yAxis(a[0]);
            if (y == 1) lb.yAxis2(a[1]);
            if (y == 2) lb.yAxis3(a[2]);
            if (y == 3) lb.yAxis4(a[3]);
        }
        final Figure figure = new Figure(lb.build(), this.traces.toArray(new Trace[this.traces.size()]));
        final Page page = Page.pageBuilder(figure, Integer.toHexString(Math.abs(this.filename.hashCode()))).build();
        String output = page.asJavascript();
        if (served) output = cdnReplacer(output);
        return output;
    }

    public String cdnReplacer(String html) {
        // patch cdn sources that external libraries may generate, here especially: plotly
        final String bad = "https://cdn.plot.ly/plotly-latest.min.js";
        final String good = "/js/plotly-latest.min.js";
        final int p = html.indexOf(bad);
        if (p >= 0) html = html.substring(0, p) + good + html.substring(p + bad.length());
        return html;
    }

    public File render2file(final boolean openBrowser) {

        final File outputFile = new File(outpath, this.filename + ".html");
        outputFile.getParentFile().mkdirs();
        final String output = render2html(1440, 720, false);

        // patch template
        //output = output.replace("width: 2160,", "width: 2160, template: 'seaborn',");
        /*
        output = output.replace("</script>", "\n" +
                "document.getElementsByClassName(\"main-svg\")[0].setAttribute(\"style\", \"background: rgb(0, 0, 0);\");\n" +
                "document.getElementsByClassName(\"rangeslider-bg\")[0].setAttribute(\"fill\", \"#000\");\n" +
                "document.getElementsByClassName(\"xgrid crisp\")[0].setAttribute(\"fill\", \"#020\");\n" +
                "document.getElementsByClassName(\"ygrid crisp\")[0].setAttribute(\"fill\", \"#020\");\n" +
                "</script>");
         */
        try {
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8)) {
                writer.write(output);
            }
            try {
                if (openBrowser)
                    new Browser().browse(outputFile);
            } catch (final UnsupportedOperationException e) {}
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        return outputFile;
    }
}
