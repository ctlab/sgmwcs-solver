package ru.ifmo.ctddev.gmwcs.solver;

import ru.ifmo.ctddev.gmwcs.LDSU;
import ru.ifmo.ctddev.gmwcs.TimeLimit;
import ru.ifmo.ctddev.gmwcs.graph.Graph;
import ru.ifmo.ctddev.gmwcs.graph.Unit;

import java.util.List;

public interface Solver {
    List<Unit> solve(Graph graph, LDSU<Unit> synonyms) throws SolverException;

    boolean isSolvedToOptimality();

    TimeLimit getTimeLimit();

    void setTimeLimit(TimeLimit tl);

    void suppressOutput();

    void setLB(double lb);
}
