package ru.ifmo.ctddev.gmwcs.solver;

import ru.ifmo.ctddev.gmwcs.Signals;
import ru.ifmo.ctddev.gmwcs.TimeLimit;
import ru.ifmo.ctddev.gmwcs.graph.Edge;
import ru.ifmo.ctddev.gmwcs.graph.Graph;
import ru.ifmo.ctddev.gmwcs.graph.Node;
import ru.ifmo.ctddev.gmwcs.graph.Unit;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class Worker implements Runnable {
    private final Signals signals;
    private final Graph graph;
    private final RLTSolver solver; //was RootedSolver, TODO!!!!!!!!!!!!
    private final Node root;
    private List<Unit> result;
    private boolean isSolvedToOptimality;
    private boolean isOk;
    private long startTime;
    private int logLevel; // todo

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
        solver.setRoot(root);
        if (root != null) {
            BlockPreprocessing bp = new BlockPreprocessing(graph, signals, root);
            bp.setLB(solver.getLB());
            Set<Node> toRemove = bp.result();
            toRemove.forEach(n -> {
                Set<Edge> edges = graph.edgesOf(n);
                graph.removeVertex(n);
                edges.forEach(signals::remove);
                signals.remove(n);
            });
            if (bp.psd.ub() < bp.getLB()) {
                result = Collections.emptyList();
                return;
            }
            solver.setSolIsTree(bp.psd.solutionIsTree);
            solver.setPSD(bp.psd);
            if (logLevel > 1 || true) {
                System.out.println("Block Preprocessing removed " + toRemove.size() + " nodes.");
            }
        }
        double tl = solver.getTimeLimit().getRemainingTime() - (System.currentTimeMillis() - startTime) / 1000.0;
        if (tl <= 0) {
            isSolvedToOptimality = false;
            return;
        }
        solver.setTimeLimit(new TimeLimit(Math.max(tl, 0.0)));
        if (graph.vertexSet().isEmpty()) {
            result = Collections.emptyList();
        } else try {
            Set<Node> comp = null;
            if (graph.connectedSets().size() > 1) {
                comp = graph.connectedSets().stream().filter(s -> s.contains(root)).findFirst().get();
            }
            List<Unit> sol = solver.solve(comp != null ? graph.subgraph(comp) : graph, signals);
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
