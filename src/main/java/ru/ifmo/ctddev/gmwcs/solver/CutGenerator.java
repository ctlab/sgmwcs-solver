package ru.ifmo.ctddev.gmwcs.solver;

import org.jgrapht.UndirectedGraph;
import ru.ifmo.ctddev.gmwcs.Pair;
import ru.ifmo.ctddev.gmwcs.graph.Edge;
import ru.ifmo.ctddev.gmwcs.graph.Node;
import ru.ifmo.ctddev.gmwcs.graph.flow.EdmondsKarp;
import ru.ifmo.ctddev.gmwcs.graph.flow.MaxFlow;

import java.util.*;

public class CutGenerator {
    public static final double EPS = 1e-5;
    private MaxFlow maxFlow;
    private Map<Node, Integer> nodes;
    private Node root;
    private List<Node> backLink;
    private Map<Node, Double> weights;
    private UndirectedGraph<Node, Edge> graph;

    public CutGenerator(UndirectedGraph<Node, Edge> graph, Node root) {
        int i = 0;
        weights = new HashMap<>();
        backLink = new ArrayList<>();
        nodes = new HashMap<>();
        for (Node node : graph.vertexSet()) {
            nodes.put(node, i++);
            backLink.add(node);
        }
        maxFlow = new EdmondsKarp(graph.vertexSet().size() * 2);
        for (Node v : graph.vertexSet()) {
            maxFlow.addEdge(nodes.get(v) * 2, nodes.get(v) * 2 + 1);
        }
        for (Edge e : graph.edgeSet()) {
            Node v = graph.getEdgeSource(e);
            Node u = graph.getEdgeTarget(e);
            maxFlow.addEdge(nodes.get(v) * 2 + 1, nodes.get(u) * 2);
            maxFlow.setCapacity(nodes.get(v) * 2 + 1, nodes.get(u) * 2, 1.0);
            maxFlow.addEdge(nodes.get(u) * 2 + 1, nodes.get(v) * 2);
            maxFlow.setCapacity(nodes.get(u) * 2 + 1, nodes.get(v) * 2, 1.0);
        }
        this.root = root;
        this.graph = graph;
    }

    public void setCapacity(Node v, double capacity) {
        weights.put(v, capacity);
        maxFlow.setCapacity(nodes.get(v) * 2, nodes.get(v) * 2 + 1, capacity);
    }

    public List<Node> findCut(Node v) {
        List<Pair<Integer, Integer>> cut = maxFlow.computeMinCut(nodes.get(root) * 2 + 1, nodes.get(v) * 2, weights.get(v) - EPS);
        if (cut == null) {
            return null;
        }
        Set<Node> result = new HashSet<>();
        for (Pair<Integer, Integer> p : cut) {
            result.add(backLink.get(p.second / 2));
        }
        List<Node> toReturn = new ArrayList<>();
        toReturn.addAll(result);
        return toReturn;
    }

    public Set<Node> getNodes() {
        return nodes.keySet();
    }

    public Node getRoot() {
        return root;
    }
}
