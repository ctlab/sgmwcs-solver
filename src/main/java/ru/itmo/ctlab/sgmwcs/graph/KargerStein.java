package ru.itmo.ctlab.sgmwcs.graph;

import ru.itmo.ctlab.sgmwcs.solver.Utils;

import java.util.*;

/**
 * Created by Nikolay Poperechnyi on 12.11.19.
 */
public class KargerStein {
    private Set<Edge> minCut;

    private Random random;

    public KargerStein(final Graph src) {
        random = new Random(42);
        minCut = findMinCut(src).edgeSet();
    }

    public Set<Edge> minCut() {
        System.out.println("MC size: " + minCut.size());
        return new HashSet<>(minCut);
    }

    private Graph findMinCut(final Graph g) {
        int n = g.vertexSet().size();

        if (n <= 6) {
            Graph minG = g;
            for (int i = 0; i < n * (n - 1) / 2; i++) {
                Graph res = contract(g, 2);
                if (minG.edgeSet().size() > res.edgeSet().size()) {
                    minG = res;
                }
            }
            return minG;
        }
        int t = (int) Math.ceil(1 + n / Math.sqrt(2));
        Graph g1 = findMinCut(contract(g, t));
        Graph g2 = findMinCut(contract(g, t));
        return g1.edgeSet().size() < g2.edgeSet().size() ? g1 : g2;
    }

    private Graph contract(final Graph in, int thresh) {
        Graph g = new Graph(in);
        List<Edge> edges = new LinkedList<>(g.edgeSet());
        int size = g.vertexSet().size();
        while (size > thresh) {
            int num = random.nextInt(edges.size());
            contractEdge(g, edges.remove(num));
            size = g.vertexSet().size();
        }
        return g;
    }

    private void contractEdge(Graph graph, Edge e) {
        if (!graph.containsEdge(e))
            return;
        Node main = graph.getEdgeSource(e);
        Node aux = graph.getEdgeTarget(e);
        Set<Edge> auxEdges = new HashSet<>(graph.edgesOf(aux));
        auxEdges.remove(e);
        for (Edge a : auxEdges) {
            Node opposite = graph.getOppositeVertex(aux, a);
            graph.removeEdge(a);
            if (opposite != main) {
                graph.addEdge(main, opposite, a);
            }
        }
        graph.removeVertex(aux);
    }


}
