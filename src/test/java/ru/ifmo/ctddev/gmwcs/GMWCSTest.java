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
import ru.ifmo.ctddev.gmwcs.solver.Solver;
import ru.ifmo.ctddev.gmwcs.solver.SolverException;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static ru.ifmo.ctddev.gmwcs.solver.Utils.sum;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class GMWCSTest {
    private static final int SEED = 20160309;
    private static final int TESTS_PER_SIZE = 300;
    private static final int MAX_SIZE = 15;
    private static final int RANDOM_TESTS = 2200;
    private List<TestCase> tests;
    private Solver solver;
    private ReferenceSolver referenceSolver;
    private Random random;

    public GMWCSTest() {
        random = new Random(SEED);
        this.solver = new ComponentSolver(3);
        tests = new ArrayList<>();
        referenceSolver = new ReferenceSolver();
        makeConnectedGraphs();
        makeUnconnectedGraphs();
    }

    @Test
    public void test01_empty() throws SolverException {
        Graph graph = new Graph();
        solver.setLogLevel(0);
        List<Unit> res = solver.solve(graph, new LDSU<>());
        if (!(res == null || res.isEmpty())) {
            Assert.assertTrue("An empty graph can't contain non-empty subgraph", false);
        }
    }

    @Test
    public void test02_connected() {
        int allTests = MAX_SIZE * TESTS_PER_SIZE;
        for (int i = 0; i < allTests; i++) {
            TestCase test = tests.get(i);
            check(test, i);
        }
        System.out.println();
    }

    @Test
    public void test03_random() {
        int allTests = MAX_SIZE * TESTS_PER_SIZE;
        for (int i = allTests; i < tests.size(); i++) {
            TestCase test = tests.get(i);
            check(test, i);
        }
        System.out.println();
    }

    private void check(TestCase test, int num) {
        List<Unit> expected = referenceSolver.solve(test.graph(), test.synonyms(), Collections.emptyList());
        List<Unit> actual = null;
        try {
            solver.setLogLevel(0);
            actual = solver.solve(test.graph(), test.synonyms());
        } catch (SolverException e) {
            System.out.println();
            Assert.assertTrue(num + "\n" + e.getMessage(), false);
        }
        if (Math.abs(sum(expected, test.synonyms()) - sum(actual, test.synonyms())) > 0.1) {
            System.err.println();
            System.err.println("Expected: " + sum(expected, test.synonyms()) + ", but actual: "
                    + sum(actual, test.synonyms()));
            reportError(test, expected, num);
            Assert.assertTrue("A test has failed. See *error files.", false);
            System.exit(1);
        }
    }

    private void reportError(TestCase test, List<Unit> expected, int testNum) {
        try (PrintWriter nodeWriter = new PrintWriter("nodes_" + testNum + ".error");
             PrintWriter edgeWriter = new PrintWriter("edges_" + testNum + ".error");
             PrintWriter signalWriter = new PrintWriter("signals" + testNum + ".error")) {
            Graph g = test.graph();
            for (Node v : g.vertexSet()) {
                nodeWriter.println(v.getNum() + "\t" + v.getWeight());
            }
            for (Edge e : g.edgeSet()) {
                Node from = g.getEdgeSource(e);
                Node to = g.getEdgeTarget(e);
                edgeWriter.println(from.getNum() + "\t" + to.getNum() + "\t" + e.getWeight());
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
        LDSU<Unit> signals = test.synonyms();
        Graph g = test.graph();
        for (int i = 0; i < signals.size(); i++) {
            List<Unit> set = signals.set(i);
            for (Unit u : set) {
                if (u instanceof Edge) {
                    Edge e = (Edge) u;
                    signalWriter.print(g.getEdgeSource(e).getNum() + " -- " + g.getEdgeTarget(e).getNum() + "\t");
                } else {
                    signalWriter.print(u.getNum() + "\t");
                }
            }
            if (!set.isEmpty()) {
                signalWriter.println();
            }
        }
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
                Graph graph = new Graph();
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
                tests.add(new TestCase(graph, random));
            }
        }
    }

    private void makeUnconnectedGraphs() {
        for (int i = 0; i < RANDOM_TESTS; i++) {
            int n = random.nextInt(MAX_SIZE) + 1;
            int m = Math.min((n * (n - 1)) / 2, random.nextInt(MAX_SIZE));
            Graph graph = new Graph();
            Node[] nodes = fillNodes(graph, n);
            fillEdgesRandomly(graph, m, nodes, 1);
            tests.add(new TestCase(graph, random));
        }
    }

    private Node[] fillNodes(Graph graph, int size) {
        Node[] nodes = new Node[size];
        for (int j = 0; j < size; j++) {
            nodes[j] = new Node(j + 1, random.nextInt(16) - 8);
            graph.addVertex(nodes[j]);
        }
        return nodes;
    }

    private void fillEdgesRandomly(Graph graph, int count, Node[] nodes, int offset) {
        int size = graph.vertexSet().size();
        for (int j = 0; j < count; j++) {
            int u = random.nextInt(size);
            int v = random.nextInt(size);
            if (u == v) {
                j--;
                continue;
            }
            graph.addEdge(nodes[u], nodes[v], new Edge(offset + j, random.nextInt(16) - 8));
        }
    }

    static {
        try {
            new IloCplex();
        } catch (UnsatisfiedLinkError e) {
            System.exit(1);
        } catch (IloException ignored) {}
    }
}
