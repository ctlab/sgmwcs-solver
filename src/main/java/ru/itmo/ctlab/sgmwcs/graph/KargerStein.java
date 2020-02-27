package ru.itmo.ctlab.sgmwcs.graph;

import ru.itmo.ctlab.sgmwcs.solver.Utils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by Nikolay Poperechnyi on 12.11.19.
 */
public class KargerStein {
//    private Set<Edge> minCut;
    private Map<Set<Edge>, Integer> cutMap;

    private Random random;

    public KargerStein(final Graph src) {
        random = new Random();
        cutMap = new HashMap<>();
        findMinCut(src);
        // minCut = findMinCut(src).edgeSet();
    }

    public Set<Set<Edge>> minCuts() {
//        System.out.println("MC size: " + minCut.size());
        return cutMap.keySet();
 //       return new HashSet<>(minCut);
    }

    public Set<Edge> bestCut() {
        return Collections.max(cutMap.entrySet(),
                Comparator.comparingInt(Map.Entry::getValue)).getKey();
    }


    private void findMinCut(final Graph g) {
        int n = g.vertexSet().size();

        if (n <= 6) {
            Graph minG = g;
            for (int i = 0; i < n * (n - 1) / 2; i++) {
                Graph res = contract(g, 2);
                if (minG.edgeSet().size() > res.edgeSet().size()
                        && (minG.vertexSet().size() > 2
                        || minComp(minG) < minComp(res))) {
                    minG = res;
                }
            }
            if (minG.edgeSet().size() == 2)
                cutMap.put(minG.edgeSet(), minComp(minG));
            return;
        }
        int t = (int) Math.ceil(1 + n / Math.sqrt(2));
        findMinCut(contract(g, t));
        findMinCut(contract(g, t));
    }

    private int minComp(Graph g) {
        assert g.vertexSet().size() == 2;
        List<Node> vertices = new ArrayList<>(g.vertexSet());
        int s1 = vertices.get(0).absorbed.size();
        int s2 = vertices.get(1).absorbed.size();
        return Math.min(s1, s2);
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
        main.absorb(aux);
        graph.removeVertex(aux);
    }
}
