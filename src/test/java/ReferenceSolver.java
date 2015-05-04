import org.jgrapht.UndirectedGraph;
import org.jgrapht.alg.ConnectivityInspector;
import org.jgrapht.graph.UndirectedSubgraph;
import ru.ifmo.ctddev.gmwcs.Edge;
import ru.ifmo.ctddev.gmwcs.Node;
import ru.ifmo.ctddev.gmwcs.Unit;

import java.util.*;

public class ReferenceSolver {
    public List<Unit> solve(UndirectedGraph<Node, Edge> graph, List<Node> roots) {
        for (Node root : roots) {
            if (!graph.containsVertex(root)) {
                throw new IllegalArgumentException();
            }
        }
        if (graph.edgeSet().size() > 31) {
            throw new IllegalArgumentException();
        }
        List<Unit> maxSet = Collections.emptyList();
        double max = roots.isEmpty() ? 0 : -Double.MAX_VALUE;
        // Isolated vertices
        for (Node node : graph.vertexSet()) {
            if ((roots.isEmpty() || (roots.size() == 1 && roots.get(0) == node)) && node.getWeight() > max) {
                max = node.getWeight();
                maxSet = new ArrayList<>();
                maxSet.add(node);
            }
        }
        Edge[] edges = new Edge[graph.edgeSet().size()];
        int m = 0;
        for (Edge edge : graph.edgeSet()) {
            edges[m++] = edge;
        }
        for (int i = 0; i < (1 << m); i++) {
            Set<Edge> currEdges = new LinkedHashSet<>();
            for (int j = 0; j < m; j++) {
                if ((i & (1 << j)) != 0) {
                    currEdges.add(edges[j]);
                }
            }
            UndirectedGraph<Node, Edge> subgraph = new UndirectedSubgraph<>(graph, graph.vertexSet(), currEdges);
            ConnectivityInspector<Node, Edge> inspector = new ConnectivityInspector<>(subgraph);
            for (Set<Node> component : inspector.connectedSets()) {
                if (component.size() == 1) {
                    subgraph.removeVertex(component.iterator().next());
                }
            }
            inspector = new ConnectivityInspector<>(subgraph);
            if (inspector.connectedSets().size() == 1) {
                Set<Node> res = inspector.connectedSets().iterator().next();
                boolean containsRoots = true;
                for (Node root : roots) {
                    if (!res.contains(root)) {
                        containsRoots = false;
                        break;
                    }
                }
                double candidate = sum(res) + sum(currEdges);
                if (containsRoots && candidate > max) {
                    max = candidate;
                    maxSet = new ArrayList<>();
                    maxSet.addAll(res);
                    maxSet.addAll(currEdges);
                }
            }
        }
        return maxSet;
    }

    private double sum(Collection<? extends Unit> units) {
        double result = 0;
        for (Unit unit : units) {
            result += unit.getWeight();
        }
        return result;
    }
}
