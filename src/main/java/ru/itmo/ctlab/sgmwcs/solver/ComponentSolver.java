package ru.itmo.ctlab.sgmwcs.solver;

import ru.itmo.ctlab.sgmwcs.Signals;
import ru.itmo.ctlab.sgmwcs.TimeLimit;
import ru.itmo.ctlab.sgmwcs.graph.*;

import java.util.*;
import java.util.concurrent.*;

public class ComponentSolver implements Solver {
    private final int threshold;
    private final boolean preprocess;
    private TimeLimit tl;
    private Double externLB;
    private boolean isSolvedToOptimality;
    private int logLevel;
    private int threads;

    private boolean isEdgePenalty;
    private int preprocessLevel;

    public void setEdgePenalty(double edgePenalty) {
        if (edgePenalty < 0) {
            throw new IllegalArgumentException("Edge penalty must be >= 0");
        }
        isEdgePenalty = edgePenalty > 0;
    }

    public ComponentSolver(int threshold, boolean isEdgePenalty, boolean preprocess) {
        this.threshold = threshold;
        this.isEdgePenalty = isEdgePenalty;
        this.preprocess = preprocess;
        externLB = Double.NEGATIVE_INFINITY;
        tl = new TimeLimit(Double.POSITIVE_INFINITY);
        threads = 1;
    }

    @Override
    public List<Unit> solve(Graph graph, Signals signals) throws SolverException {
        isSolvedToOptimality = true;
        Graph g = new Graph();
        Signals s = new Signals();
        int vertexBefore = graph.vertexSet().size(), edgesBefore = graph.edgeSet().size();
        Utils.copy(graph, signals, g, s);
        Set<Unit> units = new HashSet<>(g.vertexSet());
        units.addAll(g.edgeSet());
        if (logLevel > 0) {
            new GraphPrinter(g, s).printGraph("beforePrep.dot", false);
        }
        long before = System.currentTimeMillis();
        if (preprocess) {
            new Preprocessor(g, s, threads, logLevel, isEdgePenalty).preprocess(preprocessLevel);
        }
        if (logLevel > 0) {
            new GraphPrinter(g, s).printGraph("afterPrep.dot", false);
            System.out.print("Preprocessing deleted " + (vertexBefore - g.vertexSet().size()) + " nodes ");
            System.out.println("and " + (edgesBefore - g.edgeSet().size()) + " edges.");
        }
        isSolvedToOptimality = true;
        if (g.vertexSet().size() == 0) {
            return null;
        }
        return afterPreprocessing(g, new Signals(s, units));
    }

    private List<Unit> afterPreprocessing(Graph graph, Signals signals) throws SolverException {
        long timeBefore = System.currentTimeMillis();
        AtomicDouble lb = new AtomicDouble(externLB);
        PriorityQueue<Set<Node>> components = getComponents(graph);
        List<Worker> memorized = new ArrayList<>();
        BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
        ExecutorService executor = new ThreadPoolExecutor(threads, threads, Long.MAX_VALUE, TimeUnit.NANOSECONDS, queue);
        /*try {
            new SignalsGraph(graph, signals).writeGraph();
        } catch (IOException ignored) {

        }*/
        while (!components.isEmpty()) {
            Set<Node> component = components.poll();
            Graph subgraph = graph.subgraph(component);
            Node root = null;
            double timeRemains = tl.getRemainingTime()
                    - (System.currentTimeMillis() - timeBefore) / 1000.0;
            if (component.size() >= threshold && timeRemains > 0) {
                root = getRoot(subgraph, new Blocks(subgraph));
                if (root != null) {
                    addComponents(subgraph, root, components);
                }
            }
            RLTSolver solver = new RLTSolver();
            solver.setSharedLB(lb);
            solver.setTimeLimit(tl);
            solver.setLogLevel(logLevel);
            Set<Node> vertexSet = subgraph.vertexSet();
            Set<Unit> subset = new HashSet<>(vertexSet);
            subset.addAll(subgraph.edgeSet());
            Signals subSignals = new Signals(signals, subset);
            Node treeRoot = root;
            if (treeRoot == null) {
                treeRoot = vertexSet.stream().max(Comparator.comparing(signals::weight)).orElse(null);
            }
            if (treeRoot != null) {
                MSTSolver ms = new MSTSolver(
                        subgraph,
                        RLTSolver.makeHeuristicWeights(subgraph, subSignals),
                        treeRoot
                );
                ms.solve();
                Graph subtree = subgraph.subgraph(vertexSet, ms.getEdges());
                TreeSolver ts = new TreeSolver(subtree, subSignals);
                TreeSolver.Solution sol = ts.solveRooted(treeRoot);
                double tlb = subSignals.weightSum(sol.sets());
                double plb = lb.get();
                if (tlb >= plb) {
                    System.out.println("found lb " + tlb);
                    lb.compareAndSet(plb, tlb);
                    solver.setInitialSolution(sol.units);
                }
            }
            Worker worker = new Worker(subgraph, root,
                    subSignals, solver, timeBefore);
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

    /*private Node getRootGSTP(Graph graph, Blocks blocks) {
        if (blocks.cutpoints().isEmpty()) {
            return null;
        }
        Node v = blocks.cutpoints().iterator().next();
        blocks.

    }*/

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
        graph = graph.subgraph(graph.vertexSet());
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

    @Override
    public double getLB() {
        return externLB;
    }

    public void setPreprocessingLevel(int preprocessLevel) {
        this.preprocessLevel = preprocessLevel
    }

    public static class SetComparator implements Comparator<Set<Node>> {
        @Override
        public int compare(Set<Node> o1, Set<Node> o2) {
            return -Integer.compare(o1.size(), o2.size());
        }
    }
}
