package ru.itmo.ctlab.sgmwcs.solver;

import ru.itmo.ctlab.sgmwcs.Signals;
import ru.itmo.ctlab.sgmwcs.graph.Edge;
import ru.itmo.ctlab.sgmwcs.graph.Graph;
import ru.itmo.ctlab.sgmwcs.graph.Node;
import ru.itmo.ctlab.sgmwcs.graph.Unit;

import java.util.*;

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
                && parentSets.containsAll(
                s.positiveUnitSets(nonEmpty.units))) {
            return empty;
        } else {
            List<Solution> childSols = new ArrayList<>();
            Set<Integer> signals = new HashSet<>(nonEmpty.sets());
            signals.addAll(parentSets);
            for (Node node : nodes) {
                childSols.add(solve(node, root, signals));
            }
            /*while (!childSols.isEmpty()) {
                Solution max = childSols.stream().max(
                        Comparator.comparingDouble(sol -> s.weightSum(sol.sets()))
                ).get();
                max.sets().removeAll(sigs);
                if (s.weightSum(max.sets()) < 0) {
                    break;
                } else {
                    childSols.remove(max);
                    nonEmpty.units.addAll(max.units);
                }
            }*/
            for (Solution childSol: childSols) {
                Set<Integer> childSets = new HashSet<>(childSol.sets());
//                 Set<Integer> setSum = new HashSet<>(childSets);
   //             setSum.addAll(sigs);
                childSets.addAll(signals);
                if (s.weightSum(childSets) >= s.weightSum(signals)) {
                    nonEmpty.units.addAll(childSol.units);
                }
            }
        }
        return nonEmpty;
    }
}
