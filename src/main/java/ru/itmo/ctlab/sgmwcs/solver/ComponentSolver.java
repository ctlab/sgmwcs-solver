package ru.itmo.ctlab.sgmwcs.solver;

import ru.itmo.ctlab.sgmwcs.Signals;
import ru.itmo.ctlab.sgmwcs.TimeLimit;
import ru.itmo.ctlab.sgmwcs.graph.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class ComponentSolver implements Solver {
    private final int threshold;
    private TimeLimit tl;
    private Double externLB;
    private boolean isSolvedToOptimality;
    private int logLevel;
    private int threads;
    private boolean cplexOff;

    private boolean minimize;
    private int preprocessLevel;
    private Graph g;
    private Signals s;

    private int[] preprocessedSize = {0, 0};

    public int preprocessedNodes() {
        return preprocessedSize[0];
    }

    public int preprocessedEdges() {
        return preprocessedSize[1];
    }

    public ComponentSolver(int threshold, boolean minimize) {
        this.threshold = threshold;
        this.minimize = minimize;
        externLB = Double.NEGATIVE_INFINITY;
        tl = new TimeLimit(Double.POSITIVE_INFINITY);
        threads = 1;
    }

    @Override
    public List<Unit> solve(Graph graph, Signals signals) throws SolverException {
        this.g = graph;
        this.s = signals;
        isSolvedToOptimality = true;
        Graph g = new Graph();
        Signals s = new Signals();
        int vertexBefore = graph.vertexSet().size(), edgesBefore = graph.edgeSet().size();
        Utils.copy(graph, signals, g, s);
        Set<Unit> units = new HashSet<>(g.vertexSet());
        units.addAll(g.edgeSet());
        if (logLevel > 0) {
            new GraphPrinter(g, s).printGraph("beforePrep.dot", true);
        }
        long before = System.currentTimeMillis();
        new Preprocessor(g, s, threads, logLevel).preprocess(preprocessLevel);
        preprocessedSize[0] = g.vertexSet().size();
        preprocessedSize[1] = g.edgeSet().size();
        if (logLevel > 0) {
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
        // Set<Integer> requiredSigs = new HashSet<>();
        /*for (int sig = 0; sig < signals.size(); sig++) {
            if (signals.weight(sig) == Double.POSITIVE_INFINITY) {
                requiredSigs.add(sig);
            }
        }*/
        AtomicDouble lb = new AtomicDouble(externLB);
        PriorityQueue<Set<Node>> components = getComponents(graph);
        List<Worker> memorized = new ArrayList<>();
        BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
        ExecutorService executor = new ThreadPoolExecutor(threads, threads, Long.MAX_VALUE, TimeUnit.NANOSECONDS, queue);
        List<Unit> bestTree = new ArrayList<>();

        while (!components.isEmpty()) {
            Set<Node> component = components.poll();
            Graph subgraph = graph.subgraph(component);
            Node root = null;
            /*Set<Integer> us = subgraph.vertexSet().stream()
                    .flatMap(n -> signals.unitSets(n).stream())
                    .collect(Collectors.toSet());
            if (!us.containsAll(requiredSigs)) continue;*/
            double timeRemains = tl.getRemainingTime()
                    - (System.currentTimeMillis() - timeBefore) / 1000.0;
            if (component.size() >= threshold && timeRemains > 0) {
                root = getRoot(subgraph, new Blocks(subgraph));
                if (root != null) {
                    addComponents(subgraph, root, components, signals);
                }
            }
            //if (root != null && !subgraph.containsVertex(root))
            //   continue;

            Set<Node> vertexSet = subgraph.vertexSet();
            Set<Unit> subset = new HashSet<>(vertexSet);
            subset.addAll(subgraph.edgeSet());
            Signals subSignals = new Signals(signals, subset);
            Node treeRoot = root;
            if (treeRoot == null) {
                treeRoot = vertexSet.stream().max(Comparator.comparing(signals::weight)).orElse(null);
            }
            TreeSolver.Solution mstSol = null;
            if (treeRoot != null) {
                MSTSolver ms = new MSTSolver(
                        subgraph,
                        Solver.makeHeuristicWeights(subgraph, subSignals),
                        treeRoot
                );
                ms.solve();
                Graph subtree = subgraph.subgraph(vertexSet, ms.getEdges());
                TreeSolver ts = new TreeSolver(subtree, subSignals);
                mstSol = ts.solveRooted(treeRoot);
                double tlb = subSignals.weightSum(mstSol.sets());
                double plb = lb.get();
                if (tlb >= plb) {
                    System.out.println("heuristic found lb " + tlb);
                    lb.compareAndSet(plb, tlb);
                    bestTree = extract(new ArrayList<>(mstSol.units));
                }
            }
            if (!this.cplexOff) {
                RLTSolver solver = new RLTSolver();
                solver.setSharedLB(lb);
                solver.setTimeLimit(tl);
                solver.setLogLevel(logLevel);
                if (mstSol != null)
                    solver.setInitialSolution(mstSol.units);
                Worker worker = new Worker(subgraph, root,
                        subSignals, solver, timeBefore);
                executor.execute(worker);
                memorized.add(worker);

            }
        }
        executor.shutdown();
        try {
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException ignored) {
        }
        if (!this.cplexOff)
            return getResult(memorized, graph, signals);
        else {
            graph.vertexSet().forEach(Unit::clear);
            graph.edgeSet().forEach(Unit::clear);
            return bestTree;
        }
    }

    private List<Unit> getResult(List<Worker> memorized, Graph graph, Signals signals) throws SolverException {
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
        if (logLevel == 2) {
            new GraphPrinter(graph, signals)
                    .toTSV("nodes-prep.tsv", "edges-prep.tsv",
                            best == null ? Collections.emptySet() : new HashSet<>(best));
        }
        List<Unit> result = extract(best);
        graph.vertexSet().forEach(Unit::clear);
        graph.edgeSet().forEach(Unit::clear);
        if (minimize && bestScore > 0) {
            return new Postprocessor(g, s, result, logLevel).minimize();
        } else return result;
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

    private void addComponents(Graph graph, Node root,
                               PriorityQueue<Set<Node>> components,
                               Signals signals) throws SolverException {
        // signals = new Signals(signals, graph.units());
        // Graph origin = graph;
        graph = graph.subgraph(graph.vertexSet());
        graph.removeVertex(root);
        /* List<Set<Node>> sets = graph.connectedSets().stream()
                .sorted(new SetComparator()).collect(Collectors.toList());

        List<Set<Integer>> sigs = new ArrayList<>();
        for (Set<Node> set : sets) {
            Graph g = graph.subgraph(set);
            Set<Integer> s = signals.positiveUnitSets(set);
            s.addAll(signals.positiveUnitSets(g.edgeSet()));
            sigs.add(s);
        }
        double[][] inter = new double[sigs.size()][sigs.size()];
        for (int i = 0; i < sigs.size(); i++) {
            for (int j = i; j < sigs.size(); j++) {
                Set<Integer> sect = new HashSet<>(sigs.get(i));
                sect.retainAll(sigs.get(j));
                inter[i][j] = signals.weightSum(sect);
            }
        }
        boolean contains = true;
        for (int i = 1; i < inter.length; i++) {
            if (inter[0][i] < inter[i][i]) {
                contains = false;
                break;
            }
        }

        if (contains) {
            for (int sig : sigs.get(0)) {
                if (sigs.subList(1, sigs.size()).stream().noneMatch(s -> s.contains(sig))) {
                    signals.setWeight(sig, 0);
                }
            }
            // sets.remove(0);
            AtomicDouble lb = new AtomicDouble(0);
            RLTSolver sol1 = new RLTSolver();
            sol1.setSharedLB(lb);
            sol1.setLogLevel(2);
            sol1.setTimeLimit(new TimeLimit(30));
            Graph sub = graph.subgraph(graph.connectedSets().get(0));
            Signals s1 = new Signals(signals, sub.units());
            Preprocessor prep1 = new Preprocessor(sub, s1);
            prep1.preprocess(2);
            List<Unit> res1 = sol1.solve(sub, s1);
            RLTSolver sol2 = new RLTSolver();
            sol2.setSharedLB(lb);
            sol2.setRoot(root);
            Graph sub2 = origin.subgraph(origin.vertexSet());
            Signals s2 = new Signals(signals, sub2.units());
            List<Unit> res2 = sol2.solve(sub2, s2);
            double sum1 = s1.sum(res1);
            double sum2 = s2.sum(res2);
            if (sum1 > sum2) {
                System.out.println("removing root " + root);
                origin.removeVertex(root);
                components.add(sets.get(0));
                return;
            }
        }*/
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
        this.preprocessLevel = preprocessLevel;
    }

    public void setCplexOff(boolean cplexOff) {
        this.cplexOff = cplexOff;
    }

    public static class SetComparator implements Comparator<Set<Node>> {
        @Override
        public int compare(Set<Node> o1, Set<Node> o2) {
            return -Integer.compare(o1.size(), o2.size());
        }
    }
}
