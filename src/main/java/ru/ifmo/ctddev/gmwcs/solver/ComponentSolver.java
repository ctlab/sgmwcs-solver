package ru.ifmo.ctddev.gmwcs.solver;

import org.jgrapht.Graphs;
import org.jgrapht.UndirectedGraph;
import org.jgrapht.alg.ConnectivityInspector;
import ru.ifmo.ctddev.gmwcs.LDSU;
import ru.ifmo.ctddev.gmwcs.TimeLimit;
import ru.ifmo.ctddev.gmwcs.graph.Blocks;
import ru.ifmo.ctddev.gmwcs.graph.Edge;
import ru.ifmo.ctddev.gmwcs.graph.Node;
import ru.ifmo.ctddev.gmwcs.graph.Unit;

import java.util.*;

public class ComponentSolver implements Solver {
    public final int threshold;
    private final RootedSolver solver;
    private TimeLimit tl;
    private double lb;
    private boolean isSolvedToOptimality;

    public ComponentSolver(RootedSolver solver, int threshold) {
        this.solver = solver;
        this.threshold = threshold;
        lb = 0;
        tl = new TimeLimit(Double.POSITIVE_INFINITY);
    }

    @Override
    public List<Unit> solve(UndirectedGraph<Node, Edge> graph, LDSU<Unit> synonyms) throws SolverException {
        double time = tl.getRemainingTime();
        isSolvedToOptimality = true;
        List<Unit> best = null;
        double lb = this.lb;
        PriorityQueue<Set<Node>> components = getComponents(graph);
        solver.setTimeLimit(new TimeLimit(tl.getRemainingTime()));
        double tlFactor = 1;
        while (!components.isEmpty()) {
            tlFactor /= 2;
            Set<Node> component = components.poll();
            UndirectedGraph<Node, Edge> subgraph = Utils.subgraph(graph, component);
            Node root = null;
            if (component.size() >= threshold) {
                root = getRoot(subgraph);
            } else if (components.isEmpty() || components.peek().size() < 50) {
                // there will be no more big components! can take all the time we need
                tlFactor *= 2;
            }

            if (component.size() < 50) {
                solver.suppressOutput();
            }
            
            solver.setRoot(root);
            solver.setLB(lb);
            solver.setTimeLimit(new TimeLimit(tl.getRemainingTime() * tlFactor));
            List<Unit> solution = solver.solve(subgraph, synonyms);
            if (!solver.isSolvedToOptimality()) {
                isSolvedToOptimality = false;
            }
            if (Utils.sum(solution, synonyms) > Utils.sum(best, synonyms)) {
                best = solution;
                lb = Utils.sum(best, synonyms);
            }
           
            if (root != null) {
                addComponents(subgraph, root, components);
            }
        }
        return best;
    }

    @Override
    public boolean isSolvedToOptimality() {
        return isSolvedToOptimality;
    }

    private void addComponents(UndirectedGraph<Node, Edge> subgraph, Node root, PriorityQueue<Set<Node>> components) {
        subgraph.removeVertex(root);
        ConnectivityInspector<Node, Edge> inspector = new ConnectivityInspector<>(subgraph);
        components.addAll(inspector.connectedSets());
    }

    private Node getRoot(UndirectedGraph<Node, Edge> graph) {
        Blocks blocks = new Blocks(graph);
        if (blocks.cutpoints().isEmpty()) {
            return null;
        }
        int min = Integer.MAX_VALUE;
        Node res = null;
        for (Node cp : blocks.cutpoints()) {
            int curr = dfs(graph, cp, true, new HashSet<>());
            if (curr < min) {
                min = curr;
                res = cp;
            }
        }
        return res;
    }

    private int dfs(UndirectedGraph<Node, Edge> graph, Node v, boolean isMax, Set<Node> vis) {
        vis.add(v);
        int res = 0;
        for (Node u : Graphs.neighborListOf(graph, v)) {
            if (!vis.contains(u)) {
                int val = dfs(graph, u, false, vis);
                if (isMax) {
                    res = Math.max(val, res);
                } else {
                    res += val;
                }
            }
        }
        return isMax ? res : res + 1;
    }

    private PriorityQueue<Set<Node>> getComponents(UndirectedGraph<Node, Edge> graph) {
        ConnectivityInspector<Node, Edge> inspector = new ConnectivityInspector<>(graph);
        PriorityQueue<Set<Node>> result = new PriorityQueue<>(new SetComparator());
        result.addAll(inspector.connectedSets());
        return result;
    }

    @Override
    public void setTimeLimit(TimeLimit tl) {
        this.tl = tl;
    }

    @Override
    public void suppressOutput() {
        solver.suppressOutput();
    }

    @Override
    public void setLB(double lb) {
        if (lb < 0.0) {
            return;
        }
        this.lb = lb;
    }

    private static class SetComparator implements Comparator<Set<Node>> {
        @Override
        public int compare(Set<Node> o1, Set<Node> o2) {
            return -Integer.compare(o1.size(), o2.size());
        }
    }
}
