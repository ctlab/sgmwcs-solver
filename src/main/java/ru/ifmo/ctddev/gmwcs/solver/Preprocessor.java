package ru.ifmo.ctddev.gmwcs.solver;

import ru.ifmo.ctddev.gmwcs.Signals;
import ru.ifmo.ctddev.gmwcs.graph.Edge;
import ru.ifmo.ctddev.gmwcs.graph.Graph;
import ru.ifmo.ctddev.gmwcs.graph.Node;
import ru.ifmo.ctddev.gmwcs.graph.Unit;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Preprocessor {

    private int logLevel = 0;

    private boolean edgePenalty;

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
                        int logLevel,
                        boolean edgePenalty) {
        this(graph, signals);
        this.numThreads = numThreads;
        this.logLevel = logLevel;
        this.edgePenalty = edgePenalty;
    }

    public Preprocessor(Graph graph, Signals signals) {
        this.graph = graph;
        this.signals = signals;
        this.numThreads = 0;
    }

    private double weight(Unit unit) {
        return signals.weight(unit);
    }

    private boolean positive(Unit unit) {
        return signals.minSum(unit) == 0;
    }

    private boolean nonPositive(Unit unit) {
        return signals.maxSum(unit) == 0;
    }

    private boolean bijection(Unit unit) {
        return signals.bijection(unit);
    }

    private final Step<Node> cns = new Step<>(this::cns, "cns");
    private final Step<Node> npv2 = new Step<>(this::npv2, "npv2");
    private final Step<Node> leaves = new Step<>(this::leaves, "leaves");
    private final Step<Edge> npe = new Step<>(this::uselessEdges, "npe");

    public void preprocess() {
        int removed;
        do {
            removed = iteration();
            if (logLevel > 1) {
                System.out.println("Removed " + removed + " vertices");
            }
        } while (removed > 0);
    }

    private int iteration() {
        int res = 0;
        primaryNode = null;
        Set<Node> toRemove = new HashSet<>();
        for (Node v : new ArrayList<>(graph.vertexSet())) {
            if (positive(v) && (primaryNode == null || weight(v) > weight(primaryNode))) {
                primaryNode = v;
            }
        }
        if (primaryNode != null) {
            if (!edgePenalty) //TODO: seems like it is no longer needed
                new Step<Node>(s ->
                        negR(primaryNode, primaryNode, new HashSet<>(), s)
                        , "negR").apply(toRemove);
            res += leaves.apply(toRemove);
            res += cns.apply(toRemove);
        }
        posC();
        negC();
        Set<Edge> edgesToRemove = numThreads == 1 ? new HashSet<>() : new ConcurrentSkipListSet<>();
        npe.apply(edgesToRemove);
        res += npv2.apply(toRemove);
        return res;
    }

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
            if (minSum == signals.minSum(units) || signals.maxSum(u) > 0) {
                res = true;
            } else {
                minSum = signals.minSum(units);
                for (Edge edge : graph.getAllEdges(v, u)) {
                    units.add(edge);
                    if (minSum == signals.minSum(units)) {
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

    private void negC() {
        for (Node v : new ArrayList<>(graph.vertexSet())) {
            if (signals.maxSum(v) == 0 && graph.degreeOf(v) == 2) {
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

    private void contract(Edge e) {
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
        for (Node node : graph.vertexSet()) {
            Set<Edge> edges = graph.edgesOf(node);
            if (edges.size() != 1
                    || weight(node) == weight(primaryNode)) continue;
            Edge edge = edges.stream().findAny().orElse(null);
            Node opposite = graph.getOppositeVertex(node, edge);
            double minSum = signals.minSum(edge, node, opposite);
            if (minSum <= signals.minSum(edge)
                    && minSum <= signals.minSum(node)
                    && minSum == signals.minSum(opposite)
                    && graph.degreeOf(opposite) > 1) {
                toAbsorb.putIfAbsent(opposite, new ArrayList<>());
                toAbsorb.get(opposite).addAll(Arrays.asList(node, edge));
                toRemove.add(node);
            } else if (nonPositive(edge) && nonPositive(node)) {
                toRemove.add(node);
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
                .filter(n -> positive(n)
                        && positiveEdge(v, n)
                );
    }

    private void cns(Set<Node> toRemove) {
        Set<Node> vertexSet = graph.vertexSet();
        Set<Node> w;
        for (Node v : vertexSet) {
            if (toRemove.contains(v)) continue;
            w = positiveNeighbors(v)
                    .collect(Collectors.toSet());
            w.add(v);
            for (Node n : w) {
                List<Node> neighbors = graph.neighborListOf(n);
                for (Node r : neighbors) {
                    if (!signals.positiveUnitSets(r).isEmpty() || w.contains(r)) continue;
                    double rWeight = signals.minSum(r);
                    double vWorst = signals.minSum(v);
                    Set<Edge> edges = graph.edgesOf(r);
                    double bestSum = signals.maxSum(edges) + rWeight;
                    if (vWorst >= bestSum && bijection(r)
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
                        dijkstraIteration(dijkstra, u, toRemove);
                    }
            );
        }
        executor.shutdown();
        try {
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException ignored) {
        }
    }

    private void dijkstraIteration(Dijkstra dijkstra, Node u, Set<Edge> toRemove) {
        List<Node> neighbors = graph.neighborListOf(u).stream()
                .filter(n -> graph.getAllEdges(n, u)
                        .stream().anyMatch(e -> nonPositive(e) && bijection(e)))
                .collect(Collectors.toList());
        if (neighbors.isEmpty()) return;
        Set<Edge> res = dijkstra.solveNE(u, neighbors);
        for (Edge edge : res) {
            if (nonPositive(edge) && bijection(edge)) {
                toRemove.add(edge);
            }
        }
    }

    private void npv2(Set<Node> toRemove) {
        Dijkstra dijkstra = new Dijkstra(graph, signals);
        graph.vertexSet().stream()
                .filter(n -> checkNeg(n) && dijkstra.solveNP(n))
                .forEach(toRemove::add);
    }

    private void npvClique(int maxK, Set<Node> toRemove) {
        Set<Node> nodes = new HashSet<>(graph.vertexSet());
        for (Node v : graph.vertexSet()) {
            if (!negWithEdges(v)) continue;
            List<Node> delta = graph.neighborListOf(v);
            if (delta.size() <= maxK && delta.size() >= 2) {
                nodes.remove(v);
                boolean res = new Dijkstra(
                        graph.subgraph(nodes), signals
                ).solveClique(signals.minSum(v), new HashSet<>(delta)
                );
                if (res) {
                    toRemove.add(v);
                }
                nodes.add(v);
            }
        }
    }

    private boolean checkNeg(Node n) {
        return graph.degreeOf(n) == 2 && negWithEdges(n);
    }

    private boolean negWithEdges(Node n) {
        double edgeSum = signals.maxSum(graph.edgesOf(n));
        return signals.maxSum(n) + edgeSum == 0;
    }

    private void absorb(Unit who, Unit whom) {
        who.absorb(whom);
        signals.join(whom, who);
    }
}