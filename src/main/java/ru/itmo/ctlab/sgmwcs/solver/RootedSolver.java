package ru.itmo.ctlab.sgmwcs.solver;

import ru.itmo.ctlab.sgmwcs.graph.Node;

public interface RootedSolver extends Solver {
    void setRoot(Node root);

    void setSolIsTree(boolean solutionIsTree);
}
