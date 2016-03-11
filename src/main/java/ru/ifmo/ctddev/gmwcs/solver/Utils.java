package ru.ifmo.ctddev.gmwcs.solver;

import org.jgrapht.UndirectedGraph;
import org.jgrapht.graph.UndirectedSubgraph;
import ru.ifmo.ctddev.gmwcs.LDSU;
import ru.ifmo.ctddev.gmwcs.graph.Edge;
import ru.ifmo.ctddev.gmwcs.graph.Node;
import ru.ifmo.ctddev.gmwcs.graph.Unit;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

public class Utils {
    public static double sum(Collection<? extends Unit> units, LDSU<Unit> synonyms) {
        if (units == null) {
            return 0;
        }
        double result = 0;
        Set<Unit> us = new HashSet<>();
        us.addAll(units);
        for (int i = 0; i < synonyms.size(); i++) {
            List<Unit> set = synonyms.set(i);
            for (Unit unit : set) {
                if (us.contains(unit)) {
                    result += synonyms.weight(i);
                    break;
                }
            }
        }
        return result;
    }

    public static UndirectedGraph<Node, Edge> subgraph(UndirectedGraph<Node, Edge> source, Set<Node> nodes) {
        Set<Edge> edges = new TreeSet<>();
        for (Edge edge : source.edgeSet()) {
            if (nodes.contains(source.getEdgeSource(edge)) && nodes.contains(source.getEdgeTarget(edge))) {
                edges.add(edge);
            }
        }
        Set<Node> sortedNodes = new TreeSet<>();
        sortedNodes.addAll(nodes);
        return new UndirectedSubgraph<>(source, sortedNodes, edges);
    }

    private static String dotColor(Unit unit, List<Unit> expected, List<Unit> actual) {
        if (actual != null && expected.contains(unit) && actual.contains(unit)) {
            return "YELLOW";
        }
        if (expected.contains(unit)) {
            return "GREEN";
        }
        if (actual != null && actual.contains(unit)) {
            return "RED";
        }
        return "BLACK";
    }

    public static void toXdot(UndirectedGraph<Node, Edge> graph, List<Unit> expected, List<Unit> actual) throws IOException {
        Runtime runtime = Runtime.getRuntime();
        Process process = runtime.exec("xdot");
        try (PrintWriter os = new PrintWriter(process.getOutputStream())) {
            os.println("graph test {");
            for (Node node : graph.vertexSet()) {
                os.print(node.getNum() + " [label = \"" + node.getNum() + ", " + node.getWeight() + "\" ");
                os.println("color=" + dotColor(node, expected, actual) + "]");
            }
            for (Edge edge : graph.edgeSet()) {
                Node from = graph.getEdgeSource(edge);
                Node to = graph.getEdgeTarget(edge);
                os.print(from.getNum() + "--" + to.getNum() + "[label = \"" + edge.getNum() + ", " +
                        edge.getWeight() + "\" ");
                os.println("color=" + dotColor(edge, expected, actual) + "]");
            }
            os.println("}");
            os.flush();
        }
        try {
            process.waitFor();
        } catch (InterruptedException ignored) {
        }
    }
}
