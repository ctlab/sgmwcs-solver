package ru.ifmo.ctddev.gmwcs;

import ilog.concert.IloException;
import ilog.cplex.IloCplex;
import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import ru.ifmo.ctddev.gmwcs.graph.Edge;
import ru.ifmo.ctddev.gmwcs.graph.Graph;
import ru.ifmo.ctddev.gmwcs.graph.Node;
import ru.ifmo.ctddev.gmwcs.graph.Unit;
import ru.ifmo.ctddev.gmwcs.solver.ComponentSolver;
import ru.ifmo.ctddev.gmwcs.solver.RLTSolver;
import ru.ifmo.ctddev.gmwcs.solver.Solver;
import ru.ifmo.ctddev.gmwcs.solver.SolverException;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.*;

import static ru.ifmo.ctddev.gmwcs.solver.Utils.copy;
import static ru.ifmo.ctddev.gmwcs.solver.Utils.sum;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class GMWCSTest {
    private static final int SEED = 20160309;
    private static final int TESTS_PER_SIZE = 300;
    private static final int MAX_SIZE = 15;
    private static final int RANDOM_TESTS = 2200;
    private static final int RLT_MAX_SIZE = 100;

    static {
        try {
            new IloCplex();
        } catch (UnsatisfiedLinkError e) {
            System.exit(1);
        } catch (IloException ignored) {
        }
    }

    private List<TestCase> tests;
    private ComponentSolver solver;
    private ReferenceSolver referenceSolver;
    private RLTSolver rltSolver;
    private Random random;

    public GMWCSTest() {
        random = new Random(SEED);
        solver = new ComponentSolver(3, false);
        tests = new ArrayList<>();
        referenceSolver = new ReferenceSolver();
        rltSolver = new RLTSolver();
        makeConnectedGraphs(1, MAX_SIZE);
        makeUnconnectedGraphs();
    }

    @Test
    public void test_copy() {
        int allTests = MAX_SIZE * TESTS_PER_SIZE;
        for (int i = 0; i < allTests; i++) {
            TestCase test = tests.get(i);
            Graph graph = new Graph();
            Signals signals = new Signals();
            copy(test.graph(), test.signals(), graph, signals);
            double[] nodesPrev = test.graph().vertexSet().stream()
                    .mapToDouble(signals::weight).sorted().toArray();
            double[] nodesNew = graph.vertexSet().stream()
                    .mapToDouble(signals::weight).sorted().toArray();
            Assert.assertArrayEquals("Node weights must be equal", nodesPrev, nodesNew, 0.00001);

            double[] edgesPrev = test.graph().edgeSet().stream()
                    .mapToDouble(signals::weight).sorted().toArray();
            double[] edgesNew = graph.edgeSet().stream()
                    .mapToDouble(signals::weight).sorted().toArray();
            Assert.assertArrayEquals("Edge weights must be equal", edgesPrev, edgesNew, 0.00001);

            Assert.assertEquals(test.signals().size(), signals.size());
            for (int j = 0; j < signals.size(); ++j) {
                List<Unit> newUnits = signals.set(j);
                newUnits.sort(Comparator.comparingInt(Unit::getNum));
                List<Unit> oldUnits = test.signals().set(j);
                oldUnits.sort(Comparator.comparingInt(Unit::getNum));
                for (int num = 0; num < newUnits.size(); ++num) {
                    Unit nu = newUnits.get(num), ou = oldUnits.get(num);
                    Assert.assertNotSame(nu, ou);
                    Assert.assertEquals(nu.getNum(), ou.getNum());
                    Assert.assertTrue(signals.weight(nu) - signals.weight(ou) < 0.01);
                }
            }
        }
    }

    @Test
    public void test01_empty() throws SolverException {
        Graph graph = new Graph();
        solver.setLogLevel(0);
        List<Unit> res = solver.solve(graph, new Signals());
        if (!(res == null || res.isEmpty())) {
            Assert.assertTrue("An empty graph can't contain non-empty subgraph", false);
        }
    }

    @Test
    public void test02_connected() {
        solver.setThreadsNum(1);
        int allTests = MAX_SIZE * TESTS_PER_SIZE;
        for (int i = 0; i < allTests; i++) {
            System.err.println("Test " + i);
            TestCase test = tests.get(i);
/*            if (random.nextBoolean()) {
                addPenalties(test, random.nextInt(10));
            }*/
            check(test, i, referenceSolver);
//            solver.setEdgePenalty(0);
        }
        System.out.println();
    }

    @Test
    public void test03_random() {
        int allTests = MAX_SIZE * TESTS_PER_SIZE;
        solver.setEdgePenalty(0);
        for (int i = allTests; i < tests.size(); i++) {
            TestCase test = tests.get(i);
            check(test, i, referenceSolver);
        }
        System.out.println();
    }

    @Test
    public void test04_big() {
        tests.clear();
        makeConnectedGraphs(RLT_MAX_SIZE, RLT_MAX_SIZE);
        for (int i = 0; i < tests.size(); i++) {
            TestCase test = tests.get(i);
            if (random.nextBoolean())
                addPenalties(test, random.nextInt(10));
            check(test, i, rltSolver);
        }
    }

    private void check(TestCase test, int num, Solver refSolver) {
        List<Unit> expected = null;
        List<Unit> actual = null;
        try {
            expected = refSolver.solve(test.graph(), test.signals());
            solver.setLogLevel(0);
            actual = solver.solve(test.graph(), test.signals());
        } catch (SolverException e) {
            System.out.println();
            Assert.assertTrue(num + "\n" + e.getMessage(), false);
        }
        if (Math.abs(sum(expected, test.signals()) - sum(actual, test.signals())) > 0.1) {
            System.err.println();
            System.err.println("Expected: " + sum(expected, test.signals()) + ", but actual: "
                    + sum(actual, test.signals()));
            reportError(test, expected, num);
            Assert.assertTrue("A test has failed. See *error files.", false);
            System.exit(1);
        }
    }

    private void reportError(TestCase test, List<Unit> expected, int testNum) {
        try (PrintWriter nodeWriter = new PrintWriter("nodes_" + testNum + ".error");
             PrintWriter edgeWriter = new PrintWriter("edges_" + testNum + ".error");
             PrintWriter signalWriter = new PrintWriter("signals_" + testNum + ".error")) {
            Graph g = test.graph();
            Signals s = test.signals();
            for (Node v : g.vertexSet()) {
                nodeWriter.println(v.getNum() + "\tS" + (s.unitSets(v).get(0) + 1));
            }
            for (Edge e : g.edgeSet()) {
                Node from = g.getEdgeSource(e);
                Node to = g.getEdgeTarget(e);
                edgeWriter.println(from.getNum() + "\t" + to.getNum() + "\tS" + (s.unitSets(e).get(0) + 1));
            }
            reportSignals(test, signalWriter);
            System.err.println("Correct solution(one of): ");
            for (Unit u : expected) {
                if (u instanceof Edge) {
                    Edge e = (Edge) u;
                    System.err.println(g.getEdgeSource(e).getNum() + "\t" + g.getEdgeTarget(e).getNum());
                } else {
                    System.err.println(u.getNum());
                }
            }
        } catch (FileNotFoundException e) {
            System.err.println("Writing to *.error file failed.");
        }
    }

    private void reportSignals(TestCase test, PrintWriter signalWriter) {
        Signals signals = test.signals();
        for (int i = 0; i < signals.size(); i++) {
            signalWriter.println("S" + (i + 1) + "\t" + signals.weight(i));
        }
    }

    private void makeConnectedGraphs(int minSize, int maxSize) {
        for (int size = minSize; size <= maxSize; size++) {
            List<Integer> edgesCount = new ArrayList<>();
            for (int i = 0; i < TESTS_PER_SIZE; i++) {
                if (size == 1) {
                    edgesCount.add(0);
                } else {
                    int upper = Math.min((size * (size - 1)) / 2 + 1, maxSize);
                    upper -= size - 1;
                    edgesCount.add(random.nextInt(upper));
                }
            }
            Collections.sort(edgesCount);
            for (int count : edgesCount) {
                Graph graph = new Graph();
                Map<Node, Double> nodes = fillNodes(graph, size);
                List<Integer> seq = new ArrayList<>();
                for (int j = 0; j < size; j++) {
                    seq.add(j);
                }
                Collections.shuffle(seq, random);
                Node[] nodesArray = nodes.keySet().toArray(new Node[0]);
                Arrays.sort(nodesArray);
                Map<Edge, Double> edges = new HashMap<>();
                for (int j = 0; j < size - 1; j++) {
                    double weight = random.nextInt(16) - 8;
                    Edge edge = new Edge(j + 1);
                    graph.addEdge(nodesArray[seq.get(j)], nodesArray[seq.get(j + 1)], edge);
                    edges.put(edge, weight);
                }
                fillEdgesRandomly(graph, count, nodesArray, edges, size);
                Map<Unit, Double> weights = new HashMap<>();
                nodes.forEach((n, v) -> weights.put(n, v * n.getNum() * 0.03 + v));
                edges.forEach((e, v) -> weights.put(e, v * e.getNum() * 0.07 + v));
                tests.add(new TestCase(graph, weights, random));
            }
        }
    }

    private void makeUnconnectedGraphs() {
        for (int i = 0; i < RANDOM_TESTS; i++) {
            int n = random.nextInt(MAX_SIZE) + 1;
            int m = Math.min((n * (n - 1)) / 2, random.nextInt(MAX_SIZE));
            Graph graph = new Graph();
            Map<Node, Double> nodes = fillNodes(graph, n);
            Map<Edge, Double> edges = new HashMap<>();
            Node[] nodesArray = nodes.keySet().toArray(new Node[0]);
            Arrays.sort(nodesArray);
            fillEdgesRandomly(graph, m, nodesArray, edges, 1);
            Map<Unit, Double> weights = new HashMap<>();
            nodes.forEach(weights::put);
            edges.forEach(weights::put);
            tests.add(new TestCase(graph, weights, random));
        }
    }

    private Map<Node, Double> fillNodes(Graph graph, int size) {

        Map<Node, Double> nodes = new HashMap<>();
        for (int j = 0; j < size; j++) {
            Node node = new Node(j + 1);
            nodes.put(node, random.nextInt(16) - 8.0);
            graph.addVertex(node);
        }
        return nodes;
    }

    private void fillEdgesRandomly(Graph graph, int count, Node[] nodes, Map<Edge, Double> edges, int offset) {
        int size = graph.vertexSet().size();
        for (int j = 0; j < count; j++) {
            int u = random.nextInt(size);
            int v = random.nextInt(size);
            if (u == v) {
                j--;
                continue;
            }
            double weight = random.nextInt(16) - 8;
            Edge edge = new Edge(offset + j);
            graph.addEdge(nodes[u], nodes[v], edge);
            edges.put(edge, weight);
        }
    }
    private void addPenalties(TestCase test, double pen){
        solver.setEdgePenalty(pen);
        test.signals().addEdgePenalties(-pen);
    }
}
