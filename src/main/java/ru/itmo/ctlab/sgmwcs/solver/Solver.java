package ru.itmo.ctlab.sgmwcs.solver;

import ru.itmo.ctlab.sgmwcs.Signals;
import ru.itmo.ctlab.sgmwcs.TimeLimit;
import ru.itmo.ctlab.sgmwcs.graph.Graph;
import ru.itmo.ctlab.sgmwcs.graph.Unit;

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
