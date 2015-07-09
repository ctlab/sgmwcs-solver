import org.jgrapht.UndirectedGraph;
import org.jgrapht.alg.BiconnectivityInspector;
import org.jgrapht.graph.SimpleGraph;
import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import ru.ifmo.ctddev.gmwcs.graph.Blocks;
import ru.ifmo.ctddev.gmwcs.graph.Edge;
import ru.ifmo.ctddev.gmwcs.graph.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class BlockTests {
    public static final int MAX_SIZE = 10;
    public static final int SEED = 20150709;
    public static final int TESTS_PER_SIZE = 5000;
    public static final Integer TEST = null;

    private List<UndirectedGraph<Node, Edge>> tests;
    private Random random;

    public BlockTests() {
        random = new Random(SEED);
        tests = new ArrayList<>();
        for (int n = 2; n <= MAX_SIZE; n++) {
            for (int j = 0; j < TESTS_PER_SIZE; j++) {
                tests.add(generateTest(n));
            }
        }
    }

    @Test
    public void test01_single() {
        UndirectedGraph<Node, Edge> graph = new SimpleGraph<>(Edge.class);
        graph.addVertex(new Node(0, 0.0));
        Blocks blocks = new Blocks(graph);
        Assert.assertTrue(blocks.cutpoints().isEmpty());
        Assert.assertEquals(blocks.components().size(), 1);
        Assert.assertEquals(blocks.components().iterator().next().size(), 1);
    }

    @Test
    public void test02_pair() {
        UndirectedGraph<Node, Edge> graph = new SimpleGraph<>(Edge.class);
        Node v = new Node(0, 0.0);
        Node u = new Node(1, 0.0);
        graph.addVertex(v);
        graph.addVertex(u);
        graph.addEdge(v, u, new Edge(0, 0.0));
        Blocks blocks = new Blocks(graph);
        Assert.assertTrue(blocks.cutpoints().isEmpty());
        Assert.assertEquals(blocks.components().size(), 1);
        Assert.assertEquals(blocks.components().iterator().next().size(), 2);
    }

    @Test
    public void test03_many() {
        if (TEST != null) {
            check(TEST);
            return;
        }
        for (int i = 0; i < tests.size(); i++) {
            check(i);
        }
    }

    private void check(int test) {
        System.out.print("\r" + test);
        System.out.flush();
        UndirectedGraph<Node, Edge> graph = tests.get(test);
        BiconnectivityInspector<Node, Edge> inspector = new BiconnectivityInspector<>(graph);
        Blocks blocks = new Blocks(graph);
        Assert.assertEquals(inspector.getBiconnectedVertexComponents(), blocks.components());
        Assert.assertEquals(inspector.getCutpoints(), blocks.cutpoints());
    }

    private UndirectedGraph<Node, Edge> generateTest(int n) {
        UndirectedGraph<Node, Edge> graph = new SimpleGraph<>(Edge.class);
        Node[] nodes = new Node[n];
        int k = 0;
        for (int i = 0; i < n; i++) {
            nodes[i] = new Node(i, 0.0);
            graph.addVertex(nodes[i]);
            if (i > 0) {
                graph.addEdge(nodes[i - 1], nodes[i], new Edge(k++, 0.0));
            }
        }
        int m = random.nextInt((n + 1) / 2);
        for (int i = 0; i < m; i++) {
            while (true) {
                int v = random.nextInt(n);
                int u = random.nextInt(n);
                if (v != u && graph.getEdge(nodes[v], nodes[u]) == null) {
                    graph.addEdge(nodes[v], nodes[u], new Edge(k++, 0.0));
                    break;
                }
            }
        }
        return graph;
    }
}
