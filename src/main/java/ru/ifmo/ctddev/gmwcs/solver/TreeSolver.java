package ru.ifmo.ctddev.gmwcs.solver;

import ru.ifmo.ctddev.gmwcs.Signals;
import ru.ifmo.ctddev.gmwcs.graph.Edge;
import ru.ifmo.ctddev.gmwcs.graph.Graph;
import ru.ifmo.ctddev.gmwcs.graph.Node;
import ru.ifmo.ctddev.gmwcs.graph.Unit;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by Nikolay Poperechnyi on 17.03.18.
 */
public class TreeSolver {

    class Solution {
        Set<Unit> units;

        Solution() {
            this.units = new HashSet<>();
        }

        Set<Integer> sets() {
            return s.unitSets(units);
        }

        Solution(Set<Unit> units) {
            this.units = units;
        }

    }

    private final Graph g;
    private final Signals s;
    private Set<Unit> withoutRoot;
    private Set<Unit> withRoot;

    public Set<Unit> solutionWithoutRoot() {
        return withoutRoot;
    }

    public Set<Unit> solutionWithRoot() {
        return withRoot;
    }


    public TreeSolver(Graph g, Signals s) {
        this.g = g;
        this.s = s;
    }

    public Solution solveRooted(Node root) {
        return solve(root, null, Collections.emptySet());
    }

    private Solution solve(Node root, Node parent, Set<Integer> parentSets) {
        List<Node> nodes = g.neighborListOf(root);
        assert (parent == null || nodes.contains(parent));
        Set<Unit> rootSet = new HashSet<>();
        rootSet.add(root);
        Solution nonEmpty = new Solution(rootSet);
        Solution empty = new Solution();
        if (parent != null) {
            Edge e = g.getEdge(root, parent);
            nodes.remove(parent);
            rootSet.add(e);
        }
        if (nodes.isEmpty()
                && s.minSum(root) < 0
                && parentSets.containsAll(s.positiveUnitSets(root))) {
            return empty;
        } else for (Node node : nodes) {
            Set<Integer> signals = nonEmpty.sets();
            Solution childSol = solve(node, root, signals);
            childSol.sets().removeAll(signals);
            if (s.weightSum(childSol.sets()) >= 0) {
                nonEmpty.units.addAll(childSol.units);
            }
        }
        return nonEmpty;
    }
}
