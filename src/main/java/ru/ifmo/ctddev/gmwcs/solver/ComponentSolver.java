package ru.ifmo.ctddev.gmwcs.solver;

import org.jgrapht.UndirectedGraph;
import org.jgrapht.alg.ConnectivityInspector;
import ru.ifmo.ctddev.gmwcs.LDSU;
import ru.ifmo.ctddev.gmwcs.TimeLimit;
import ru.ifmo.ctddev.gmwcs.graph.Edge;
import ru.ifmo.ctddev.gmwcs.graph.Node;
import ru.ifmo.ctddev.gmwcs.graph.Unit;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ComponentSolver implements Solver {
    private final RLTSolver solver;
    private TimeLimit tl;
    private double lb;
    private boolean suppressing;

    public ComponentSolver(RLTSolver solver) {
        this.solver = solver;
        lb = 0.0;
        tl = new TimeLimit(Double.POSITIVE_INFINITY);
    }

    @Override
    public List<Unit> solve(UndirectedGraph<Node, Edge> graph, LDSU<Unit> synonyms) throws SolverException {
        int remains = graph.vertexSet().size();
        ConnectivityInspector<Node, Edge> inspector = new ConnectivityInspector<>(graph);
        List<Unit> best = new ArrayList<>();
        double lb = this.lb;
        for (Set<Node> component : inspector.connectedSets()) {
            TimeLimit local;
            if (tl.getRemainingTime() == Double.POSITIVE_INFINITY) {
                local = tl;
            } else {
                local = tl.subLimit((double) component.size() / remains);
            }
            remains -= component.size();
            solver.setLB(lb);
            solver.setTimeLimit(local);
            List<Unit> curr = solver.solve(Utils.subgraph(graph, component), synonyms);
            double currScore = Utils.sum(curr, synonyms);
            if (currScore > lb) {
                lb = currScore;
                best = curr;
            }
        }
        return best;
    }

    @Override
    public void setTimeLimit(TimeLimit tl) {
        this.tl = tl;
    }

    @Override
    public void suppressOutput() {
        this.suppressing = true;
        solver.suppressOutput();
    }

    @Override
    public void setLB(double lb) {
        if (lb < 0.0) {
            return;
        }
        this.lb = lb;
    }
}
