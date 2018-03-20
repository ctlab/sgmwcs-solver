package ru.ifmo.ctddev.gmwcs.solver;

import apple.laf.JRSUIUtils;
import ru.ifmo.ctddev.gmwcs.Signals;
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
            this.units = Collections.emptySet();
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
        Solution nonEmpty = new Solution(Collections.singleton(root));
        Solution empty = new Solution();
        Set<Integer> rootSets = new HashSet<>(parentSets);
        rootSets.addAll(s.unitSets(root));
        if (parent != null) {
            nodes.remove(parent);
            rootSets.addAll(s.unitSets(g.getEdge(root, parent)));
        }
        if (nodes.isEmpty()
                && s.minSum(root) < 0
                && parentSets.containsAll(s.positiveUnitSets(root))) {
            return empty;
        } else for (Node node : nodes) {
            Solution childSol = solve(node, root, rootSets);
            childSol.sets().removeAll(rootSets);
            if (s.weightSum(childSol.sets()) >= 0) {
                nonEmpty.units.addAll(childSol.units);
                rootSets.addAll(childSol.sets());
            }
        }
        return nonEmpty;
    }
}
