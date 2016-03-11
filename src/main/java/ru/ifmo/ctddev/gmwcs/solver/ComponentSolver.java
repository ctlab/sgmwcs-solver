package ru.ifmo.ctddev.gmwcs.solver;

import org.jgrapht.Graphs;
import org.jgrapht.UndirectedGraph;
import org.jgrapht.alg.ConnectivityInspector;
import org.jgrapht.graph.Multigraph;
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
    private boolean suppressOutput;

    public ComponentSolver(RootedSolver solver, int threshold) {
        this.solver = solver;
        this.threshold = threshold;
        lb = 0;
        tl = new TimeLimit(Double.POSITIVE_INFINITY);
    }

    @Override
    public List<Unit> solve(UndirectedGraph<Node, Edge> graph, LDSU<Unit> synonyms) throws SolverException {
        UndirectedGraph<Node, Edge> g = new Multigraph<>(Edge.class);
        Graphs.addGraph(g, graph);
        Set<Unit> units = new HashSet<>(g.vertexSet());
        units.addAll(g.edgeSet());
        synonyms = new LDSU<>(synonyms, units);
        Preprocessor.preprocess(g, synonyms);
        if (!suppressOutput) {
            System.out.print("Preprocessing deleted " + (graph.vertexSet().size() - g.vertexSet().size()) + " nodes ");
            System.out.println("and " + (graph.edgeSet().size() - g.edgeSet().size()) + " edges.");
        }
        isSolvedToOptimality = true;
        if(g.vertexSet().size() == 0){
            return null;
        }
        return afterPreprocessing(g, new LDSU<>(synonyms, units));
    }

    private List<Unit> afterPreprocessing(UndirectedGraph<Node, Edge> graph, LDSU<Unit> synonyms) throws SolverException {
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
                root = getRoot(subgraph, new Blocks(subgraph));
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
            Set<Unit> subset = new HashSet<>(subgraph.edgeSet());
            subset.addAll(subgraph.vertexSet());
            List<Unit> solution = solver.solve(subgraph, new LDSU<>(synonyms, subset));
            if (!solver.isSolvedToOptimality()) {
                isSolvedToOptimality = false;
            }
            if (Utils.sum(solution, synonyms) > Utils.sum(best, synonyms)) {
                best = solution;
                lb = Utils.sum(best, synonyms);
            }
            addComponents(subgraph, root, components);
        }
        return extract(best, graph);
    }

    private Node getRoot(UndirectedGraph<Node, Edge> graph, Blocks blocks) {
        Map<Node, Integer> maximum = new HashMap<>();
        if (blocks.cutpoints().isEmpty()) {
            return null;
        }
        Node v = blocks.cutpoints().iterator().next();
        dfs(v, null, blocks, maximum, graph.vertexSet().size());
        if(maximum.isEmpty()){
            return null;
        }
        Node best = maximum.keySet().iterator().next();
        for(Node u : maximum.keySet()){
            if(maximum.get(u) < maximum.get(best)){
                best = u;
            }
        }
        return best;
    }

    private int dfs(Node v, Node p, Blocks blocks, Map<Node, Integer> max, int n) {
        int res = 0;
        for (Set<Node> c : blocks.incidentBlocks(v)) {
            if (c.contains(p)) {
                continue;
            }
            int sum = c.size() - 1;
            for (Node cp : blocks.cutpointsOf(c)) {
                if (cp != v) {
                    sum += dfs(cp, v, blocks, max, n);
                }
            }
            if (!max.containsKey(v) || max.get(v) < sum) {
                max.put(v, sum);
            }
            res += sum;
        }
        int rest = n - res - 1;
        if (!max.containsKey(v) || max.get(v) < rest) {
            max.put(v, rest);
        }
        return res;
    }

    private List<Unit> extract(List<Unit> s, UndirectedGraph<Node, Edge> graph) {
        if(s == null){
            return null;
        }
        List<Unit> l = new ArrayList<>(s);
        for(Unit u : s){
            l.addAll(u.getAbsorbed());
        }
        graph.vertexSet().forEach(Unit::clear);
        graph.edgeSet().forEach(Unit::clear);
        return l;
    }

    @Override
    public boolean isSolvedToOptimality() {
        return isSolvedToOptimality;
    }

    private void addComponents(UndirectedGraph<Node, Edge> subgraph, Node root, PriorityQueue<Set<Node>> components) {
        if(root == null){
            return;
        }
        subgraph.removeVertex(root);
        ConnectivityInspector<Node, Edge> inspector = new ConnectivityInspector<>(subgraph);
        components.addAll(inspector.connectedSets());
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
        suppressOutput = true;
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
