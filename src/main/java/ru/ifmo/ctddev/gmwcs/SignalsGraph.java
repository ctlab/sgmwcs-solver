package ru.ifmo.ctddev.gmwcs;

import ru.ifmo.ctddev.gmwcs.graph.Edge;
import ru.ifmo.ctddev.gmwcs.graph.Graph;
import ru.ifmo.ctddev.gmwcs.graph.Node;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Created by Nikolay Poperechnyi on 20.08.18.
 */
public class SignalsGraph {

    private Graph gin;
    private Signals sin;
    private Graph gout;
    private Node[] nodes;
    private Map<Node, Set<Node>> edges;
    int ec = 1;

    public SignalsGraph(Graph gin, Signals sin) {
        this.gin = gin;
        this.sin = sin;
        this.gout = new Graph();
        this.nodes = new Node[sin.size()];
        this.edges = new HashMap<>();
        IntStream.range(0, sin.size()).forEach(s -> {
            nodes[s] = new Node(s);
            gout.addVertex(nodes[s]);
        });
        constructGraph();

    }

    private void constructGraph() {
        for (Node n : gin.vertexSet()) {
            for (Edge e : gin.edgesOf(n)) {
                for (int s1 : sin.unitSets(n))
                    for (int s2 : sin.unitSets(e)) {
                        edges.putIfAbsent(nodes[s1], new HashSet<>());
                        if (edges.get(nodes[s1]).add(nodes[s2])) {
                            gout.addEdge(nodes[s1], nodes[s2], new Edge(ec++));
                        }
                    }
            }
        }
    }

    public void writeGraph() throws IOException {
        try (PrintWriter nodes = new PrintWriter("snodes.txt");
             PrintWriter edges = new PrintWriter("sedges.txt")) {
            for (Node n: gout.vertexSet()) {
                nodes.println(n.getNum() + " " + sin.weight(n.getNum()));
            }
            for (Edge e: gout.edgeSet()) {
                edges.println(gout.getEdgeSource(e).getNum()+ " " + gout.getEdgeTarget(e).getNum() + " " + 0.0);
            }
        }
    }

}
