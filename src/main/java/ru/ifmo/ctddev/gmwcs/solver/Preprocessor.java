package ru.ifmo.ctddev.gmwcs.solver;

import ru.ifmo.ctddev.gmwcs.Signals;
import ru.ifmo.ctddev.gmwcs.graph.Edge;
import ru.ifmo.ctddev.gmwcs.graph.Graph;
import ru.ifmo.ctddev.gmwcs.graph.Node;
import ru.ifmo.ctddev.gmwcs.graph.Unit;

import java.util.*;
import java.util.function.Function;

public class Preprocessor {

    private Graph graph;
    private Signals signals;

    public Preprocessor(Graph graph, Signals signals) {
        this.graph = graph;
        this.signals = signals;
    }

    private double weight(Unit unit) {
        return signals.weight(unit);
    }

    private boolean positive(Unit unit) {
        return signals.unitSets(unit).stream().allMatch(s -> signals.weight(s) >= 0);
    }

    private boolean negative(Unit unit) {
        return signals.unitSets(unit).stream().allMatch(s -> signals.weight(s) <= 0);
    }

    private boolean bijection(Unit unit) {
        List<Integer> ss = signals.unitSets(unit);
        return ss.stream().allMatch(s -> signals.set(s).size() == 1);
    }

    public void preprocess() {
        uselessEdges();
        Node primaryNode = null;
        int size = 1;
        while (size > 0) {
            size = 0;
            for (Node v : new ArrayList<>(graph.vertexSet())) {
                if (positive(v) && (primaryNode == null || weight(v) > weight(primaryNode))) {
                    primaryNode = v;
                }
            }
            if (primaryNode != null) {
                Set<Node> toRemove = new HashSet<>();
                discard(primaryNode, toRemove);
                size = toRemove.size();
                toRemove.forEach(graph::removeVertex);
            }
        }
        if (primaryNode != null) {
            adjacent(primaryNode);
            Set<Node> toRemove = new HashSet<>();
            negR(primaryNode, primaryNode, new HashSet<>(), toRemove);
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
            for (Node candidate : graph.vertexSet()) {
                if (candidate == node || toRemove.contains(candidate)) continue;
                List<Node> cNeighbors = graph.neighborListOf(candidate);
                if (cNeighbors.containsAll(graph.neighborListOf(node))) {
                    if (testSums(candidate, node, graph.neighborListOf(node))) {
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
        List<Unit> units = new ArrayList<>(neighbors);
        units.addAll(graph.edgesOf(node));
        units.add(node);
        double minSum = signals.minSum(units);
        double maxSum = signals.maxSum(units);
        List<Unit> cUnits = new ArrayList<>(neighbors);
        for (Node neighbor : neighbors) {
            cUnits.add(graph.getEdge(neighbor, candidate));
        }
        cUnits.add(candidate);
        double cMinSum = signals.minSum(cUnits);
        double cMaxSum = signals.maxSum(cUnits);
        return signals.minSum(neighbors) >= minSum
                && signals.maxSum(neighbors) >= maxSum
                && cMinSum >= signals.minSum(neighbors)
                && cMaxSum > maxSum;
    }

    private void uselessEdges() {
        Set<Edge> toRemove = new HashSet<>();
        for (Node u : graph.vertexSet()) {
            for (Node v : graph.neighborListOf(u)) {
                if (u != v) {
                    List<Edge> edges = graph.getAllEdges(u, v);
                    if (edges.size() <= 1) continue;
                    edges.sort((e1, e2) -> Boolean.compare(positive(e1), positive(e2)));
                    if (negative(edges.get(edges.size() - 1))) continue;
                    for (Edge edge : edges.subList(0, edges.size() - 1)) {
                        if (negative(edge) && !toRemove.contains(edge)) {
                            toRemove.add(edge);
                        }
                    }
                }
            }
        }
        toRemove.forEach(graph::removeEdge);

    }

    private void absorb(Unit who, Unit whom) {
        who.absorb(whom);
        signals.join(whom, who);
    }
}
