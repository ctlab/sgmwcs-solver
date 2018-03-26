package ru.ifmo.ctddev.gmwcs.solver;

import ru.ifmo.ctddev.gmwcs.Signals;
import ru.ifmo.ctddev.gmwcs.TimeLimit;
import ru.ifmo.ctddev.gmwcs.graph.Graph;
import ru.ifmo.ctddev.gmwcs.graph.Node;
import ru.ifmo.ctddev.gmwcs.graph.Unit;

import java.util.List;
import java.util.Set;

public class Worker implements Runnable {
    private final Signals signals;
    private final Graph graph;
    private final RootedSolver solver;
    private final Node root;
    private List<Unit> result;
    private boolean isSolvedToOptimality;
    private boolean isOk;
    private long startTime;


    private int logLevel = 0;

    public Worker(Graph graph, Node root, Signals signals, RootedSolver solver, long time) {
        this.solver = solver;
        this.graph = graph;
        this.signals = signals;
        this.root = root;
        isSolvedToOptimality = true;
        isOk = true;
        startTime = time;
    }

    @Override
    public void run() {
        solver.setRoot(root);
        if (root != null) {
            //   Set<Node> toRemove = new BlockPreprocessing(graph, signals, root).result();
//            if (logLevel > 0) {
//                System.out.println("Block Preprocessing removed " + toRemove.size() + " nodes.");
 //           }
        }
        double tl = solver.getTimeLimit().getRemainingTime() - (System.currentTimeMillis() - startTime) / 1000.0;
        if (tl <= 0) {
            isSolvedToOptimality = false;
            return;
        }
        solver.setTimeLimit(new TimeLimit(Math.max(tl, 0.0)));
        try {
            List<Unit> sol = solver.solve(graph, signals);
            if (Utils.sum(sol, signals) > Utils.sum(result, signals)) {
                result = sol;
            }
        } catch (SolverException e) {
            isOk = false;
        }
        if (!solver.isSolvedToOptimality()) {
            isSolvedToOptimality = false;
        }
    }

    public List<Unit> getResult() {
        return result;
    }

    public boolean isSolvedToOptimality() {
        return isSolvedToOptimality;
    }

    public boolean isOk() {
        return isOk;
    }
}
