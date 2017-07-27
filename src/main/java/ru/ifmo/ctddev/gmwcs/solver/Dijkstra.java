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

    private double weight2(Unit unit) {
        boolean added = currentUnits.add(unit);
        double cw = currentWeight();
        if (added)
            currentUnits.remove(unit);
        return cw;
    }

    private double weight(Unit unit) {
        return d.getOrDefault(unit, Double.MAX_VALUE);
    }

    public Dijkstra(Graph graph, Signals signals) {
        this.graph = graph;
        this.signals = signals;
        d = new HashMap<>();
        p = new HashMap<>();
        q = new PriorityQueue<>(Comparator.comparingDouble(this::weight));
        currentUnits = new HashSet<>();
    }


    public boolean solve(Node u, Node v, double edgeWeight) {
        q.add(u);
        p.put(u, new HashSet<>());
        p.get(u).addAll(currentUnits);
        Node cur;
        while ((cur = q.poll()) != null) {
            currentUnits = p.getOrDefault(cur, new HashSet<>());
            for (Node node : graph.neighborListOf(cur)) {
                List<Edge> edges = graph.getAllEdges(node, cur);
                boolean added1 = currentUnits.add(node);
                boolean added2 = currentUnits.add(edges.get(0));
                if (currentWeight() < weight(node)) {
                    q.remove(node);
                    d.put(node, currentWeight());
                    p.put(node, new HashSet<>());
                    p.get(node).addAll(currentUnits);
                    q.add(node);
                }
                if (added1) currentUnits.remove(node);
                if (added2) currentUnits.remove(edges.get(0));
            }
        }
        return d.containsKey(v) && -edgeWeight >= d.get(v);
    }


}
