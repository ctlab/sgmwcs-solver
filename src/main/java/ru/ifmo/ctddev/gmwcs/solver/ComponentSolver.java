package ru.ifmo.ctddev.gmwcs.solver;

import org.jgrapht.UndirectedGraph;
import org.jgrapht.alg.ConnectivityInspector;
import ru.ifmo.ctddev.gmwcs.graph.Edge;
import ru.ifmo.ctddev.gmwcs.graph.Node;
import ru.ifmo.ctddev.gmwcs.graph.Unit;

import java.util.List;
import java.util.Set;

public class ComponentSolver implements Solver {
    private final Solver solver;

    public ComponentSolver(Solver solver) {
        this.solver = solver;
    }

    @Override
    public List<Unit> solve(UndirectedGraph<Node, Edge> graph) throws SolverException {
        ConnectivityInspector<Node, Edge> inspector = new ConnectivityInspector<>(graph);
        double max = 0.0;
        List<Unit> best = null;
        for (Set<Node> component : inspector.connectedSets()) {
            List<Unit> result = solver.solve(Utils.subgraph(graph, component));
            if (Utils.sum(result) > max) {
                max = Utils.sum(result);
                best = result;
            }
        }
        return best;
    }
}
