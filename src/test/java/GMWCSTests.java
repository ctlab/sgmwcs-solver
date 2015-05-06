import ilog.concert.IloException;
import org.jgrapht.UndirectedGraph;
import org.jgrapht.graph.SimpleGraph;
import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import ru.ifmo.ctddev.gmwcs.*;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class GMWCSTests {
    public static final int SEED = 20140503;
    public static final int TESTS_PER_SIZE = 300;
    public static final int MAX_SIZE = 16;
    public static final int RANDOM_TESTS = 1000;
    public static final Integer DEBUG_TEST = null;
    private PrintStream nativeOut;
    private PrintStream nullOut;
    private List<UndirectedGraph<Node, Edge>> tests;
    private Solver solver;
    private ReferenceSolver referenceSolver;
    private Random random;

    public GMWCSTests() {
        random = new Random(SEED);
        solver = new RLTSolver();
        tests = new ArrayList<>();
        nativeOut = System.out;
        nullOut = new PrintStream(new OutputStream() {
            @Override
            public void write(int b) throws IOException {
            }
        });
        referenceSolver = new ReferenceSolver();
        if (System.getProperty("skipTests") != null) {
            System.exit(0);
        }
        makeConnectedGraphs();
        makeUnconnectedGraphs();
    }

    @Test
    public void test01_empty() throws IloException {
        if (DEBUG_TEST != null) {
            return;
        }
        UndirectedGraph<Node, Edge> graph = new SimpleGraph<>(Edge.class);
        Assert.assertNull(solver.solve(graph, 4));
    }

    @Test
    public void test02_connected() {
        int allTests = MAX_SIZE * TESTS_PER_SIZE;
        if (DEBUG_TEST != null) {
            if (DEBUG_TEST < allTests) {
                check(tests.get(DEBUG_TEST), DEBUG_TEST);
            } else {
                return;
            }
        } else {
            for (int i = 0; i < allTests; i++) {
                UndirectedGraph<Node, Edge> test = tests.get(i);
                System.out.print("\rTest(connected) no. " + (i + 1) + "/" + tests.size());
                System.out.print(": n = " + test.vertexSet().size() + ", m = " + test.edgeSet().size() + "       ");
                check(test, i);
            }
        }
        System.out.println();
    }

    @Test
    public void test03_random() {
        int allTests = MAX_SIZE * TESTS_PER_SIZE;
        if (DEBUG_TEST != null) {
            if (DEBUG_TEST < allTests) {
                return;
            } else {
                check(tests.get(DEBUG_TEST), DEBUG_TEST);
            }
        } else {
            for (int i = allTests; i < tests.size(); i++) {
                UndirectedGraph<Node, Edge> test = tests.get(i);
                System.out.print("\rTest(random) no. " + (i) + "/" + tests.size());
                System.out.print(": n = " + test.vertexSet().size() + ", m = " + test.edgeSet().size() + "       ");
                check(test, i);
            }
        }
        System.out.println();
    }

    private void check(UndirectedGraph<Node, Edge> graph, int num) {
        List<Unit> expected = referenceSolver.solve(graph, Collections.<Node>emptyList());
        List<Unit> actual = null;
        try {
            System.setOut(nullOut);
            actual = solver.solve(graph, 4);
        } catch (IloException e) {
            System.setOut(nativeOut);
            System.out.println();
            Assert.assertTrue(num + "\n" + e.getMessage(), false);
        } catch (UnsatisfiedLinkError e) {
            System.err.println();
            System.err.println("java.library.path must point to the directory containing the CPLEX shared library\n" +
                    "try invoking java with java -Djava.library.path=...");
            System.exit(1);
        } finally {
            System.setOut(nativeOut);
        }
        Assert.assertEquals(num + "\n" + graphToString(graph), sum(expected), sum(actual), 0.1);
    }

    private String graphToString(UndirectedGraph<Node, Edge> graph) {
        String result = graph.vertexSet().size() + " " + graph.edgeSet().size() + "\n";
        result += "Nodes: \n";
        for (Node node : graph.vertexSet()) {
            result += node.getNum() + " " + node.getWeight() + "\n";
        }
        result += "Edges: \n";
        for (Edge edge : graph.edgeSet()) {
            Node from = graph.getEdgeSource(edge);
            Node to = graph.getEdgeTarget(edge);
            result += from.getNum() + " " + to.getNum() + " " + edge.getWeight() + "\n";
        }
        return result;
    }

    private void makeConnectedGraphs() {
        for (int size = 1; size <= MAX_SIZE; size++) {
            List<Integer> edgesCount = new ArrayList<>();
            for (int i = 0; i < TESTS_PER_SIZE; i++) {
                if (size == 1) {
                    edgesCount.add(0);
                } else {
                    int upper = Math.min((size * (size - 1)) / 2 + 1, MAX_SIZE);
                    upper -= size - 1;
                    edgesCount.add(random.nextInt(upper));
                }
            }
            Collections.sort(edgesCount);
            for (int count : edgesCount) {
                UndirectedGraph<Node, Edge> graph = new SimpleGraph<>(Edge.class);
                Node[] nodes = fillNodes(graph, size);
                List<Integer> seq = new ArrayList<>();
                for (int j = 0; j < size; j++) {
                    seq.add(j);
                }
                Collections.shuffle(seq, random);
                for (int j = 0; j < size - 1; j++) {
                    graph.addEdge(nodes[seq.get(j)], nodes[seq.get(j + 1)], new Edge(j + 1, random.nextInt(16) - 8));
                }
                fillEdgesRandomly(graph, count, nodes, size);
                tests.add(graph);

            }
        }
    }

    private void makeUnconnectedGraphs() {
        for (int i = 0; i < RANDOM_TESTS; i++) {
            int n = random.nextInt(MAX_SIZE) + 1;
            int m = Math.min((n * (n - 1)) / 2, random.nextInt(MAX_SIZE));
            UndirectedGraph<Node, Edge> graph = new SimpleGraph<>(Edge.class);
            Node[] nodes = fillNodes(graph, n);
            fillEdgesRandomly(graph, m, nodes, 1);
            tests.add(graph);
        }
    }

    private Node[] fillNodes(UndirectedGraph<Node, Edge> graph, int size) {
        Node[] nodes = new Node[size];
        for (int j = 0; j < size; j++) {
            nodes[j] = new Node(j + 1, random.nextInt(16) - 8);
            graph.addVertex(nodes[j]);
        }
        return nodes;
    }

    private void fillEdgesRandomly(UndirectedGraph<Node, Edge> graph, int count, Node[] nodes, int offset) {
        int size = graph.vertexSet().size();
        for (int j = 0; j < count; j++) {
            int u = random.nextInt(size);
            int v = random.nextInt(size);
            if (u == v || graph.getEdge(nodes[u], nodes[v]) != null) {
                j--;
                continue;
            }
            graph.addEdge(nodes[u], nodes[v], new Edge(offset + j, random.nextInt(16) - 8));
        }
    }

    private double sum(List<Unit> units) {
        if (units == null) {
            return 0;
        }
        double res = 0;
        for (Unit unit : units) {
            res += unit.getWeight();
        }
        return res;
    }
}
