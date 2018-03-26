package ru.ifmo.ctddev.gmwcs.solver;

import ru.ifmo.ctddev.gmwcs.graph.Node;

import java.util.*;

public class NaiveMST {
    private final Set<Node> nodes;
    private final Map<Node, Map<Node, Double>> weights;

    private boolean solved = false;
    private double res;

    public NaiveMST(Set<Node> nodes, Map<Node, Map<Node, Double>> weights) {
        this.nodes = nodes;
        this.weights = weights;
    }

    private void solve() {
        Set<Node> queue = new HashSet<>(nodes);
        Set<Node> t = new HashSet<>();
        Node cur = queue.iterator().next();
        queue.remove(cur);
        t.add(cur);
        res = 0;
        while (!queue.isEmpty()) {
            Node next = null;
            Double cw = Double.MAX_VALUE;
            for (Node n : t) {
                Map<Node, Double> neighbors = weights.get(n);
                for (Map.Entry<Node, Double> cand : neighbors.entrySet()) {
                    Node node = cand.getKey();
                    double w = cand.getValue();
                    if (nodes.contains(node) && !t.contains(node) && w < cw) {
                        cw = w;
                        next = node;
                    }
                }
            }
            res += cw;
            t.add(next);
            queue.remove(next);
        }
    }

    public double result() {
        if (!solved) {
            solve();
        }
        return res;
    }

}
