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
import java.util.concurrent.*;

public class ComponentSolver implements Solver {
    public final int threshold;
    private TimeLimit tl;
    private Double externLB;
    private boolean isSolvedToOptimality;
    private boolean suppressingOutput;
    private int threads;

    public ComponentSolver(int threshold) {
        this.threshold = threshold;
        externLB = 0.0;
        tl = new TimeLimit(Double.POSITIVE_INFINITY);
        threads = 1;
    }

    @Override
    public List<Unit> solve(UndirectedGraph<Node, Edge> graph, LDSU<Unit> synonyms) throws SolverException {
        long timeBefore = System.currentTimeMillis();
        isSolvedToOptimality = true;
        UndirectedGraph<Node, Edge> g = new Multigraph<>(Edge.class);
        Graphs.addGraph(g, graph);
        Set<Unit> units = new HashSet<>(g.vertexSet());
        units.addAll(g.edgeSet());
        synonyms = new LDSU<>(synonyms, units);
        Preprocessor.preprocess(g, synonyms);
        if (!suppressingOutput) {
            System.out.print("Preprocessing deleted " + (graph.vertexSet().size() - g.vertexSet().size()) + " nodes ");
            System.out.println("and " + (graph.edgeSet().size() - g.edgeSet().size()) + " edges.");
        }
        graph = g;
        AtomicDouble lb = new AtomicDouble(externLB);
        PriorityQueue<Set<Node>> components = getComponents(graph);
        List<Worker> memorized = new ArrayList<>();
        BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
        ExecutorService executor = new ThreadPoolExecutor(threads, threads, Long.MAX_VALUE, TimeUnit.NANOSECONDS, queue);
        while(!components.isEmpty()){
            Set<Node> component = components.poll();
            UndirectedGraph<Node, Edge> subgraph = Utils.subgraph(graph, component);
            Node root = null;
            if(component.size() >= threshold){
                root = getRoot(subgraph);
                if(root != null){
                    addComponents(subgraph, root, components);
                }
            }
            RLTSolver solver = new RLTSolver();
            solver.setSharedLB(lb);
            solver.setTimeLimit(tl);
            if(suppressingOutput){
                solver.suppressOutput();
            }
            Set<Unit> subset = new HashSet<>(subgraph.vertexSet());
            subset.addAll(subgraph.edgeSet());
            Worker worker = new Worker(subgraph, root, new LDSU<>(synonyms, subset), solver, timeBefore);
            executor.execute(worker);
            memorized.add(worker);
        }
        executor.shutdown();
        try {
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException ignored) {
        }
        return getResult(memorized, graph, synonyms);
    }

    private List<Unit> getResult(List<Worker> memorized, UndirectedGraph<Node, Edge> graph, LDSU<Unit> signals) {
        List<Unit> best = null;
        double bestScore = -Double.MAX_VALUE;
        for (Worker worker : memorized) {
            List<Unit> solution = worker.getResult();
            if (bestScore < Utils.sum(solution, signals)) {
                best = solution;
                bestScore = Utils.sum(solution, signals);
            }
            if (!worker.isSolvedToOptimality()) {
                isSolvedToOptimality = false;
            }
        }
        List<Unit> result = extract(best);
        graph.vertexSet().forEach(Unit::clear);
        graph.edgeSet().forEach(Unit::clear);
        return result;
    }

    private List<Unit> extract(List<Unit> s) {
        if (s == null) {
            return null;
        }
        List<Unit> l = new ArrayList<>(s);
        for (Unit u : s) {
            l.addAll(u.getAbsorbed());
        }
        return l;
    }

    @Override
    public boolean isSolvedToOptimality() {
        return isSolvedToOptimality;
    }

    private void addComponents(UndirectedGraph<Node, Edge> subgraph, Node root, PriorityQueue<Set<Node>> components) {
        UndirectedGraph<Node, Edge> copy = new Multigraph<>(Edge.class);
        Graphs.addGraph(copy, subgraph);
        copy.removeVertex(root);
        ConnectivityInspector<Node, Edge> inspector = new ConnectivityInspector<>(copy);
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
    public TimeLimit getTimeLimit() {
        return tl;
    }

    @Override
    public void setTimeLimit(TimeLimit tl) {
        this.tl = tl;
    }

    @Override
    public void suppressOutput() {
        suppressingOutput = true;
    }

    public void setThreadsNum(int n) {
        if (n < 1) {
            throw new IllegalArgumentException();
        }
        threads = n;
    }

    @Override
    public void setLB(double lb) {
        externLB = lb;
    }

    private static class SetComparator implements Comparator<Set<Node>> {
        @Override
        public int compare(Set<Node> o1, Set<Node> o2) {
            return -Integer.compare(o1.size(), o2.size());
        }
    }
}
