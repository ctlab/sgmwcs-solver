package ru.ifmo.ctddev.gmwcs.solver;

import ru.ifmo.ctddev.gmwcs.Signals;
import ru.ifmo.ctddev.gmwcs.graph.Edge;
import ru.ifmo.ctddev.gmwcs.graph.Graph;
import ru.ifmo.ctddev.gmwcs.graph.Node;
import ru.ifmo.ctddev.gmwcs.graph.Unit;

import java.util.*;

public class PSD {
    private Graph g;
    private Signals s;

    private double[] d;

    private Map<Node, Center> centers;

    private Map<Node, Path> paths;

    private Map<Center, Path> bestPaths;

    private Map<Integer, Path> dsuPaths;

    private double ub;

    private double sub;

    public double ub() {
        return ub;
    }

    private class Path {
        Node n;
        Path parent;
        Center c;
        Set<Integer> sigs;

        Path(Center c, Node n) {
            this.c = c;
            this.n = n;
            this.parent = this;
            this.sigs = new HashSet<>(s.negativeUnitSets(n, c.elem));
        }

        Path(Path p, Node n, Edge e) {
            this.c = p.c;
            this.parent = p;
            this.n = n;
            this.sigs = new HashSet<>(p.sigs);
            this.sigs.addAll(s.negativeUnitSets(n, e));
        }
    }

    private class Center {
        List<Integer> sigs = new ArrayList<>();
        Unit elem;

        Center(Unit elem) {
            this.elem = elem;
            if (elem instanceof Edge) {
                Edge e = (Edge) elem;
                Node u = g.getEdgeSource(e);
                Node v = g.getEdgeTarget(e);
                sigs.addAll(s.positiveUnitSets(e));
                d[u.getNum()] = 0;
                d[v.getNum()] = 0;
                centers.putIfAbsent(u, this);
                centers.putIfAbsent(v, this);
            } else {
                centers.put((Node) elem, this);
                sigs.addAll(s.positiveUnitSets(elem));
                d[elem.getNum()] = 0;
            }
        }
    }

    public PSD(Graph g, Signals s) {
        this.g = g;
        this.s = s;
        g.vertexSet();
        this.centers = new HashMap<>();
        this.paths = new HashMap<>();
        this.bestPaths = new HashMap<>();
        this.dsuPaths = new HashMap<>();
        d = new double[5000]; // TODO
        Arrays.fill(d, Double.POSITIVE_INFINITY);
    }

    public void decompose() {
        makeCenters();
        dijkstra();
        findBoundaries();
        filterBoundaries();
        double ub = dsuPaths.values().stream()
                .flatMap(p -> p.c.sigs.stream()).distinct()
                .mapToDouble(set -> s.weight(set)).sum();
        ub += dsuPaths.values().stream().distinct()
                .flatMap(p -> p.sigs.stream()).distinct()
                .mapToDouble(set -> s.weight(set)).sum();
        this.ub = ub;
    }

    private void findBoundaries() {
        for (Path p : paths.values()) {
            double ws = s.weightSum(p.c.sigs);
            Collection<Integer> eSigs = g.edgesOf(p.n).stream().filter(u -> u != p.c.elem)
                    .max(Comparator.comparingDouble(u -> s.weight(u)))
                    .map(u -> s.negativeUnitSets(u)).orElse(Collections.emptySet());
            p.sigs.addAll(eSigs);
            double r = s.weightSum(p.sigs) + ws;
            if (isBoundary(p) && r > 0) {
                Path prev = bestPaths.get(p.c);
                if (prev == null || s.weightSum(prev.sigs) < s.weightSum(p.sigs))
                    bestPaths.put(p.c, p);
            }
        }
    }

    private void filterBoundaries() {
        DSU dsu = new DSU(s);
        for (int sig = 0; sig < s.size(); sig++) {
            if (s.weight(sig) > 0) {
                sub += s.weight(sig);
                List<Unit> units = s.set(sig);
                for (Unit u : units) {
                    List<Node> nodes;
                    if (u instanceof Edge && g.containsEdge((Edge) u)) {
                        nodes = g.disjointVertices((Edge) u);
                    } else if (u instanceof Node && g.containsVertex((Node) u)) {
                        nodes = Collections.singletonList((Node) u);
                    } else continue;
                    for (Node n : nodes) {
                        Center c = centers.get(n);
                        if (c == null) continue;
                        List<Integer> sets = c.sigs;
                        int min = dsu.min(sets.get(0));
                        for (int set : sets) {
                            dsu.union(min, set);
                        }
                        min = dsu.min(min);
                        Path p = bestPaths.get(c);
                        if (p != null) {
                            updatePath(min, p);
                        } else {
                            centers.remove(n);
                        }
                    }
                }
            }
        }
        Set<Integer> usedSets = new HashSet<>();
        for (Map.Entry<Integer, Path> kvp : dsuPaths.entrySet()) {
            usedSets.addAll(kvp.getValue().c.sigs);
        }
        for (Node n : new HashSet<>(centers.keySet())) {
            Unit u = paths.get(n).c.elem;
            if (!usedSets.containsAll(s.positiveUnitSets(n, u))) {
                centers.remove(n);
            }
        }
    }


    private boolean isBoundary(Path p) {
        return g.vertexSet().size() == 1 || g.neighborListOf(p.n).stream()
                .anyMatch(n -> paths.get(n).c != p.c);
    }

    private void updatePath(int set, Path p) {
        Path prev = dsuPaths.get(set);
        if (prev == null || s.weightSum(p.sigs) > s.weightSum(prev.sigs)) {
            dsuPaths.put(set, p);
        }
    }

    private Center addCenter(Unit unit) {
        return new Center(unit);
    }

    private void makeCenters() {
        for (Node n : g.vertexSet()) {
            if (s.maxSum(n) > 0) {
                Center c = addCenter(n);
                paths.put(n, new Path(c, n));
            }
        }
        for (Edge e : g.edgeSet()) {
            if (s.maxSum(e) > 0) {
                Center c = addCenter(e);
                Node u = g.getEdgeSource(e), v = g.getEdgeTarget(e);
                paths.putIfAbsent(u, new Path(c, u));
                paths.putIfAbsent(v, new Path(c, v));
            }
        }
    }

    private void dijkstra() {
        PriorityQueue<Node> q = new PriorityQueue<>(
                Comparator.comparingDouble(n -> d[n.getNum()])
        );
        q.addAll(centers.keySet());
        while (!q.isEmpty()) {
            Node cur = q.poll();
            Path p = paths.get(cur);
            Center c = centers.get(cur);
            for (Node n : g.neighborListOf(cur)) {
                Edge e = g.getEdge(n, cur);
                if (s.weight(e) > 0) continue;
                double w = d[cur.getNum()] - s.minSum(e, n);
                if (d[n.getNum()] > w) {
                    paths.put(n, new Path(p, n, e));
                    d[n.getNum()] = w;
                    q.add(n);
                }
            }
        }
    }

    private void test(Node n) {
        // double ub =
    }
}
