package ru.ifmo.ctddev.gmwcs.solver;

import org.jgrapht.UndirectedGraph;
import ru.ifmo.ctddev.gmwcs.LDSU;
import ru.ifmo.ctddev.gmwcs.TimeLimit;
import ru.ifmo.ctddev.gmwcs.graph.Edge;
import ru.ifmo.ctddev.gmwcs.graph.Node;
import ru.ifmo.ctddev.gmwcs.graph.Unit;

import java.util.List;

public class Worker implements Runnable {
    private final LDSU<Unit> synonyms;
    private final UndirectedGraph<Node, Edge> graph;
    private final RootedSolver solver;
    private final Node root;
    private List<Unit> result;
    private boolean isSolvedToOptimality;
    private boolean isOk;
    private long startTime;

    public Worker(UndirectedGraph<Node, Edge> graph, Node root, LDSU<Unit> synonyms, RootedSolver solver, long time) {
        this.solver = solver;
        this.graph = graph;
        this.synonyms = synonyms;
        this.root = root;
        solver.suppressOutput();
        isSolvedToOptimality = true;
        isOk = true;
        startTime = time;
    }

    @Override
    public void run() {
        solver.setRoot(root);
        double tl = solver.getTimeLimit().getRemainingTime() - (System.currentTimeMillis() - startTime) / 1000.0;
        solver.setTimeLimit(new TimeLimit(Math.max(tl, 0.0)));
        try {
            List<Unit> sol = solver.solve(graph, synonyms);
            if (Utils.sum(sol, synonyms) > Utils.sum(result, synonyms)) {
                result = sol;
            }
        } catch (SolverException e) {
            isOk = false;
        }
        if (!solver.isSolvedToOptimality()) {
            isSolvedToOptimality = false;
        }
    }

    public List<Unit> getResult(){
        return result;
    }

    public boolean isSolvedToOptimality(){
        return isSolvedToOptimality;
    }

    public boolean isOk(){
        return isOk;
    }
}
