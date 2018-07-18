package ru.ifmo.ctddev.gmwcs.solver;

import ru.ifmo.ctddev.gmwcs.Signals;
import ru.ifmo.ctddev.gmwcs.TimeLimit;
import ru.ifmo.ctddev.gmwcs.graph.Graph;
import ru.ifmo.ctddev.gmwcs.graph.Unit;

import java.util.List;

public interface Solver {
    List<Unit> solve(Graph graph, Signals signals) throws SolverException;

    boolean isSolvedToOptimality();

    TimeLimit getTimeLimit();

    void setTimeLimit(TimeLimit tl);

    void setLogLevel(int logLevel);

    void setLB(double lb);

    double getLB();
}
