package ru.ifmo.ctddev.gmwcs.solver;

import ru.ifmo.ctddev.gmwcs.Signals;
import ru.ifmo.ctddev.gmwcs.graph.Edge;
import ru.ifmo.ctddev.gmwcs.graph.Graph;
import ru.ifmo.ctddev.gmwcs.graph.Node;
import ru.ifmo.ctddev.gmwcs.graph.Unit;

import java.util.*;

class Dijkstra {
    private Graph graph;
    private Signals signals;
    private PriorityQueue<Node> q;
    private Map<Node, Double> d;
    private Map<Unit, Set<Unit>> p;

    private Set<Unit> currentUnits;

    private double currentWeight() {
        return -signals.minSum(currentUnits);
    }

    private double weight(Unit unit) {
        return d.getOrDefault(unit, Double.MAX_VALUE);
    }

    Dijkstra(Graph graph, Signals signals) {
        this.graph = graph;
        this.signals = signals;
    }


    Set<Edge> solve(Node u, List<Node> neighbors) {
        d = new HashMap<>();
        p = new HashMap<>();
        q = new PriorityQueue<>(Comparator.comparingDouble(this::weight));
        currentUnits = new HashSet<>();
        q.add(u);
        d.put(u, 0.0);
        p.put(u, new HashSet<>());
        Node cur;
        while ((cur = q.poll()) != null) {
            currentUnits = p.getOrDefault(cur, new HashSet<>());
            for (Node node : graph.neighborListOf(cur)) {
                Edge edge = graph.getEdge(node, cur);
                boolean added1 = currentUnits.add(node);
                boolean added2 = currentUnits.add(edge);
                double cw = currentWeight();
                if (cw < weight(node)) {
                    q.remove(node);
                    d.put(node, cw);
                    p.put(node, new HashSet<>());
                    p.get(node).addAll(currentUnits);
                    q.add(node);
                }
                if (added1) currentUnits.remove(node);
                if (added2) currentUnits.remove(edge);
            }
        }
        Set<Edge> res = new HashSet<>();
        neighbors.forEach(n -> {
            List<Edge> edges = graph.getAllEdges(n, u);
            for (Edge e : edges)
                if (!p.get(n).contains(e))
                    res.add(e);
        });
        return res;
    }
}
