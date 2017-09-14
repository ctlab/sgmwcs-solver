package ru.ifmo.ctddev.gmwcs;

import ru.ifmo.ctddev.gmwcs.graph.Edge;
import ru.ifmo.ctddev.gmwcs.graph.Graph;
import ru.ifmo.ctddev.gmwcs.graph.Node;
import ru.ifmo.ctddev.gmwcs.graph.Unit;
import ru.ifmo.ctddev.gmwcs.solver.Solver;
import ru.ifmo.ctddev.gmwcs.solver.SolverException;

import java.util.*;

import static ru.ifmo.ctddev.gmwcs.solver.Utils.sum;

public class ReferenceSolver implements Solver {
    public List<Unit> solve(Graph graph, Signals signals, List<Node> roots) {
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
            if ((roots.isEmpty() || (roots.size() == 1 && roots.get(0) == node)) && signals.weight(node) > max) {
                max = signals.weight(node);
                maxSet = new ArrayList<>();
                maxSet.add(node);
            }
        }
        Edge[] edges = graph.edgeSet().stream().toArray(Edge[]::new);
        int m = edges.length;
        for (int i = 0; i < (1 << m); i++) {
            Set<Edge> currEdges = new LinkedHashSet<>();
            for (int j = 0; j < m; j++) {
                if ((i & (1 << j)) != 0) {
                    currEdges.add(edges[j]);
                }
            }

            Graph subgraph = graph.subgraph(graph.vertexSet(), currEdges);
            for (Set<Node> component : subgraph.connectedSets()) {
                if (component.size() == 1) {
                    subgraph.removeVertex(component.iterator().next());
                }
            }
            List<Set<Node>> connectedSets = subgraph.connectedSets();
            if (connectedSets.size() == 1) {
                Set<Node> res = connectedSets.iterator().next();
                boolean containsRoots = true;
                for (Node root : roots) {
                    if (!res.contains(root)) {
                        containsRoots = false;
                        break;
                    }
                }
                Set<Unit> units = new HashSet<>(res);
                units.addAll(currEdges);
                double candidate = sum(units, signals);
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

    @Override
    public List<Unit> solve(Graph graph, Signals signals) throws SolverException {
        return solve(graph, signals, Collections.emptyList());
    }

    @Override
    public boolean isSolvedToOptimality() {
        return true;
    }

    @Override
    public TimeLimit getTimeLimit() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setTimeLimit(TimeLimit tl) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setLogLevel(int logLevel) {
        throw new UnsupportedOperationException();

    }

    @Override
    public void setLB(double lb) {
        throw new UnsupportedOperationException();

    }
}
