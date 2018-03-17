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
        Set<Integer> sets;
        private double sum = 0;

        Solution() {
            this.units= Collections.emptySet();
            this.sets = new HashSet<>();
        }

        Solution(Set<Unit> units) {
            this.units = units;
            this.sets = new HashSet<>(s.unitSets(units));
            sum = s.weightSum(sets);
        }

        void addUnits(Set<? extends Unit> us) {
            units.addAll(us);
            for (int set: s.unitSets(us)) {
                if (!sets.contains(set)) {
                  sets.add(set);
                  sum += s.weight(set);
                }
            }
        }

        double sum() {
            return sum;
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

    private Solution solve(Node root, Node parent, Set<Integer> rootSets) {
        List<Node> nodes = g.neighborListOf(root);
        assert (parent == null || nodes.contains(parent));
        Solution single = new Solution(Collections.singleton(root));
        Solution sol = new Solution();
        if (parent != null) {
            nodes.remove(parent);
        }
        if (nodes.isEmpty()) {
            if (s.minSum(root) < 0 && rootSets.containsAll(single.sets)) {
                return sol;
            } else {
                return single;
            }
        } else for (Node node : nodes) {
            double sum = s.weightSum(sol.sets);
            Set<Integer> nodeSets = new HashSet<>(s.unitSets(node));
            nodeSets.addAll(rootSets);
            Solution childSol = solve(node, root, nodeSets);
        }
        return sol;

    }
}
