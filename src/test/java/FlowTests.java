import org.jgrapht.alg.MinSourceSinkCut;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;
import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import ru.ifmo.ctddev.gmwcs.Pair;
import ru.ifmo.ctddev.gmwcs.graph.flow.EdmondsKarp;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class FlowTests {
    public static final int SEED = 20151026;
    public static final int TESTS_PER_SIZE = 1000;
    public static final int MAX_SIZE = 20;
    public static final Integer DEBUG_TEST = null;
    public static final int DEBUG_SINK = 0;
    private Random random;

    public FlowTests() throws IOException {
        random = new Random(SEED);
    }

    @Test
    public void test01() throws IOException {
        int testNo = 0;
        for (int n = 3; n < MAX_SIZE; n++) {
            List<Integer> edges = new ArrayList<>();
            for (int j = 0; j < TESTS_PER_SIZE; j++) {
                edges.add(random.nextInt((n * (n - 1)) / 2));
            }
            Collections.sort(edges);
            for (int j = 0; j < TESTS_PER_SIZE; j++) {
                MyGraph graph = new MyGraph();
                for (int i = 0; i < n; i++) {
                    graph.addVertex(i);
                }
                for (int k = 0; k < edges.get(j); k++) {
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
                int s = random.nextInt(n);
                testNo++;
                if (DEBUG_TEST != null) {
                    if (testNo != DEBUG_TEST) {
                        continue;
                    }
                }
                EdmondsKarp maxFlow = build(graph);
                for (int i = 0; i < n; i++) {
                    if (DEBUG_TEST != null && i != DEBUG_SINK) {
                        continue;
                    }
                    if (i == s) {
                        continue;
                    }
                    double actualCapacity;
                    try {
                        List<Pair<Integer, Integer>> cut = maxFlow.computeMinCut(s, i, Double.POSITIVE_INFINITY);
                        actualCapacity = getCutCapacity(cut, graph);
                        MinSourceSinkCut<Integer, Integer> checker = new MinSourceSinkCut<>(graph);
                        checker.computeMinCut(s, i);
                        double expectedCapacity = checker.getCutWeight();
                        if (Math.abs(actualCapacity - expectedCapacity) > 1e-4) {
                            toXDot(graph, s, i, cut);
                            System.err.println("Test no. " + testNo);
                            System.err.println("Sink: " + i);
                            Assert.assertEquals(expectedCapacity, actualCapacity, 1e-4);
                        }
                        if (DEBUG_TEST != null) {
                            return;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        toXDot(graph, s, i, null);
                        System.err.println("Test no. " + testNo);
                        System.err.println("Sink: " + i);
                        System.exit(1);
                    }
                }
            }
        }
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
        Set<Integer> visited = new HashSet<>();
        for (Integer e : graph.edgeSet()) {
            if (visited.contains(e)) {
                continue;
            }
            int v = graph.getEdgeSource(e);
            int u = graph.getEdgeTarget(e);
            visited.add(e);
            g.addEdge(v, u);
            g.setCapacity(v, u, graph.getEdgeWeight(e));
        }
        return g;
    }

    private void toXDot(MyGraph graph, int s, int t, List<Pair<Integer, Integer>> cut) throws IOException {
        Runtime runtime = Runtime.getRuntime();
        Process process = runtime.exec("xdot");
        int n = graph.vertexSet().size();
        try (PrintWriter os = new PrintWriter(process.getOutputStream())) {
            os.println("digraph G {");
            for (int i = 0; i < graph.vertexSet().size(); i++) {
                os.print(i);
                if (i == s) {
                    os.print("[color=green]");
                }
                if (i == t) {
                    os.print("[color=yellow]");
                }
                os.println(";");
            }
            for (int i = 0; i < n; i++) {
                Set<Integer> marked = new HashSet<>();
                if (cut != null) {
                    for (Pair<Integer, Integer> e : cut) {
                        if (e.first == i) {
                            marked.add(e.second);
                        }
                    }
                }
                for (int j = 0; j < n; j++) {
                    if (graph.containsEdge(i, j)) {
                        os.println(i + " -> " + j + (marked.contains(j) ? "[color=red]" : "") + ";");
                        System.err.println(i + " -> " + j + ": " + graph.getEdgeWeight(graph.getEdge(i, j)));
                    }
                }
            }
            os.println("}");
        }
    }

    private class MyGraph extends SimpleDirectedWeightedGraph<Integer, Integer> {
        private Map<Integer, Double> weights;

        public MyGraph() {
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
