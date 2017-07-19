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

    public void preprocess() {
        Node primaryNode = null;
        double maxWeight = Double.MIN_VALUE;
        for (Node v : new ArrayList<>(graph.vertexSet())) {
            if (weight(v) > 0 && (primaryNode == null || weight(v) > weight(primaryNode))) {
                primaryNode = v;
                maxWeight = weight(v);
            }
        }
        for (Edge e : graph.edgeSet()) {
            if (maxWeight < weight(e)) {
                maxWeight = weight(e);
            }
        }

        if (primaryNode != null) {
            Set<Node> toRemove = new HashSet<>();
            discard(primaryNode, maxWeight, toRemove);
            toRemove.forEach(graph::removeVertex);
        }
      /*  primaryNode = null;
        for (Edge edge : new ArrayList<>(graph.edgeSet())) {
            if (!graph.containsEdge(edge)) {
                continue;
            }
            Node from = graph.getEdgeSource(edge);
            Node to = graph.getEdgeTarget(edge);
            if (signals.weight(edge) >= 0 && weight(from) >= 0 && weight(to) >= 0) {
                merge(edge, from, to);
            }
        }
        for (Node v : new ArrayList<>(graph.vertexSet())) {
            if (weight(v) > 0 && (primaryNode == null || weight(v) > weight(primaryNode))) {
                primaryNode = v;
            }
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
        if (primaryNode != null) {
            Set<Node> toRemove = new HashSet<>();
            negR(primaryNode, primaryNode, new HashSet<>(), toRemove);
            toRemove.forEach(graph::removeVertex);
        }*/
    }

    private boolean negR(Node v, Node r, Set<Node> vis, Set<Node> toRemove) {
        boolean safe = false;
        vis.add(v);
        for (Edge e : graph.edgesOf(v)) {
            Node u = graph.getOppositeVertex(v, e);
            if (vis.contains(u)) {
                if (u != r && !toRemove.contains(u)) {
                    safe = true;
                }
                continue;
            }
            boolean res = negR(u, v, vis, toRemove);
            if (weight(u) > 0) {
                res = true;
            } else {
                for (Edge edge : graph.getAllEdges(v, u)) {
                    if (weight(edge) > 0) {
                        res = true;
                    }
                }
            }
            if (!res) {
                toRemove.add(u);
            }
            safe = res || safe;
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
                    if (weight(a) >= 0) {
                        absorb(main, a);
                    }
                    continue;
                }
                graph.addEdge(main, opposite, a);
            } else {
                if (weight(a) >= 0 && weight(m) >= 0) {
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



    private void discard(Node primary, Double maxWeight, Set<Node> toRemove) {
        Map<Node, List<Unit>> toAbsorb = new HashMap<>();
        Map<Unit, List<Integer>> uToS = signals.getUnitsSets();
        for (Node node : graph.vertexSet()) {
            Set<Edge> edges = graph.edgesOf(node);
            if (edges.size() != 1 || weight(node) == weight(primary)) continue;
            Edge edge = edges.stream().findAny().orElse(null);
            Node opposite = graph.getOppositeVertex(node, edge);
            List<Integer> edgeS = uToS.get(edge), nodeS = uToS.get(node);
            if (edgeS.stream().allMatch(s -> signals.weight(s) >= 0)
                    && nodeS.stream().allMatch(s -> signals.weight(s) >= 0)) {
                if (graph.degreeOf(opposite) == 1
                        || maxWeight <= weight(node) + weight(edge)) continue;
                toAbsorb.putIfAbsent(opposite, new ArrayList<>());
                toAbsorb.get(opposite).add(node);
                toAbsorb.get(opposite).add(edge);
                toRemove.add(node);
            } else if (edgeS.stream().allMatch(s -> signals.weight(s) <= 0)
                    && nodeS.stream().allMatch(s -> signals.weight(s) <= 0)) {
                toRemove.add(node);
            }
        }
        for (Map.Entry<Node, List<Unit>> kvp : toAbsorb.entrySet()) {
            List<Unit> willAbsorb = kvp.getValue();
            willAbsorb.forEach(val -> {
                absorb(kvp.getKey(), val);
            });
        }
    }

    private void absorb(Unit who, Unit whom) {
        who.absorb(whom);
        signals.join(whom, who);
    }
}
