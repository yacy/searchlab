/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// taken from
// https://www.programcreek.com/java-api-examples/?code=jtablesaw%2Ftablesaw%2Ftablesaw-master%2Fjsplot%2Fsrc%2Ftest%2Fjava%2Ftech%2Ftablesaw%2Fplotly%2FScatterTest.java#


package eu.searchlab.storage.table;

import java.io.File;

import tech.tablesaw.api.DoubleColumn;
import tech.tablesaw.api.Table;
import tech.tablesaw.plotly.Plot;
import tech.tablesaw.plotly.api.Scatter3DPlot;
import tech.tablesaw.plotly.components.Axis;
import tech.tablesaw.plotly.components.Figure;
import tech.tablesaw.plotly.components.Font;
import tech.tablesaw.plotly.components.HoverLabel;
import tech.tablesaw.plotly.components.Layout;
import tech.tablesaw.plotly.components.Marker;
import tech.tablesaw.plotly.components.Symbol;
import tech.tablesaw.plotly.components.TickSettings;
import tech.tablesaw.plotly.traces.BarTrace;
import tech.tablesaw.plotly.traces.BoxTrace;
import tech.tablesaw.plotly.traces.HistogramTrace;
import tech.tablesaw.plotly.traces.PieTrace;
import tech.tablesaw.plotly.traces.Scatter3DTrace;
import tech.tablesaw.plotly.traces.ScatterTrace;

public class PlotlyTest {

    private final static String[] text = {"acc", "dnax", "lc", "hc", "seq"};
    private final static double[] vals = {1, 6, 14, 25, 39};

    private final static double[] x = {1, 2, 3, 4, 5, 6};
    private final static double[] y = {0, 1, 6, 14, 25, 39};
    private final static double[] z = {-23, 11, -2, -7, 0.324, -11};

    private final static String[] labels = {"apple", "bike", "car", "dog", "elephant", "fox"};

    private final static void showDiamond() {
        final ScatterTrace trace =
                ScatterTrace.builder(x, y)
                .marker(Marker.builder().size(12.0).symbol(Symbol.DIAMOND_TALL).color("#c68486").build())
                .mode(ScatterTrace.Mode.MARKERS)
                .text(labels)
                .build();
        final Figure figure = new Figure(trace);
        Plot.show(figure, new File("testoutput/showDiamond.html"));
    }

    private final static void showLine() {
        final Layout layout =
                Layout.builder()
                .title("test")
                .titleFont(Font.builder().size(32).color("green").build())
                .showLegend(true)
                .height(700)
                .width(1200)
                .build();
        final ScatterTrace trace =
                ScatterTrace.builder(x, y)
                .mode(ScatterTrace.Mode.LINE)
                .hoverLabel(HoverLabel.builder().bgColor("red").font(Font.builder().size(24).build()).build())
                .showLegend(true)
                .build();
        final Figure figure = new Figure(layout, trace);
        Plot.show(figure, new File("testoutput/showLine.html"));
    }

    private final static void showLineWithArrayTicks() {
        final double[] x1 = {13, 14, 15, 16, 17, 18};
        final double[] y1 = {0, 1, 6, 14, 25, 39};
        final double[] x2 = {7, 9, 11, 13};
        final double[] y2 = {0, 1, 6, 14};
        final Axis.Spikes spikes = Axis.Spikes.builder().color("blue").dash("solid").thickness(1).build();
        final TickSettings tickSettings =
                TickSettings.builder()
                .tickMode(TickSettings.TickMode.ARRAY)
                .showTickLabels(true)
                .arrayTicks(vals, text)
                .build();
        final Axis yAxis =
                Axis.builder()
                .title("stages")
                .tickSettings(tickSettings)
                .autoRange(Axis.AutoRange.REVERSED)
                .gridWidth(1)
                .gridColor("grey")
                .spikes(spikes)
                .build();
        final Layout layout =
                Layout.builder()
                .title("train time")
                .yAxis(yAxis)
                .xAxis(Axis.builder().spikes(spikes).build())
                .height(700)
                .width(1200)
                .hoverMode(Layout.HoverMode.CLOSEST)
                .build();
        final ScatterTrace trace1 = ScatterTrace.builder(x, y).mode(ScatterTrace.Mode.LINE).build();
        final ScatterTrace trace3 = ScatterTrace.builder(x1, y1).mode(ScatterTrace.Mode.LINE).build();
        final ScatterTrace trace2 = ScatterTrace.builder(x2, y2).mode(ScatterTrace.Mode.LINE).build();
        final Figure figure = new Figure(layout, trace1, trace2, trace3);
        Plot.show(figure, new File("testoutput/showLineWithArrayTicks.html"));
    }

    private final static void showLineAndMarkers() {
        final ScatterTrace trace = ScatterTrace.builder(x, y).mode(ScatterTrace.Mode.LINE_AND_MARKERS).build();
        final Figure figure = new Figure(trace);
        Plot.show(figure, new File("testoutput/showLineAndMarkers.html"));
    }

    private final static void showText() {
        final ScatterTrace trace = ScatterTrace.builder(x, y).mode(ScatterTrace.Mode.TEXT).text(labels).build();
        final Figure figure = new Figure(trace);
        Plot.show(figure, new File("testoutput/showText.html"));
    }

