package ru.itmo.ctlab.sgmwcs.graph.flow;

import org.jgrapht.alg.MinSourceSinkCut;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;
import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import ru.itmo.ctlab.sgmwcs.Pair;

import java.io.IOException;
import java.util.*;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class FlowTest {
    private static final int SEED = 20151026;
    private static final int TESTS_PER_SIZE = 1000;
    private static final int MAX_SIZE = 20;
    private Random random;

    public FlowTest() throws IOException {
        random = new Random(SEED);
    }

    @Test
    public void testMaxFlow() throws IOException {
        for (int n = 3; n < MAX_SIZE; n++) {
            List<Integer> edges = new ArrayList<>();
            for (int j = 0; j < TESTS_PER_SIZE; j++) {
                edges.add(random.nextInt((n * (n - 1)) / 2));
            }
            Collections.sort(edges);
            for (int j = 0; j < TESTS_PER_SIZE; j++) {
                MyGraph graph = randomGraph(n, edges.get(j));
                int s = random.nextInt(n);
                EdmondsKarp maxFlow = build(graph);
                for (int i = 0; i < n; i++) {
                    if (i == s) {
                        continue;
                    }
                    double actualCapacity;
                    List<Pair<Integer, Integer>> cut = maxFlow.computeMinCut(s, i, Double.POSITIVE_INFINITY);
                    actualCapacity = getCutCapacity(cut, graph);
                    MinSourceSinkCut<Integer, Integer> checker = new MinSourceSinkCut<>(graph);
                    checker.computeMinCut(s, i);
                    double expectedCapacity = checker.getCutWeight();
                    if (Math.abs(actualCapacity - expectedCapacity) > 1e-4) {
                        Assert.assertEquals(expectedCapacity, actualCapacity, 1e-4);
                    }
                }
            }
        }
    }

    private MyGraph randomGraph(int n, int m){
        MyGraph graph = new MyGraph();
        for (int i = 0; i < n; i++) {
            graph.addVertex(i);
        }
        for (int k = 0; k < m; k++) {
            int v = random.nextInt(n);
            int u = random.nextInt(n);
            if (graph.containsEdge(v, u) || v == u) {
                k--;
                continue;
            }
            double capacity = random.nextDouble();
            graph.addEdge(v, u, k);
            graph.setEdgeWeight(graph.getEdge(v, u), capacity);
        }
        return graph;
    }

    private double getCutCapacity(List<Pair<Integer, Integer>> cut, MyGraph graph) {
        double sum = 0;
        for (Pair<Integer, Integer> e : cut) {
            sum += graph.getEdgeWeight(graph.getEdge(e.first, e.second));
        }
        return sum;
    }

    private EdmondsKarp build(MyGraph graph) {
        int n = graph.vertexSet().size();
        EdmondsKarp g = new EdmondsKarp(n);
        for (Integer e : graph.edgeSet()) {
            int v = graph.getEdgeSource(e);
            int u = graph.getEdgeTarget(e);
            g.addEdge(v, u);
            g.setCapacity(v, u, graph.getEdgeWeight(e));
        }
        return g;
    }

    private class MyGraph extends SimpleDirectedWeightedGraph<Integer, Integer> {
        private Map<Integer, Double> weights;

        MyGraph() {
            super(Integer.class);
            weights = new HashMap<>();
        }

        @Override
        public void setEdgeWeight(Integer edge, double weight) {
            weights.put(edge, weight);
        }

        @Override
        public double getEdgeWeight(Integer edge) {
            if (weights.containsKey(edge)) {
                return weights.get(edge);
            }
            return 0;
        }
    }
}
