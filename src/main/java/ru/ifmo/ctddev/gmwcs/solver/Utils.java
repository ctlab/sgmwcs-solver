package ru.ifmo.ctddev.gmwcs.solver;

import org.jgrapht.UndirectedGraph;
import org.jgrapht.graph.UndirectedSubgraph;
import ru.ifmo.ctddev.gmwcs.graph.Edge;
import ru.ifmo.ctddev.gmwcs.graph.Node;
import ru.ifmo.ctddev.gmwcs.graph.Unit;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class Utils {
    public static double sum(List<Unit> units) {
        if (units == null) {
            return 0;
        }
        double res = 0;
        for (Unit unit : units) {
            res += unit.getWeight();
        }
        return res;
    }

    public static UndirectedGraph<Node, Edge> subgraph(UndirectedGraph<Node, Edge> source, Set<Node> nodes) {
        Set<Edge> edges = new LinkedHashSet<>();
        for (Edge edge : source.edgeSet()) {
            if (nodes.contains(source.getEdgeSource(edge)) && nodes.contains(source.getEdgeTarget(edge))) {
                edges.add(edge);
            }
        }
        return new UndirectedSubgraph<>(source, nodes, edges);
    }
}
