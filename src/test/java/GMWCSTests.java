import org.jgrapht.UndirectedGraph;
import org.jgrapht.graph.SimpleGraph;
import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import ru.ifmo.ctddev.gmwcs.LDSU;
import ru.ifmo.ctddev.gmwcs.graph.Edge;
import ru.ifmo.ctddev.gmwcs.graph.Node;
import ru.ifmo.ctddev.gmwcs.graph.Unit;
import ru.ifmo.ctddev.gmwcs.solver.*;

import java.io.IOException;
import java.util.*;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class GMWCSTests {
    public static final int SEED = 20140503;
    public static final int TESTS_PER_SIZE = 300;
    public static final int MAX_SIZE = 16;
    public static final int RANDOM_TESTS = 2200;
    public static final Integer DEBUG_TEST = null;
    private List<TestCase> tests;
    private Solver solver;
    private ReferenceSolver referenceSolver;
    private Random random;

    public GMWCSTests() {
        random = new Random(SEED);
        ComponentSolver solver = new ComponentSolver(new RLTSolver(true));
        this.solver = solver;
        tests = new ArrayList<>();
        referenceSolver = new ReferenceSolver();
        if (System.getProperty("skipTests") != null) {
            System.exit(0);
        }
        makeConnectedGraphs();
        makeUnconnectedGraphs();
    }

    @Test
    public void test01_empty() throws SolverException {
        if (DEBUG_TEST != null) {
            return;
        }
        UndirectedGraph<Node, Edge> graph = new SimpleGraph<>(Edge.class);
        List<Unit> res = solver.solve(graph, new LDSU<>());
        if (!(res == null || res.isEmpty())) {
            Assert.assertTrue(false);
        }
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
                TestCase test = tests.get(i);
                System.out.print("\rTest(connected) no. " + (i + 1) + "/" + tests.size());
                System.out.print(": n = " + test.n() + ", m = " + test.m() + "       ");
                check(test, i);
            }
        }
        System.out.println();
    }

    @Test
    public void test04_random() {
        int allTests = MAX_SIZE * TESTS_PER_SIZE;
        if (DEBUG_TEST != null) {
            if (DEBUG_TEST < allTests) {
                return;
            } else {
                check(tests.get(DEBUG_TEST), DEBUG_TEST);
            }
        } else {
            for (int i = allTests; i < tests.size(); i++) {
                TestCase test = tests.get(i);
                System.out.print("\rTest(random) no. " + (i) + "/" + tests.size());
                System.out.print(": n = " + test.n() + ", m = " + test.m() + "       ");
                check(test, i);
            }
        }
        System.out.println();
    }

    private void check(TestCase test, int num) {
        List<Unit> expected = referenceSolver.solve(test.graph(), test.synonyms(), Collections.emptyList());
        List<Unit> actual = null;
        try {
            solver.suppressOutput();
            actual = solver.solve(test.graph(), test.synonyms());
        } catch (SolverException e) {
            System.out.println();
            Assert.assertTrue(num + "\n" + e.getMessage(), false);
        } catch (UnsatisfiedLinkError e) {
            System.err.println();
            System.err.println("java.library.path must point to the directory containing the CPLEX shared library\n" +
                    "try invoking java with java -Djava.library.path=...");
            System.exit(1);
        }
        try {
            if (Math.abs(sum(expected, test.synonyms()) - sum(actual, test.synonyms())) > 0.1) {
                System.err.println(error(test));
                System.err.println("Expected: " + sum(expected, test.synonyms()) + ", but actual: "
                        + sum(actual, test.synonyms()));
                Utils.toXdot(test.graph(), expected, actual);
                System.exit(1);
            }
        } catch (IOException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }

    private String error(TestCase test) throws IOException {
        System.err.println();
        Set<Unit> visited = new LinkedHashSet<>();
        Set<Unit> toVisit = new LinkedHashSet<>();
        toVisit.addAll(test.graph().vertexSet());
        toVisit.addAll(test.graph().edgeSet());
        String message = "";
        for (Unit unit : toVisit) {
            if (visited.contains(unit)) {
                continue;
            }
            for (Unit element : test.synonyms().listOf(unit)) {
                message += element + " ";
                visited.add(element);
            }
            message += "\n";
        }
        return message;
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
                tests.add(new TestCase(graph, random));
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
            tests.add(new TestCase(graph, random));
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

    private double sum(Collection<? extends Unit> units, LDSU<Unit> synonyms) {
        if (units == null) {
            return 0;
        }
        double result = 0;
        Set<Unit> visited = new LinkedHashSet<>();
        for (Unit unit : units) {
            if (visited.contains(unit)) {
                continue;
            }
            visited.addAll(synonyms.listOf(unit));
            result += unit.getWeight();
        }
        return result;
    }
}
