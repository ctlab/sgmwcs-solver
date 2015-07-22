package ru.ifmo.ctddev.gmwcs.solver;

import org.jgrapht.UndirectedGraph;
import org.jgrapht.graph.UndirectedSubgraph;
import ru.ifmo.ctddev.gmwcs.LDSU;
import ru.ifmo.ctddev.gmwcs.graph.Edge;
import ru.ifmo.ctddev.gmwcs.graph.Node;
import ru.ifmo.ctddev.gmwcs.graph.Unit;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public class Utils {
    public static double sum(Collection<? extends Unit> units, LDSU<Unit> synonyms) {
        if (units == null) {
            return 0;
        }
        double result = 0;
        Set<Unit> visited = new LinkedHashSet<>();
        for (Unit unit : units) {
            if (visited.contains(unit)) {
                continue;
            }
            visited.addAll(synonyms.listOf(unit));
            result += unit.getWeight();
        }
        return result;
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
