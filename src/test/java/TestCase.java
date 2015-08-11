import org.jgrapht.UndirectedGraph;
import ru.ifmo.ctddev.gmwcs.LDSU;
import ru.ifmo.ctddev.gmwcs.graph.Edge;
import ru.ifmo.ctddev.gmwcs.graph.Node;
import ru.ifmo.ctddev.gmwcs.graph.Unit;

import java.util.*;

public class TestCase {
    private UndirectedGraph<Node, Edge> graph;
    private LDSU<Unit> synonyms;
    private Node root;

    public TestCase(UndirectedGraph<Node, Edge> graph, Random random, Node root) {
        this.graph = graph;
        synonyms = new LDSU<>();
        makeSynonyms(synonyms, random, graph.vertexSet());
        makeSynonyms(synonyms, random, graph.edgeSet());
        this.root = root;
    }

    private static void makeSynonyms(LDSU<Unit> synonyms, Random random, Set<? extends Unit> set) {
        if (set.isEmpty()) {
            return;
        }
        List<Unit> units = new ArrayList<>();
        units.addAll(set);
        Collections.shuffle(units, random);
        Unit last = units.get(0);
        synonyms.add(last);
        for (int i = 1; i < units.size(); i++) {
            Unit current = units.get(i);
            synonyms.add(current);
            if (random.nextBoolean()) {
                synonyms.merge(current, last);
                current.setWeight(last.getWeight());
            } else {
                last = current;
            }
        }
    }

    public LDSU<Unit> synonyms() {
        return synonyms;
    }

    public UndirectedGraph<Node, Edge> graph() {
        return graph;
    }

    public int n() {
        return graph.vertexSet().size();
    }

    public int m() {
        return graph.edgeSet().size();
    }

    public Node root() {
        return root;
    }
}
