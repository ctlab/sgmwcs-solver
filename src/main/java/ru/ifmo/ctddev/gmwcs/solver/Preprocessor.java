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
import java.util.stream.Collectors;

public class Preprocessor {
    private int numThreads;

    private Graph graph;
    private Signals signals;

    public Preprocessor(Graph graph, Signals signals, int numThreads) {
        this(graph, signals);
        this.numThreads = numThreads - 1;
    }

    public Preprocessor(Graph graph, Signals signals) {
        this.graph = graph;
        this.signals = signals;
        this.numThreads = 0;
    }


    private double weight(Unit unit) {
        return signals.weight(unit);
    }

    private List<Node> neighbors(Node node) {
        return graph.neighborListOf(node);
    }

    private boolean positive(Unit unit) {
        return signals.unitSets(unit).stream().allMatch(s -> signals.weight(s) >= 0);
    }

    private boolean negative(Unit unit) {
        return signals.unitSets(unit).stream().allMatch(s -> signals.weight(s) < 0);
    }

    private boolean bijection(Unit unit) {
        List<Integer> ss = signals.unitSets(unit);
        return ss.stream().allMatch(s -> signals.set(s).size() == 1);
    }

    public void preprocess() {
        Node primaryNode = null;
        for (Node v : new ArrayList<>(graph.vertexSet())) {
            if (positive(v) && (primaryNode == null || weight(v) > weight(primaryNode))) {
                primaryNode = v;
            }
        }
        if (primaryNode != null) {
            adjacent(primaryNode);
            Set<Node> toRemove = new HashSet<>();
            negR(primaryNode, primaryNode, new HashSet<>(), toRemove);
            discard(primaryNode, toRemove);
            toRemove.forEach(graph::removeVertex);
        }
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
        for (Node v : new ArrayList<>(graph.vertexSet())) {
            if (weight(v) <= 0 && graph.degreeOf(v) == 2) {
                Edge[] edges = graph.edgesOf(v).toArray(new Edge[0]);
                if (weight(edges[1]) > 0 || weight(edges[0]) > 0) {
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
        graph = graph.subgraph(graph.vertexSet(), graph.edgeSet());
        if (numThreads == 0) {
            uselessEdges();
        } else {
            parallelUselessEdges();
        }
    }

    private boolean negR(Node v, Node r, Set<Node> vis, Set<Node> toRemove) {
        boolean safe = false;
        vis.add(v);
        List<Unit> units = new ArrayList<>();
        units.addAll(vis);
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
            if (minSum == signals.minSum(units)) {
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


    private void discard(Node primary, Set<Node> toRemove) {
        Map<Node, List<Unit>> toAbsorb = new HashMap<>();
        for (Node node : graph.vertexSet()) {
            Set<Edge> edges = graph.edgesOf(node);
            if (edges.size() != 1 || weight(node) == weight(primary)) continue;
            Edge edge = edges.stream().findAny().orElse(null);
            Node opposite = graph.getOppositeVertex(node, edge);
            double minSum = signals.minSum(edge, node, opposite);
            if (minSum == signals.minSum(edge)
                    && minSum == signals.minSum(node)
                    && minSum == signals.minSum(opposite)
                    && graph.degreeOf(opposite) > 1) {
                toAbsorb.putIfAbsent(opposite, new ArrayList<>());
                toAbsorb.get(opposite).add(node);
                toAbsorb.get(opposite).add(edge);
                toRemove.add(node);
            } else if (negative(edge) && negative(node)) {
                toRemove.add(node);
            }
        }
        for (Map.Entry<Node, List<Unit>> kvp : toAbsorb.entrySet()) {
            List<Unit> willAbsorb = kvp.getValue();
            willAbsorb.forEach(val -> absorb(kvp.getKey(), val));
        }
    }

    private void adjacent(Node primary) {
        Set<Node> toRemove = new HashSet<>();
        for (Node node : graph.vertexSet()) {
            if (node == primary) continue;
            for (Node candidate : candidates(node)) {
                if (candidate == node || toRemove.contains(candidate)) continue;
                List<Node> cNeighbors = neighbors(candidate);
                if (cNeighbors.containsAll(neighbors(node))) {
                    if (testSums(candidate, node, neighbors(node))) {
                        toRemove.add(node);
                        break;
                    }
                }
            }
        }
        for (Node node : toRemove) {
            graph.removeVertex(node);
        }
    }

    private boolean testSums(Node candidate, Node node, List<Node> neighbors) {
        List<Unit> units = new ArrayList<>();
        units.addAll(graph.edgesOf(node));
        units.add(node);
        double minSum = signals.minSum(units);
        double maxSum = signals.maxSum(units);
        List<Unit> cUnits = new ArrayList<>();
        for (Node neighbor : neighbors) {
            cUnits.add(graph.getEdge(neighbor, candidate));
        }
        cUnits.add(candidate);
        double cMinSum = signals.minSum(cUnits);
        return //(signals.negativeUnitSets(units).containsAll(signals.positiveUnitSets(cUnits))
                // && signals.positiveUnitSets(cUnits).containsAll(signals.positiveUnitSets(units)))
                cMinSum >= minSum && maxSum == 0;
    }

    private List<Node> candidates(Node node) {
        return neighbors(node).stream()
                .flatMap(n -> neighbors(n).stream())
                .collect(Collectors.toList());
    }


    private void uselessEdges() {
        Set<Edge> toRemove = new HashSet<>();
        Dijkstra dijkstra = new Dijkstra(graph, signals);
        for (Node u : graph.vertexSet()) {
            dijkstraIteration(dijkstra, u, toRemove);
        }
        toRemove.forEach(graph::removeEdge);
    }

    private void parallelUselessEdges() {
        Set<Edge> toRemove = new ConcurrentSkipListSet<>();
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        for (Node u : graph.vertexSet()) {
            executor.execute(() -> {
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
        toRemove.forEach(graph::removeEdge);
    }

    private void dijkstraIteration(Dijkstra dijkstra, Node u, Set<Edge> toRemove) {
        List<Node> neighbors = graph.neighborListOf(u).stream()
                .filter(n -> graph.getAllEdges(n, u)
                        .stream().anyMatch(e -> negative(e) && bijection(e)))
                .collect(Collectors.toList());
        if (neighbors.isEmpty()) return;
        Set<Edge> res = dijkstra.solve(u, neighbors);
        for (Edge edge : res) {
            if (negative(edge) && bijection(edge)) {
                toRemove.add(edge);
            }
        }
    }


    private void absorb(Unit who, Unit whom) {
        who.absorb(whom);
        signals.join(whom, who);
    }
}
