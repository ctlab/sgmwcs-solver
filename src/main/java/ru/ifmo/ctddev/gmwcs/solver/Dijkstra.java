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
    private Map<Node, Double> d;
    private Map<Unit, Set<Integer>> p;
    private Map<Set<Integer>, Double> cache;
    private Map<Unit, List<Integer>> unitSetHash;

    private Set<Integer> currentSignals;

    private double currentWeight() {
        return -signals.weightSum(currentSignals);
        //return cache.computeIfAbsent(currentSignals, s -> -signals.weightSum(s));
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

    private void solve(Node u, Collection<Node> dest) {
        dest = new HashSet<>(dest);
        d = new HashMap<>();
        p = new HashMap<>();
        PriorityQueue<Node> q = new PriorityQueue<>(Comparator.comparingDouble(this::weight));
        cache = new HashMap<>();
        currentSignals = new HashSet<>();
        unitSetHash = new HashMap<>();
        q.add(u);
        d.put(u, 0.0);
        p.put(u, new HashSet<>());
        Node cur;
        List<Integer> negE, negN, addedE = new ArrayList<>(), addedN = new ArrayList<>();
        while (!dest.isEmpty() && (cur = q.poll()) != null) {
            currentSignals = p.getOrDefault(cur, new HashSet<>());
            double cw = currentWeight();
            for (Node node : graph.neighborListOf(cur)) {
                negN = negativeUnitSets(node);
                double sumN = 0;
                for (int i : negN) {
                    if (currentSignals.add(i)) {
                        addedN.add(i);
                        sumN -= signals.weight(i);
                    }
                }
                cw += sumN;
                for (Edge edge : graph.getAllEdges(node, cur)) {
                    negE = negativeUnitSets(edge);
                    double sumE = 0;
                    for (int i : negE) {
                        if (currentSignals.add(i)) {
                            addedE.add(i);
                            sumE -= signals.weight(i);
                        }
                    }
                    cw += sumE;
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
                dest.remove(node);
            }
        }
    }

    boolean solveNP(Node u) {
        assert graph.degreeOf(u) == 2;
        List<Node> nbors = graph.neighborListOf(u);
        if (nbors.size() != 2) return false;
        Node v_1 = nbors.get(0), v_2 = nbors.get(1);
        solve(v_1, Collections.singletonList(v_2));
        Set<Integer> unitSets = new HashSet<>(signals.negativeUnitSets(u));
        unitSets.addAll(signals.negativeUnitSets(graph.edgesOf(u)));
        return !p.get(v_2).containsAll(unitSets);
    }

    Set<Edge> solveNE(Node u, List<Node> neighbors) {
        solve(u, neighbors);
        Set<Edge> res = new HashSet<>();
        neighbors.forEach(n -> {
            List<Edge> edges = graph.getAllEdges(n, u);
            for (Edge e : edges)
                if (!p.get(n).containsAll(signals.unitSets(e)))
                    res.add(e);
        });
        return res;
    }

    boolean solveClique(double p, Set<Node> k) {
        if (k.size() < 2) return false;
        Map<Node, Map<Node, Double>> distances = new HashMap<>();
        for (Node v : k) {
            solve(v, k);
            distances.putIfAbsent(v, new HashMap<>());
            Map<Node, Double> cd = distances.get(v);
            for (Node n: k) {
                if (n == v) continue;
                Set<Integer> path = this.p.get(n);
                if (path == null) return false;
                path.addAll(signals.negativeUnitSets(v));
                cd.put(n, -signals.weightSum(path));
            }
        }
        Set<Set<Node>> subsets = Utils.subsets(k);
        for (Set<Node> subset: subsets) {
            if (subset.size() < 2) continue;
            if (new MST(subset, distances).result() + p > 0) {
                return false;
            }
        }
        return true;
    }
}