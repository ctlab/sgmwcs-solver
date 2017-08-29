package ru.ifmo.ctddev.gmwcs.graph;

import ru.ifmo.ctddev.gmwcs.Signals;
import ru.ifmo.ctddev.gmwcs.solver.SolverException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

public class GraphPrinter {
    private final Graph graph;
    private final Signals signals;


    public GraphPrinter(Graph graph, Signals signals) {
        this.graph = graph;
        this.signals = signals;
    }

    private String formatSignal(int signal) {
        return "S" + (signal + 1);
    }

    private String printSignals(Unit unit) {
        List<Integer> ss = signals.unitSets(unit);
        String u = formatSignal(ss.get(0));
        return ss.subList(1, ss.size()).stream()
                .map(this::formatSignal)
                .reduce(u, (a, b) -> a + ", " + b);
    }

    private String formatUnit(String unit, String signals) {
        return unit + " [label=\"" + unit + "(" + signals + ")" + "\"]";
    }

    public void printGraph(String fileName) throws SolverException {
        List<String> output = new ArrayList<>();
        output.add("graph graphname {");
        for (Node v : graph.vertexSet()) {
            String str = (v.getNum() + 1) + "";
            output.add(formatUnit(str, printSignals(v)));
        }
        for (Edge e : graph.edgeSet()) {
            String str = (graph.getEdgeSource(e).num + 1)
                    + "--" + (graph.getEdgeTarget(e).num + 1);
            output.add(formatUnit(str, printSignals(e)));
        }
        output.add("node[shape=record]");
        String signs = "signals [label=\"{" + IntStream.range(1, signals.size())
                .mapToObj(this::formatSignal)
                .reduce("S1", (a, b) -> a + "|" + b) +
                "}|{" +
                IntStream.range(1, signals.size())
                        .mapToObj(s -> signals.weight(s) + "")
                        .reduce(signals.weight(0) + "", (a, b) -> a + "|" + b) +
                "}" +
                "\"]";
        output.add(signs);
        output.add("}");
        Path file = Paths.get(fileName);
        try {
            Files.write(file, output);
        } catch (IOException e) {
            throw new SolverException("Couldn't print graph");
        }
    }

}
