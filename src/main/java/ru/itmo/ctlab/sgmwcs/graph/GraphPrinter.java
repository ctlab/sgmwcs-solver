package ru.itmo.ctlab.sgmwcs.graph;

import ru.itmo.ctlab.sgmwcs.Signals;
import ru.itmo.ctlab.sgmwcs.solver.SolverException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class GraphPrinter {
    private final Graph graph;
    private final Signals signals;
    private final Set<Edge> cutEdges;


    public GraphPrinter(Graph graph, Signals signals, Set<Edge> cutEdges) {
        this.graph = graph;
        this.signals = signals;
        this.cutEdges = cutEdges;
    }

    public GraphPrinter(Graph graph, Signals signals) {
        this(graph, signals, Collections.emptySet());
    }


    private String formatSignal(int signal) {
        return "S" + signal;
    }

    private String printSignals(Unit unit) {
        List<Integer> ss = signals.unitSets(unit);
        String u = formatSignal(ss.get(0));
        return ss.subList(1, ss.size()).stream()
                .map(this::formatSignal)
                .reduce(u, (a, b) -> a + ", " + b);
    }

    private String formatUnit(String unit, String signals, String color) {
        return unit + " [label=\"" + unit + "(" + signals + ")" + "\"" + color + "]";
    }

    private String formatUnit(String unit, String signals) {
        return unit + (this.signals == null ? "" : " [label=\"" + unit + "(" + signals + ")" + "\"]");
    }

    private String weight(Unit unit) {
        return this.signals == null ? unit.getNum() + "" : signals.weight(unit) + "";
    }

    public void printGraph(String fileName) throws SolverException {
        printGraph(fileName, true);
    }

    public void toTSV(String nodesFile, String edgesFile) throws SolverException {
        Path nodes = Paths.get(nodesFile);
        List<String> nodesList = new ArrayList<>();
        Path edges = Paths.get(edgesFile);
        List<String> edgesList = new ArrayList<>();
        nodesList.add("name\tsignals\tscore\tscore2");
        edgesList.add("source\ttarget\tsignals\tscore\tscore2");
        for (Unit u : graph.units()) {
            String sigString = String.join(",", signals.unitSets(u).stream()
                    .map(s -> "S" + s).collect(Collectors.toList()));
            double w = signals.weight(u);
            double rounded = ((double)(int) (w * 100)) / 100;
            String str = sigString + '\t' + w
                    + '\t' + rounded + '\n';
            if (u instanceof Edge) {
                Edge e = (Edge) u;
                str = (graph.getEdgeSource(e).num)
                        + "\t" + (graph.getEdgeTarget(e).num) + "\t" + str;
                edgesList.add(str);
            } else {
                str = u.getNum() + "\t" + str;
                nodesList.add(str);
            }
        }
        try {
            Files.write(nodes, nodesList);
            Files.write(edges, edgesList);
        } catch (IOException e) {
            throw new SolverException("Couldn't print graph");
        }

    }

    public void printGraph(String fileName, boolean sigLabels) throws SolverException {
        List<String> output = new ArrayList<>();
        output.add("graph graphname {");
        for (Node v : graph.vertexSet()) {
            String str = v.getNum() + "";
            output.add(formatUnit(str, sigLabels ? printSignals(v) : weight(v)));
        }
        for (Edge e : graph.edgeSet()) {
            String str = (graph.getEdgeSource(e).num)
                    + "--" + (graph.getEdgeTarget(e).num);
            if (cutEdges.contains(e)) {
                output.add(formatUnit(str, sigLabels ? printSignals(e) : weight(e), " dir = none color=\"red\""));
            } else {
                output.add(formatUnit(str, sigLabels ? printSignals(e) : weight(e)));
            }
        }
        if (sigLabels) {
            output.add("node[shape=record]");
            String signs = "signals [label=\"{" + IntStream.range(1, signals.size())
                    .mapToObj(this::formatSignal)
                    .reduce("S0", (a, b) -> a + "|" + b) +
                    "}|{" +
                    IntStream.range(1, signals.size())
                            .mapToObj(s -> signals.weight(s) + "")
                            .reduce(signals.weight(0) + "", (a, b) -> a + "|" + b) +
                    "}" +
                    "\"]";
            output.add(signs);
        }
        output.add("}");
        Path file = Paths.get(fileName);
        try {
            Files.write(file, output);
        } catch (IOException e) {
            throw new SolverException("Couldn't print graph");
        }
    }


}
