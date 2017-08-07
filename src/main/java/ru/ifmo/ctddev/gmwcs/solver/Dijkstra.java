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
    private Map<Unit, Set<Integer>> p;
    private Map<Set<Integer>, Double> hash;
    private Map<Unit, List<Integer>> unitSetHash;

    private Set<Integer> currentSignals;

    private double currentWeight() {
        return hash.computeIfAbsent(currentSignals, s -> -signals.weightSum(s));
    }

    private List<Integer> negativeUnitSets(Unit unit) {
        return unitSetHash.computeIfAbsent(unit, u -> signals.negativeUnitSets(u));
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
        hash = new HashMap<>();
        currentSignals = new HashSet<>();
        unitSetHash = new HashMap<>();
        q.add(u);
        d.put(u, 0.0);
        p.put(u, new HashSet<>());
        Node cur;
        List<Integer> negE, negN, addedE = new ArrayList<>(), addedN = new ArrayList<>();
        while ((cur = q.poll()) != null) {
            currentSignals = p.getOrDefault(cur, new HashSet<>());
            double cw = currentWeight();
            for (Node node : graph.neighborListOf(cur)) {
                negN = negativeUnitSets(node);
                double sumN = 0;
                for (int i : negN) {
                    if (currentSignals.add(i)) {
                        addedN.add(i);
                        cw -= signals.weight(i);
                        sumN -= signals.weight(i);
                    }
                }
                for (Edge edge : graph.getAllEdges(node, cur)) {
                    negE = negativeUnitSets(edge);
                    double sumE = 0;
                    for (int i : negE) {
                        if (currentSignals.add(i)) {
                            addedE.add(i);
                            cw -= signals.weight(i);
                            sumE -= signals.weight(i);
                        }
                    }
                    if (cw < weight(node)) {
                        q.remove(node);
                        d.put(node, cw);
                        p.put(node, new HashSet<>());
                        p.get(node).addAll(currentSignals);
                        q.add(node);
                    }
                    currentSignals.removeAll(addedE);
                    addedE.clear();
                    cw -= sumE;
                }
                currentSignals.removeAll(addedN);
                addedN.clear();
                cw -= sumN;
            }
        }
        Set<Edge> res = new HashSet<>();
        neighbors.forEach(n -> {
            List<Edge> edges = graph.getAllEdges(n, u);
            for (Edge e : edges)
                if (signals.minSum(e) <= 0 && !p.get(n).containsAll(signals.unitSets(e)))
                    res.add(e);
        });
        return res;
    }
}