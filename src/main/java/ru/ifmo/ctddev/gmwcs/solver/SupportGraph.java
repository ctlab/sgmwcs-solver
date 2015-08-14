package ru.ifmo.ctddev.gmwcs.solver;

import org.jgrapht.UndirectedGraph;
import org.jgrapht.alg.MinSourceSinkCut;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;
import ru.ifmo.ctddev.gmwcs.graph.Edge;
import ru.ifmo.ctddev.gmwcs.graph.Node;

import java.util.*;

public class SupportGraph {
    private SimpleDirectedWeightedGraph<String, String> graph;
    private Map<String, Node> nodes;

    public SupportGraph(UndirectedGraph<Node, Edge> source) {
        graph = new MyGraph();
        nodes = new LinkedHashMap<>();
        for (Node node : source.vertexSet()) {
            String from = node.getNum() + "_1";
            String to = node.getNum() + "_2";
            graph.addVertex(from);
            graph.addVertex(to);
            graph.addEdge(from, to, node.getNum() + "");
            nodes.put(node.getNum() + "", node);
            graph.setEdgeWeight(node.getNum() + "", 1);
        }
        int n = graph.vertexSet().size() * 2;
        for (Edge edge : source.edgeSet()) {
            Node from = source.getEdgeSource(edge);
            Node to = source.getEdgeTarget(edge);
            graph.addEdge(from.getNum() + "_2", to.getNum() + "_1", "e" + edge.getNum() + "_1");
            graph.setEdgeWeight("e" + edge.getNum() + "_1", n);
            graph.addEdge(to.getNum() + "_2", from.getNum() + "_1", "e" + edge.getNum() + "_2");
            graph.setEdgeWeight("e" + edge.getNum() + "_2", n);
        }
    }

    public Cut findCut(Node v, Node u) {
        MinSourceSinkCut<String, String> minCut = new MinSourceSinkCut<>(graph);
        minCut.computeMinCut(v.getNum() + "_2", u.getNum() + "_1");
        Set<Node> cut = new HashSet<>();
        for (String s : minCut.getCutEdges()) {
            cut.add(nodes.get(s));
        }
        Set<Node> sink = new HashSet<>();
        for (String s : minCut.getSinkPartition()) {
            if (nodes.containsKey(s)) {
                sink.add(nodes.get(s));
            }
        }
        return new Cut(cut, sink);
    }

    public static class Cut {
        private Set<Node> cut;
        private Set<Node> sink;

        public Cut(Set<Node> cut, Set<Node> sink) {
            this.cut = cut;
            this.sink = sink;
        }

        public Set<Node> cut() {
            return cut;
        }

        public Set<Node> sink() {
            return sink;
        }
    }

    private class MyGraph extends SimpleDirectedWeightedGraph<String, String> {
        private Map<String, Double> weights;

        public MyGraph() {
            super(String.class);
            weights = new HashMap<>();
        }

        @Override
        public void setEdgeWeight(String edge, double weight) {
            weights.put(edge, weight);
        }

        @Override
        public double getEdgeWeight(String edge) {
            if (weights.containsKey(edge)) {
                return weights.get(edge);
            }
            return 0;
        }
    }
}