    private final static void showLineAndMarkers2() {
        final ScatterTrace trace1 = ScatterTrace.builder(x, y).mode(ScatterTrace.Mode.LINE).build();
        final ScatterTrace trace2 = ScatterTrace
                .builder(x, y)
                .mode(ScatterTrace.Mode.MARKERS)
                .text(labels)
                .build();
        final Figure figure = new Figure(trace1, trace2);
        Plot.show(figure, new File("testoutput/showLineAndText.html"));
    }

    private final static void show3DScatter() {
        final Scatter3DTrace trace = Scatter3DTrace.builder(x, y, z).mode(Scatter3DTrace.Mode.MARKERS).text(labels).build();
        final Layout layout = Layout.builder().xAxis(Axis.builder().title("x title").build()).build();
        Plot.show(new Figure(layout, trace), new File("testoutput/show3DScatter.html"));
    }

    private final static void show3DLineAndMarkers() {
        final Scatter3DTrace trace = Scatter3DTrace.builder(x, y, z).mode(Scatter3DTrace.Mode.LINE_AND_MARKERS).build();
        final Layout layout = Layout.builder().xAxis(Axis.builder().title("x title").build()).build();
        Plot.show(new Figure(layout, trace), new File("testoutput/show3DLineAndMarkers.html"));
    }

    private final static void show3DText() {
        final Scatter3DTrace trace = Scatter3DTrace.builder(x, y, z).mode(Scatter3DTrace.Mode.TEXT).text(labels).build();
        Plot.show(new Figure(trace), new File("testoutput/show3DText.html"));
    }

    private final static void showScatter3DPlot() {
        final DoubleColumn xData = DoubleColumn.create("x", new double[] {2, 2, 1});
        final DoubleColumn yData = DoubleColumn.create("y", new double[] {1, 2, 3});
        final DoubleColumn zData = DoubleColumn.create("z", new double[] {1, 4, 1});
        final Table data = Table.create().addColumns(xData, yData, zData);
        final Figure figure = Scatter3DPlot.create("3D plot", data, "x", "y", "z");
        Plot.show(figure, new File("testoutput/showScatter3DPlot.html"));
    }

    private final static void showBox() {
        final Object[] x = {"sheep", "cows", "fish", "tree sloths", "sheep", "cows", "fish", "tree sloths", "sheep", "cows", "fish", "tree sloths"};
        final double[] y = {1, 4, 9, 16, 3, 6, 8, 8, 2, 4, 7, 11};
        final BoxTrace trace = BoxTrace.builder(x, y).build();
        final Figure figure = new Figure(trace);
        Plot.show(figure, new File("testoutput/showBox.html"));
    }

    private final static void showPie() {
        final Object[] x = {"sheep", "cows", "fish", "tree sloths"};
        final double[] y = {1, 4, 9, 16};
        final PieTrace trace = PieTrace.builder(x, y).build();
        final Figure figure = new Figure(trace);
        Plot.show(figure, new File("testoutput/showPie.html"));
    }

    private final static void showBubbles() {
        final double[] x = {1, 2, 3, 4, 5, 6};
        final double[] y = {0, 1, 6, 14, 25, 39};
        final double[] size = {10, 33, 21, 40, 28, 16};

        final ScatterTrace trace =
                ScatterTrace.builder(x, y)
                .mode(ScatterTrace.Mode.MARKERS)
                .marker(
                        Marker.builder()
                        .size(size)
                        .colorScale(Marker.Palette.CIVIDIS)
                        .opacity(.5)
                        .showScale(true)
                        .symbol(Symbol.DIAMOND_TALL)
                        .build())
                .build();

        Plot.show(new Figure(trace), new File("testoutput/showBubbles.html"));
    }

    private final static void showHistogram() {
        final double[] y1 = {1, 4, 9, 16, 11, 4, -1, 20, 4, 7, 9, 12, 8, 6, 28, 12};
        final double[] y2 = {3, 11, 19, 14, 11, 14, 5, 24, -4, 10, 15, 6, 5, 18};
        final Layout layout = Layout.builder().barMode(Layout.BarMode.OVERLAY).build();
        final HistogramTrace trace1 = HistogramTrace.builder(y1).opacity(.75).build();
        final HistogramTrace trace2 = HistogramTrace.builder(y2).opacity(.75).build();
        Plot.show(new Figure(layout, trace1, trace2), new File("testoutput/showHistogram.html"));
    }

    private final static void showBar() {
        final Object[] x = {"sheep", "cows", "fish", "tree sloths"};
        final double[] y = {1, 4, 9, 16};
        final BarTrace trace = BarTrace.builder(x, y).build();
        final Figure figure = new Figure(trace);
        Plot.show(figure, new File("testoutput/showBar.html"));
    }

    public static void main(String[] args) {
        final File testoutput = new File("testoutput");
        if (!testoutput.exists()) testoutput.mkdir();
        showDiamond();
        showLine();
        showLineWithArrayTicks();
        showLineAndMarkers();
        showText();
        showLineAndMarkers2();
        show3DScatter();
        show3DText();
        show3DLineAndMarkers();
        showScatter3DPlot();
        showBox();
        showPie();
        showBubbles();
        showHistogram();
        showBar();
    }
}