package ru.ifmo.ctddev.gmwcs.solver;

import ru.ifmo.ctddev.gmwcs.graph.Node;

public interface RootedSolver extends Solver {
    void setRoot(Node root);

    void setSolIsTree(boolean solutionIsTree);
}
