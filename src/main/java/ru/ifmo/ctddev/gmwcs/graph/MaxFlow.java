package ru.ifmo.ctddev.gmwcs.graph;

import ru.ifmo.ctddev.gmwcs.Pair;

import java.util.*;

public class MaxFlow {
    private int n;
    private List<List<Integer>> adj;
    private List<List<Integer>> backIndex;
    private List<List<Double>> capacity;
    private List<Map<Integer, Integer>> indices;

    public MaxFlow(int n) {
        this.n = n;
        adj = new ArrayList<>();
        backIndex = new ArrayList<>(n);
        capacity = new ArrayList<>(n);
        indices = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            adj.add(new ArrayList<>());
            backIndex.add(new ArrayList<>());
            capacity.add(new ArrayList<>());
            indices.add(new HashMap<>());
        }
    }

    public void addEdge(int i, int j) {
        adj.get(i).add(j);
        backIndex.get(i).add(-1);
        capacity.get(i).add(0.0);
        indices.get(i).put(j, adj.get(i).size() - 1);
    }

    public void addBiEdge(int i, int j) {
        addEdge(i, j);
        addEdge(j, i);
        backIndex.get(i).set(backIndex.get(i).size() - 1, adj.get(j).size() - 1);
        backIndex.get(j).set(backIndex.get(j).size() - 1, adj.get(i).size() - 1);
    }

    public void setCapacity(int i, int j, double c) {
        capacity.get(i).set(indices.get(i).get(j), c);
    }

    public List<Pair<Integer, Integer>> computeMinCut(int s, int t, double threshold) {
        List<List<Double>> flow = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            flow.add(new ArrayList<>());
            for (int j = 0; j < adj.get(i).size(); j++) {
                flow.get(i).add(0.0);
            }
        }
        double maxFlow = 0.0;
        List<Double> label;
        MAIN:
        while (true) {
            if (maxFlow >= threshold) {
                return null;
            }
            List<Integer> parent = new ArrayList<>(n);
            List<Integer> parentIndex = new ArrayList<>(n);
            List<Double> min = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                parent.add(-1);
                parentIndex.add(-1);
                min.add(Double.POSITIVE_INFINITY);
            }
            Queue<Integer> q = new ArrayDeque<>();
            q.add(s);
            PATH:
            while (true) {
                if (q.isEmpty()) {
                    label = min;
                    break MAIN;
                }
                int v = q.poll();
                for (int i = 0; i < adj.get(v).size(); i++) {
                    int u = adj.get(v).get(i);
                    if (rcap(flow, v, i) > 0.0) {
                        if (min.get(u) == Double.POSITIVE_INFINITY) {
                            min.set(u, Math.min(min.get(v), rcap(flow, v, i)));
                            q.add(u);
                            parent.set(u, v);
                            parentIndex.set(u, i);
                        }
                    }
                    if (u == t) {
                        int k = t;
                        double f = min.get(t);
                        while (k != s) {
                            int p = parent.get(k);
                            int pi = parentIndex.get(k);
                            push(p, pi, f, flow);
                            k = p;
                        }
                        break PATH;
                    }
                }
            }
        }
        return getResult(label);
    }

    private List<Pair<Integer, Integer>> getResult(List<Double> label) {
        List<Pair<Integer, Integer>> res = new ArrayList<>();
        for (int v = 0; v < n; v++) {
            if (label.get(v) == Double.POSITIVE_INFINITY) {
                continue;
            }
            for (int u : adj.get(v)) {
                if (label.get(u) == Double.POSITIVE_INFINITY) {
                    res.add(new Pair<>(v, u));
                }
            }
        }
        return res;
    }

    private double rcap(List<List<Double>> flow, int v, int i) {
        double res = 0.0;
        int u = adj.get(v).get(i);
        int j = backIndex.get(v).get(i);
        if (backIndex.get(v).get(i) != -1) {
            res += flow.get(u).get(j);
        }
        return res + capacity.get(v).get(i) - flow.get(v).get(i);
    }

    private void push(int v, int i, double cap, List<List<Double>> flow) {
        int u = adj.get(v).get(i);
        int j = backIndex.get(v).get(i);
        double can = Math.min(cap, flow.get(u).get(j));
        flow.get(u).set(j, flow.get(u).get(j) - can);
        cap -= can;
        if (cap > 0) {
            flow.get(v).set(i, flow.get(v).get(i) + cap);
        }
    }
}
