package ru.ifmo.ctddev.gmwcs.solver;

import ru.ifmo.ctddev.gmwcs.Signals;
import ru.ifmo.ctddev.gmwcs.TimeLimit;
import ru.ifmo.ctddev.gmwcs.graph.Blocks;
import ru.ifmo.ctddev.gmwcs.graph.Graph;
import ru.ifmo.ctddev.gmwcs.graph.Node;
import ru.ifmo.ctddev.gmwcs.graph.Unit;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class ComponentSolver implements Solver {
    public final int threshold;
    private TimeLimit tl;
    private Double externLB;
    private boolean isSolvedToOptimality;
    private int logLevel;
    private int threads;
    private double edgePenalty;

    public ComponentSolver(int threshold) {
        this.threshold = threshold;
        externLB = 0.0;
        tl = new TimeLimit(Double.POSITIVE_INFINITY);
        threads = 1;
        edgePenalty = 0;
    }

    @Override
    public List<Unit> solve(Graph graph, Signals signals) throws SolverException {
        isSolvedToOptimality = true;
        Graph g = new Graph();
        Signals s = new Signals();
        int vertexBefore = graph.vertexSet().size(), edgesBefore = graph.edgeSet().size();
        Utils.copy(graph, signals, g, s);
        graph = g;
        signals = s;
        Set<Unit> units = new HashSet<>(g.vertexSet());
        units.addAll(g.edgeSet());
        signals = new Signals(signals, units);
        if (edgePenalty == 0) {
            Preprocessor.preprocess(g, signals);
            if (logLevel > 0) {
                System.out.print("Preprocessing deleted " + (vertexBefore - graph.vertexSet().size()) + " nodes ");
                System.out.println("and " + (edgesBefore - graph.edgeSet().size()) + " edges.");
            }
        }
        isSolvedToOptimality = true;
        if (g.vertexSet().size() == 0) {
            return null;
        }
        return afterPreprocessing(g, new Signals(signals, units));
    }

    private List<Unit> afterPreprocessing(Graph graph, Signals signals) throws SolverException {
        long timeBefore = System.currentTimeMillis();
        AtomicDouble lb = new AtomicDouble(externLB);
        PriorityQueue<Set<Node>> components = getComponents(graph);
        List<Worker> memorized = new ArrayList<>();
        BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
        ExecutorService executor = new ThreadPoolExecutor(threads, threads, Long.MAX_VALUE, TimeUnit.NANOSECONDS, queue);
        while (!components.isEmpty()) {
            Set<Node> component = components.poll();
            Graph subgraph = graph.subgraph(component);
            Node root = null;
            double timeRemains = tl.getRemainingTime() - (System.currentTimeMillis() - timeBefore) / 1000.0;
            if (component.size() >= threshold && timeRemains > 0) {
                root = getRoot(subgraph, new Blocks(subgraph));
                if (root != null) {
                    addComponents(subgraph, root, components);
                }
            }
            RLTSolver solver = new RLTSolver();
            solver.setSharedLB(lb);
            solver.setTimeLimit(tl);
            solver.setEdgePenalty(edgePenalty);
            solver.setLogLevel(logLevel);
            Set<Unit> subset = new HashSet<>(subgraph.vertexSet());
            subset.addAll(subgraph.edgeSet());
            Worker worker = new Worker(subgraph, root, new Signals(signals, subset), solver, timeBefore);
            executor.execute(worker);
            memorized.add(worker);
        }
        executor.shutdown();
        try {
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException ignored) {
        }
        return getResult(memorized, graph, signals);
    }

    private List<Unit> getResult(List<Worker> memorized, Graph graph, Signals signals) {
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

    private Node getRoot(Graph graph, Blocks blocks) {
        Map<Node, Integer> maximum = new HashMap<>();
        if (blocks.cutpoints().isEmpty()) {
            return null;
        }
        Node v = blocks.cutpoints().iterator().next();
        dfs(v, null, blocks, maximum, graph.vertexSet().size());
        if (maximum.isEmpty()) {
            return null;
        }
        Node best = maximum.keySet().iterator().next();
        for (Node u : maximum.keySet()) {
            if (maximum.get(u) < maximum.get(best)) {
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

    private void addComponents(Graph graph, Node root, PriorityQueue<Set<Node>> components) {
        Graph copy = graph.subgraph(graph.vertexSet());
        graph = copy;
        graph.removeVertex(root);
        components.addAll(graph.connectedSets());
    }

    private PriorityQueue<Set<Node>> getComponents(Graph graph) {
        PriorityQueue<Set<Node>> result = new PriorityQueue<>(new SetComparator());
        result.addAll(graph.connectedSets());
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

    public void setEdgePenalty(double edgePenalty) {
        this.edgePenalty = edgePenalty;
    }

    @Override
    public void setLogLevel(int logLevel) {
        this.logLevel = logLevel;
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
