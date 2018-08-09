package ru.ifmo.ctddev.gmwcs.solver;

import ru.ifmo.ctddev.gmwcs.Signals;
import ru.ifmo.ctddev.gmwcs.graph.Edge;
import ru.ifmo.ctddev.gmwcs.graph.Graph;
import ru.ifmo.ctddev.gmwcs.graph.Node;
import ru.ifmo.ctddev.gmwcs.graph.Unit;

import java.util.*;
import java.util.stream.Stream;

public class PSD {
    private Graph g;
    private Signals s;

    private DSU dsu;

    private double[] d;

    private Map<Node, Center> centers;

    private Map<Node, Path> paths;

    private Map<Center, Path> bestPaths;

    private Map<Integer, Path> dsuPaths;

    private Set<Node> forced;

    public final boolean solutionIsTree;

    private double ub;

    private double sub;

    public double ub() {
        return ub;
    }

    public double sub() {
        return sub;
    }

    public boolean hasCenter(Path p) {
        return dsuPaths.containsKey(dsu.min(p.c.sigs.get(0)));
    }

    public Map<Center, List<Path>> centerPaths() {
        Map<Center, List<Path>> res = new HashMap<>();
        for (Path p: paths.values()) {
            if (hasCenter(p)) {
                res.putIfAbsent(p.c, new ArrayList<>());
                res.get(p.c).add(p);
            }
        }
        return res;
    }

    public Map<Node, Path> getPaths() {
        return Collections.unmodifiableMap(paths);
    }
    public class Path {
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

    public class Center {
        List<Integer> sigs = new ArrayList<>();
        Unit elem;

        Center(Unit elem) {
            this.elem = elem;
            if (elem instanceof Edge) {
                Edge e = (Edge) elem;
                Node u = g.getEdgeSource(e);
                Node v = g.getEdgeTarget(e);
                sigs.addAll(s.positiveUnitSets(e));
                d[u.getNum()] = -s.weight(u);
                d[v.getNum()] = -s.weight(v);
                centers.putIfAbsent(u, this);
                centers.putIfAbsent(v, this);
            } else {
                centers.put((Node) elem, this);
                sigs.addAll(s.positiveUnitSets(elem));
                d[elem.getNum()] = -s.weight(elem);
            }
        }
    }

    public PSD(Graph g, Signals s, Set<Node> forced) {
        int sz = g.vertexSet().stream().mapToInt(Unit::getNum).max().orElse(0) + 1;
        d = new double[sz];
        Arrays.fill(d, Double.POSITIVE_INFINITY);
        this.g = g;
        this.s = s;
        this.centers = new HashMap<>();
        this.paths = new HashMap<>();
        this.bestPaths = new HashMap<>();
        this.dsuPaths = new HashMap<>();
        this.forced = forced;

        int[] colors = new int[sz];
        boolean posCycle = false;
        for (Node n : g.vertexSet()) {
            if (colors[n.getNum()] == 0) {
                posCycle |= posCycles(n, null, colors, new HashSet<>());
            }
        }
        solutionIsTree = !posCycle;
    }

    public PSD(Graph g, Signals s) {
        this(g, s, Collections.emptySet());
    }


    private boolean posCycles(Node r, Node par, int[] colors, Set<Integer> sigs) {
        colors[r.getNum()] = 1;
        boolean pos = false;
        sigs.addAll(s.positiveUnitSets(r));
        for (Node n : g.neighborListOf(r)) {
            if (colors[n.getNum()] == 2 || n == par) continue;
            for (Edge e : g.getAllEdges(r, n)) {
                Set<Integer> ens = s.positiveUnitSets(e, n);
                if (s.weight(e) >= 0 && !sigs.containsAll(ens)) {
                    if (colors[n.getNum()] == 1)
                        pos = true;
                    else {
                        Set<Integer> added = new HashSet<>();
                        for (int sig: ens) {
                            if (sigs.add(sig)) {
                                added.add(sig);
                            }
                        }
                        pos |= posCycles(n, r, colors, sigs);
                        sigs.removeAll(added);
                    }
                }
            }
        }
        colors[r.getNum()] = 2;
        return pos;
    }


    private double getUpperBound() {
        return dsuPaths.values().stream()
                .flatMap(p -> Stream.concat(
                        p.c.sigs.stream(),
                        p.sigs.stream())).distinct()
                .mapToDouble(set -> s.weight(set)).sum();
    }

    public boolean decompose() {
        makeCenters();
        if (paths.isEmpty())
            return false; // No positive vertices, no decomposition exists
        dijkstra();
        findBoundaries();
        filterBoundaries();
        forceVertices(forced);
        this.ub = getUpperBound();
        return true;
    }

    public Optional<Path> forceVertex(Node f) {
        Path p = paths.get(f);
        int key = dsu.min(p.c.sigs.get(0));
        Path prev = dsuPaths.put(key, p);
        if (prev != null && prev != p) {
            return Optional.of(prev);
        } else return Optional.empty();
    }

    public Set<Path> forceVertices(Set<Node> vertices) {
        Set<Path> res = new HashSet<>();
        this.forced = vertices;
        for (Node f : forced) {
            forceVertex(f).ifPresent(res::add);
        }
        return res;
    }

    private void findBoundaries() {
        for (Path p : paths.values()) {
            double ws = s.weightSum(p.c.sigs);
            Collection<Integer> eSigs = g.edgesOf(p.n).stream().filter(u -> u != p.c.elem)
                    .max(Comparator.comparingDouble(s::weight))
                    .map(s::negativeUnitSets).orElse(Collections.emptySet());
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
        dsu = new DSU(s);
        for (int sig = 0; sig < s.size(); sig++) {
            if (s.weight(sig) > 0) {
                double add = 0;
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
                            add = s.weight(sig);
                            updatePath(min, p);
                            for (int si : p.c.sigs) {
                                if (!c.sigs.contains(si)) {
                                    c.sigs.add(si);
                                }
                            }
                        } else {
                            centers.remove(n);
                        }
                    }
                }
                sub += add;
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
            for (Node n : g.neighborListOf(cur)) {
                Edge e = g.getEdge(n, cur);
                if (s.weight(e) > 0 || centers.containsKey(n)) continue;
                double w = d[cur.getNum()] - s.minSum(e, n);
                if (d[n.getNum()] > w) {
                    paths.put(n, new Path(p, n, e));
                    d[n.getNum()] = w;
                    q.add(n);
                }
            }
        }
    }
}