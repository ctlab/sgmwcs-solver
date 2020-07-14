package ru.itmo.ctlab.sgmwcs.solver;

import ru.itmo.ctlab.sgmwcs.Signals;
import ru.itmo.ctlab.sgmwcs.TimeLimit;
import ru.itmo.ctlab.sgmwcs.graph.Edge;
import ru.itmo.ctlab.sgmwcs.graph.Graph;
import ru.itmo.ctlab.sgmwcs.graph.Node;
import ru.itmo.ctlab.sgmwcs.graph.Unit;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface Solver {
    List<Unit> solve(Graph graph, Signals signals) throws SolverException;

    boolean isSolvedToOptimality();

    TimeLimit getTimeLimit();

    void setTimeLimit(TimeLimit tl);

    void setLogLevel(int logLevel);

    void setLB(double lb);

    double getLB();

    public static Map<Edge, Double> makeHeuristicWeights(Graph graph, Signals signals) {
        Map<Edge, Double> weights = new HashMap<>();
        for (Edge e : graph.edgeSet()) {
            Node u = graph.getEdgeSource(e), v = graph.getEdgeTarget(e);
            double weightSum = signals.sum(e, u, v);
            if (weightSum > 0) {
                weights.put(e, 1.0 / weightSum); // Edge is non-negative so it has the highest priority
            } else weights.put(e, 2.0);
        }
        return weights;
    }

}
