package ru.ifmo.ctddev.gmwcs.solver;

import org.jgrapht.Graphs;
import org.jgrapht.UndirectedGraph;
import org.jgrapht.graph.SimpleGraph;
import ru.ifmo.ctddev.gmwcs.LDSU;
import ru.ifmo.ctddev.gmwcs.TimeLimit;
import ru.ifmo.ctddev.gmwcs.graph.Edge;
import ru.ifmo.ctddev.gmwcs.graph.Node;
import ru.ifmo.ctddev.gmwcs.graph.Unit;

import java.util.List;
import java.util.PriorityQueue;

public class ComponentSolver implements Solver {
    private final RLTSolver solver;
    private TimeLimit tl;
    private double lb;
    private Integer BFNum;

    public ComponentSolver(RLTSolver solver) {
        this.solver = solver;
        lb = 0.0;
        tl = new TimeLimit(Double.POSITIVE_INFINITY);
    }

    @Override
    public List<Unit> solve(UndirectedGraph<Node, Edge> origin, LDSU<Unit> synonyms) throws SolverException {
        UndirectedGraph<Node, Edge> graph = new SimpleGraph<>(Edge.class);
        Graphs.addGraph(graph, origin);
        if (graph.vertexSet().isEmpty()) {
            return null;
        }
        solver.setLB(lb);
        solver.setTimeLimit(tl);
        solver.setRootsNum(BFNum);
        List<Unit> sol = null;
        if (BFNum != 0) {
            sol = solver.solve(graph, synonyms);
        }
        solver.setRootsNum(0);
        solver.setLB(Math.max(lb, Utils.sum(sol, synonyms)));
        PriorityQueue<Node> nodes = new PriorityQueue<>();
        nodes.addAll(graph.vertexSet());
        for (int i = 0; i < BFNum; i++) {
            if (graph.vertexSet().isEmpty()) {
                break;
            }
            graph.removeVertex(nodes.poll());
        }
        List<Unit> sol2 = solver.solve(graph, synonyms);
        return sol2 == null ? sol : sol2;
    }

    @Override
    public void setTimeLimit(TimeLimit tl) {
        this.tl = tl;
    }

    @Override
    public void suppressOutput() {
        solver.suppressOutput();
    }

    @Override
    public void setLB(double lb) {
        if (lb < 0.0) {
            return;
        }
        this.lb = lb;
    }

    public void setBFNum(Integer BFNum) {
        this.BFNum = BFNum;
    }
}
