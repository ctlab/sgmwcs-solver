package ru.itmo.ctlab.sgmwcs.solver;

import ru.itmo.ctlab.sgmwcs.Signals;
import ru.itmo.ctlab.sgmwcs.TimeLimit;
import ru.itmo.ctlab.sgmwcs.graph.Graph;
import ru.itmo.ctlab.sgmwcs.graph.Node;
import ru.itmo.ctlab.sgmwcs.graph.Unit;

import java.util.*;
import java.util.stream.Collectors;

public class Worker implements Runnable {
    private final Signals signals;
    private final Graph graph;
    private final RLTSolver solver;
    private final Node root;
    private List<Unit> result;
    private boolean isSolvedToOptimality;
    private boolean isOk;
    private long startTime;
    private int logLevel;

    public Worker(Graph graph, Node root, Signals signals, RLTSolver solver, long time) {
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
        Set<Node> vertexSet = graph.vertexSet();
        solver.setRoot(root);
        //PSD psd = new PSD(graph, signals);
        if (vertexSet.size() <= 1) {
            result = vertexSet.stream().filter(n -> signals.weight(n) >= 0).collect(Collectors.toList());
            return;
        }
        /*if (psd.decompose()) {
            if (root != null) {
                psd.forceVertex(root);
            }
            if (psd.ub() - solver.getLB() < -0.001) {
                System.out.println(psd.ub());
                result = Collections.singletonList(vertexSet.stream().max(
                        Comparator.comparingDouble(signals::weight)).get());
                return;
            }
            solver.setSolIsTree(psd.solutionIsTree);
            solver.setPSD(psd);
        } else {
            result = Collections.emptyList();
            return;
        }*/
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
