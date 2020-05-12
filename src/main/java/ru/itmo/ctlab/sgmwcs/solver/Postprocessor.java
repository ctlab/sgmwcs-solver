package ru.itmo.ctlab.sgmwcs.solver;

import ru.itmo.ctlab.sgmwcs.Signals;
import ru.itmo.ctlab.sgmwcs.graph.Edge;
import ru.itmo.ctlab.sgmwcs.graph.Graph;
import ru.itmo.ctlab.sgmwcs.graph.Node;
import ru.itmo.ctlab.sgmwcs.graph.Unit;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by Nikolay Poperechnyi on 06.05.20.
 */
public class Postprocessor {

    private final int logLevel;
    private Graph g;
    private Signals s;

    private List<Unit> solution;

    public Postprocessor(Graph g, Signals s, List<Unit> sol, int logLevel) {
        this.solution = sol;
        this.s = s;
        this.g = g;
        this.logLevel = logLevel;
    }

    public List<Unit> minimize() throws SolverException {
        Set<Double> weights = new HashSet<>();
        Set<Integer> sets = new HashSet<>();
        Set<Node> toRemove = new HashSet<>();
        for (Unit u : solution) {
            weights.add(s.weight(u));
            sets.addAll(s.unitSets(u));
        }
        for (Node r : g.vertexSet()) {
            if (!sets.containsAll(s.unitSets(r))) {
                toRemove.add(r);
            }
            /*if (!weights.contains(s.weight(r)) && s.weight(r) <= 0) {
                toRemove.add(r);
            }*/
        }
        for (Node r : toRemove) {
            Set<Edge> es = g.edgesOf(r);
            for (Edge e : es) {
                s.remove(e);
            }
            g.removeVertex(r);
            s.remove(r);
        }
        for (Edge e : new HashSet<>(g.edgeSet())) {
/*            if (!weights.contains(s.weight(e))) {
                g.removeEdge(e);
            }*/

        }
        s.addEdgePenalties(-0.001);
        ComponentSolver solver = new ComponentSolver(25, false);
        solver.setPreprocessingLevel(0);
        solver.setThreadsNum(4);
        solver.setLogLevel(logLevel);
        List<Unit> res = solver.solve(g, s);
        System.out.println(solution.containsAll(res));
        return res;
    }
}


