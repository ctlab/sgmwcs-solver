package ru.itmo.ctlab.sgmwcs.solver;

import ru.itmo.ctlab.sgmwcs.Signals;
import ru.itmo.ctlab.sgmwcs.graph.Edge;
import ru.itmo.ctlab.sgmwcs.graph.Graph;
import ru.itmo.ctlab.sgmwcs.graph.Node;
import ru.itmo.ctlab.sgmwcs.graph.Unit;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Preprocessor {

    private Node root = null;
    private int logLevel = 0;


    public void setLogLevel(int level) {
        this.logLevel = level;
    }

    private class Step<T extends Unit> {
        private Consumer<Set<T>> test;
        private String name;

        Step(Consumer<Set<T>> test, String name) {
            this.name = name;
            this.test = test;
        }

        int apply(Set<T> toRemove) {
            toRemove.clear();
            test.accept(toRemove);
            int res = toRemove.size();

            if (logLevel > 1) {
                System.out.println(name + " test: " + res + " units to remove.");
            }
            for (Unit t : toRemove) {
                if (t instanceof Node) {
                    graph.removeVertex((Node) t);
                }
                if (t instanceof Edge) {
                    graph.removeEdge((Edge) t);
                }
            }
            return res;
        }
    }

    private int numThreads;

    private Graph graph;
    private Signals signals;

    private Node primaryNode;

    public Preprocessor(Graph graph,
                        Signals signals,
                        int numThreads,
                        int logLevel) {
        this(graph, signals);
        this.numThreads = numThreads;
        this.logLevel = logLevel;
    }

    public Preprocessor(Graph graph, Signals signals) {
        this.graph = graph;
        this.signals = signals;
        this.numThreads = 0;
        this.logLevel = 0;
    }

    public void setRoot(Node r) {
        this.root = r;
    }

    private double weight(Unit unit) {
        return signals.weight(unit);
    }

    private boolean positive(Unit unit) {
        return signals.minSum(unit) >= 0;
        // return signals.minSum(unit) == 0;
    }

    private boolean nonPositive(Unit unit) {
        return signals.minSum(unit) <= 0;
    }

    private boolean bijection(Unit unit) {
        return signals.bijection(unit);
    }

    private final Step<Node> cns = new Step<>(this::cns, "cns");
    private final Step<Node> npv2 = new Step<>(this::npv2, "npv2");
    private final Step<Node> leaves = new Step<>(this::leaves, "leaves");
    private final Step<Edge> npe = new Step<>(this::uselessEdges, "npe");
    private final Step<Edge> nnp = new Step<>(this::nnp, "nnp");
    //private final Step<Node> npv3 = new Step<>(this::npv3, "npv3");

    public void preprocessBasic() {
        posC();
        negC();
        primaryNode = root;
        Set<Node> toRemove = new HashSet<>();
        if (root != null)
            for (Node v : new ArrayList<>(graph.vertexSet())) {
                if (positive(v) && (primaryNode == null || weight(v) > weight(primaryNode))) {
                    primaryNode = v;
                }
            }
        if (primaryNode != null) {
            new Step<Node>(s ->
                    negR(primaryNode, primaryNode, new HashSet<>(), s)
                    , "negR").apply(toRemove);
        }
    }

    public void preprocess(int preprocessLevel) {
        if (preprocessLevel == 0) {
            return;
        }
        if (preprocessLevel == 1) {
            preprocessBasic();
            return;
        }
        int removed;
        do {
            removed = iteration();
            if (logLevel > 1) {
                System.out.println("Removed " + removed + " units");
            }
        } while (removed > 0);
    }

    private void mergeEdges() {
        for (Node u : graph.vertexSet()) {
            for (Node v : graph.neighborListOf(u)) {
                if (v.getNum() <= u.getNum()) continue;
                List<Edge> es = graph.getAllEdges(u, v);
                if (es.size() == 1) continue;
                for (Edge e : new ArrayList<>(es)) {
                    if (signals.minSum(e) < 0 && signals.maxSum(e) == 0)
                        es.remove(e);
                }
                while (es.size() > 1) {
                    absorb(es.get(1), es.get(0));
                    graph.removeEdge(es.get(0));
                    es.remove(0);
                }
            }
        }
    }

    private int iteration() {
        int res = 0;
        Set<Node> toRemove = new HashSet<>();
        primaryNode = root;
        if (primaryNode == null)
            for (Node v : new ArrayList<>(graph.vertexSet())) {
                if (primaryNode == null || weight(v) > weight(primaryNode)) {
                    primaryNode = v;
                }
            }
        if (primaryNode != null) {
           res += leaves.apply(toRemove);
        }
        res += cns.apply(toRemove);
        negC();
        posC();
        primaryNode = root;
        Node posNode = null;
        if (primaryNode == null)
            for (Node v : new ArrayList<>(graph.vertexSet())) {
                if (primaryNode == null || weight(v) > weight(primaryNode)) {
                    posNode = primaryNode;
                    primaryNode = v;
                }
            }
        if (primaryNode != null)
            res += new Step<Node>(s ->
                    negR(primaryNode, primaryNode, new HashSet<>(), s)
                    , "negR").apply(toRemove);
        if (posNode != null && graph.containsVertex(posNode)) {
            final Node pn = posNode;
            res += new Step<Node>(s ->
                    negR(pn, pn, new HashSet<>(), s)
                    , "negR").apply(toRemove);
        }
        Set<Edge> edgesToRemove = numThreads == 1 ? new HashSet<>() : new ConcurrentSkipListSet<>();
        res += npe.apply(edgesToRemove);
        res += nnp.apply(edgesToRemove);

        res += npv2.apply(toRemove);
        // res += npv3.apply(toRemove);
        return res;
    }

    /*private void cleanEdges() {
        for (Edge e: graph.edgeSet()) {
            for (int sig: signals.positiveUnitSets(graph.disjointVertices(e))) {
                if (signals.set(sig).contains(e)) {
                    signals.remove(e, sig);
                }
            }
        }
    }*/

    private boolean negR(Node v, Node r, Set<Node> vis, Set<Node> toRemove) {
        boolean safe = false;
        vis.add(v);
        List<Unit> units = new ArrayList<>(vis);
        for (Edge e : graph.edgesOf(v)) {
            double minSum = signals.minSum(units);
            Node u = graph.getOppositeVertex(v, e);
            if (vis.contains(u)) {
                if (u != r && !toRemove.contains(u)) {
                    safe = true;
                }
                continue;
            }
            boolean res = negR(u, v, vis, toRemove);
            units.add(u);
            if (minSum <= signals.minSum(units) || signals.maxSum(u) > 0) {
                res = true;
            } else {
                minSum = signals.minSum(units);
                for (Edge edge : graph.getAllEdges(v, u)) {
                    units.add(edge);
                    if (minSum <= signals.minSum(units)) {
                        res = true;
                    }
                    units.remove(units.size() - 1);
                }
            }
            if (!res) {
                toRemove.add(u);
            }
            safe = res || safe;
            units.remove(units.size() - 1);
        }
        return safe;
    }


    private void nnp(Set<Edge> toRemove) {
        for (Edge e : graph.edgeSet()) {
            if (signals.minSum(e) > 0) continue;
            Node u = graph.getEdgeTarget(e), v = graph.getEdgeSource(e);
            for (Node n : graph.neighborListOf(v)) {
                Edge eu = graph.getEdge(n, v);
                if (eu == e || signals.minSum(eu, n) < signals.minSum(e)) continue;
                if (graph.neighborListOf(n).contains(u)) {
                    Edge ev = graph.getEdge(n, u);
                    if (toRemove.contains(eu) || toRemove.contains(ev))
                        continue;
                    Set<Integer> pos = signals.positiveUnitSets(e);
                    double lowest = Math.min(
                            signals.minSum(eu, ev), signals.minSum(eu, ev, n));
                    double lowest2 = Math.min(signals.minSum(eu), signals.minSum(ev));
                    lowest = Math.min(lowest, lowest2);
                    if (lowest >= signals.minSum(e)
                            && signals.positiveUnitSets(eu, n, u, v)
                            .containsAll(pos)
                            && signals.positiveUnitSets(ev, n, u, v)
                            .containsAll(pos))
                        toRemove.add(e);
                }
            }
        }

    }

    private void negC() {
        for (Node v : new ArrayList<>(graph.vertexSet())) {
            if (signals.maxSum(v) <= 0 && graph.degreeOf(v) == 2) {
                Edge[] edges = graph.edgesOf(v).toArray(new Edge[0]);
                if (signals.maxSum(edges[1]) > 0 || signals.maxSum(edges[0]) > 0) {
                    continue;
                }
                Node left = graph.getOppositeVertex(v, edges[0]);
                Node right = graph.getOppositeVertex(v, edges[1]);
                if (left == right) {
                    graph.removeVertex(v);
                } else {
                    graph.removeVertex(v);
                    absorb(edges[0], v);
                    absorb(edges[0], edges[1]);
                    graph.addEdge(left, right, edges[0]);
                }
            }
        }
    }

    private void posC() {
        for (Edge edge : new ArrayList<>(graph.edgeSet())) {
            if (!graph.containsEdge(edge)) {
                continue;
            }
            Node from = graph.getEdgeSource(edge);
            Node to = graph.getEdgeTarget(edge);
            if (positive(edge) && positive(from) && positive(to)) {
                merge(edge, from, to);
            }
        }
    }

    private void merge(Unit... units) {
        Set<Node> nodes = new HashSet<>();
        Set<Edge> edges = new HashSet<>();
        for (Unit unit : units) {
            if (unit instanceof Node) {
                nodes.add((Node) unit);
            } else {
                edges.add((Edge) unit);
            }
        }
        for (Edge e : edges) {
            if (!nodes.contains(graph.getEdgeSource(e)) || !nodes.contains(graph.getEdgeTarget(e))) {
                throw new IllegalArgumentException();
            }
        }
        for (Edge e : edges) {
            contract(e);
        }
    }

    public void contract(Edge e) {
        Node main = graph.getEdgeSource(e);
        Node aux = graph.getEdgeTarget(e);
        Set<Edge> auxEdges = new HashSet<>(graph.edgesOf(aux));
        auxEdges.remove(e);
        for (Edge a : auxEdges) {
            Node opposite = graph.getOppositeVertex(aux, a);
            Edge m = graph.getEdge(main, opposite);
            graph.removeEdge(a);
            if (m == null) {
                if (opposite == main) {
                    if (positive(a)) {
                        absorb(main, a);
                    }
                    continue;
                }
                graph.addEdge(main, opposite, a);
            } else {
                if (positive(a) && positive(m)) {
                    absorb(m, a);
                } else {
                    graph.addEdge(main, opposite, a);
                }
            }
        }
        graph.removeVertex(aux);
        absorb(main, aux);
        absorb(main, e);
    }

    private void leaves(Set<Node> toRemove) {
        Map<Node, List<Unit>> toAbsorb = new HashMap<>();
        for (Node leaf : graph.vertexSet()) {
            Set<Edge> edges = graph.edgesOf(leaf);
            if (edges.size() != 1
                    || weight(leaf) == weight(primaryNode)) continue;
            Edge edge = edges.stream().findAny().orElse(null);
            Node opposite = graph.getOppositeVertex(leaf, edge);
            double minSum = signals.minSum(edge, leaf, opposite);
            if (minSum >= signals.minSum(opposite)
                    && graph.degreeOf(opposite) > 1) {
                toAbsorb.putIfAbsent(opposite, new ArrayList<>());
                toAbsorb.get(opposite).addAll(Arrays.asList(leaf, edge));
                toRemove.add(leaf);
            } else if (
                    signals.sum(edge, leaf, opposite) <= signals.sum(opposite)) {
                toRemove.add(leaf);
            } else {
                for (Node other : graph.neighborListOf(opposite)) {
                    if (toRemove.contains(other) || other == leaf)
                        continue;
                    Edge otherEdge = graph.getEdge(other, opposite);
                    if (signals.positiveUnitSets(otherEdge, other)
                            .containsAll(signals.positiveUnitSets(leaf, edge)) &&
                            signals.minSum(otherEdge, other) >= signals.minSum(leaf, edge)) {
                        toRemove.add(leaf);
                    }
                }
            }
        }
        for (Map.Entry<Node, List<Unit>> kvp : toAbsorb.entrySet()) {
            List<Unit> willAbsorb = kvp.getValue();
            willAbsorb.forEach(val -> absorb(kvp.getKey(), val));
        }
    }

    private boolean positiveEdge(Node u, Node v) {
        return graph.getAllEdges(u, v).stream().anyMatch(this::positive);
    }

    private Stream<Node> positiveNeighbors(Node v) {
        return graph.neighborListOf(v).stream()
                .filter(n -> positive(n) && positiveEdge(n, v));
//                .filter(n -> signals.minSum(n, graph.getEdge(n, v)) >= 0);
    }

    private void cns(Set<Node> toRemove) {
        Set<Node> vertexSet = graph.vertexSet();
        Set<Node> w;
        Set<Edge> we;
        for (Node v : vertexSet) {
            if (toRemove.contains(v)) continue;
            w = positiveNeighbors(v)
                    .collect(Collectors.toSet());
            // we = w.stream().map(n -> graph.getEdge(n, v)).collect(Collectors.toSet());
            w.add(v);
            // ws.addAll(signals.unitSets(w));
            final Set<Integer> ws = signals.unitSets(w);
            Set<Node> wnbs = w.stream()
                    .flatMap(n -> graph.neighborListOf(n).stream())
                    .collect(Collectors.toSet());
            for (Node n : wnbs) {
                List<Node> neighbors = graph.neighborListOf(n);
                for (Node r : neighbors) {
                    if (w.contains(r) || r == root) continue;
                    double vWorst = signals.minSum(v);
                    Set<Edge> edges = graph.edgesOf(r);
                    Set<Integer> rs = signals.positiveUnitSets(edges);
                    rs.addAll(signals.positiveUnitSets(r));
                    // double rWeight = signals.weightSum(signals.positiveUnitSets(edges));
                    double bestSum = signals.minSum(r);
                    if (vWorst >= bestSum && ws.containsAll(rs)
                            && w.containsAll(graph.neighborListOf(r)))
                        toRemove.add(r);
                }
            }
        }
    }

    private void uselessEdges(Set<Edge> toRemove) {
        ExecutorService executor;
        if (numThreads > 1) {
            executor = Executors.newFixedThreadPool(numThreads);
            synchronized (this) {
                // Remove nodes marked as deleted
                // in internal graph representation
                graph.subgraph(graph.vertexSet());
            }
        } else executor = Executors.newSingleThreadExecutor();
        parallelUselessEdges(toRemove, executor);
    }

    private void parallelUselessEdges(Set<Edge> toRemove, ExecutorService executor) {
        for (Node u : graph.vertexSet()) {
            executor.execute(
                    () -> {
                        Dijkstra dijkstra = new Dijkstra(graph, signals);
                        npeIteration(dijkstra, u, toRemove);
                    }
            );
        }
        executor.shutdown();
        try {
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException ignored) {
        }
    }

    private void npeIteration(Dijkstra dijkstra, Node u, Set<Edge> toRemove) {
        List<Node> neighbors = graph.neighborListOf(u).stream()
                .filter(n -> graph.getAllEdges(n, u)
                        .stream().anyMatch(this::nonPositive))
                .collect(Collectors.toList());
        if (neighbors.isEmpty()) return;
        Set<Edge> res = dijkstra.solveNE(u, neighbors);
        for (Edge edge : res) {
            // if (nonPositive(edge)) {
            toRemove.add(edge);
            // }
        }
    }

    private void npv2(Set<Node> toRemove) {
        Dijkstra dijkstra = new Dijkstra(graph, signals);
        for (Node n : graph.vertexSet()) {
            if (n == primaryNode) continue;
            if (!checkNeg(n)) continue;
            if (graph.neighborListOf(n).stream().anyMatch(toRemove::contains)) continue;
            if (dijkstra.solveNP(n))
                toRemove.add(n);

        }

    }

    private void npv3(Set<Node> toRemove) {
        npvClique(3, toRemove);
    }

    private void npv4(Set<Node> toRemove) {
        npvClique(4, toRemove);
    }

    private void npvClique(int maxK, Set<Node> toRemove) {
        Set<Node> nodes = new HashSet<>(graph.vertexSet());
        for (Node v : graph.vertexSet()) {
            // if (!negWithEdges(v)) continue;
            List<Node> delta = graph.neighborListOf(v);
            if (delta.size() <= maxK && delta.size() >= 2) {
                nodes.remove(v);
                boolean res = new Dijkstra(
                        graph.subgraph(nodes), signals
                ).solveClique(v, new HashSet<>(delta)
                );
                if (res) {
                    toRemove.add(v);
                }
                nodes.add(v);
            }
        }
    }

    private boolean checkNeg(Node n) {
        return graph.degreeOf(n) == 2 && negWithEdges(n); //signals.maxSum(n) + signals.maxSum(graph.edgesOf(n)) <= 0);
    }

    private boolean negWithEdges(Node n) {
        Edge[] e = graph.edgesOf(n).toArray(new Edge[0]);
        Set<Integer> es = signals.positiveUnitSets(graph.edgesOf(n));
        es.addAll(signals.unitSets(n));
        return signals.weightSum(es)
                + Math.max(signals.weightSum(signals.negativeUnitSets(e[0])),
                signals.weightSum(signals.negativeUnitSets(e[1]))) <= 0;
    }

    private void absorb(Unit who, Unit whom) {
        who.absorb(whom);
        signals.join(whom, who);
    }
}