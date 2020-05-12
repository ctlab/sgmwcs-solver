package ru.itmo.ctlab.sgmwcs.solver;

/**
 * Created by Nikolay Poperechnyi on 13.02.20.
 */

import ru.itmo.ctlab.sgmwcs.Signals;
import ru.itmo.ctlab.sgmwcs.SignalsGraph;
import ru.itmo.ctlab.sgmwcs.TimeLimit;
import ru.itmo.ctlab.sgmwcs.graph.*;

import java.util.*;
import java.util.concurrent.*;

public class BicomponentSolver implements Solver {
    private final int threshold;
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

    public BicomponentSolver(int threshold, boolean isEdgePenalty) {
        this.threshold = threshold;
        this.isEdgePenalty = isEdgePenalty;
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
            new GraphPrinter(g, s, Collections.emptySet()).printGraph("beforePrep.dot", false);
        }
        long before = System.currentTimeMillis();
        new Preprocessor(g, s, threads, logLevel).preprocess(preprocessLevel);
        if (logLevel > 0) {
            new GraphPrinter(g, s, Collections.emptySet()).printGraph("afterPrep.dot", false);
            System.out.print("Preprocessing deleted " + (vertexBefore - g.vertexSet().size()) + " nodes ");
            System.out.println("and " + (edgesBefore - g.edgeSet().size()) + " edges.");
        }
        isSolvedToOptimality = true;
        if (g.vertexSet().size() == 0) {
            return null;
        }
        Signals ss = new Signals(s, units);
        for (int i = 0; i < 10; i++) {
            afterPreprocessing(g, ss);
        }
        ComponentSolver solver = new ComponentSolver(threshold, isEdgePenalty);
        solver.setThreadsNum(threads);
        solver.setTimeLimit(tl);
        solver.setLogLevel(logLevel);
        solver.setPreprocessingLevel(preprocessLevel);
        return solver.solve(g, ss);
    }

    private List<Unit> afterPreprocessing(Graph graph, Signals signals) throws SolverException {
        Graph origin = graph;
        List<Node> roots = new ArrayList<>();
        if (graph.edgeSet().size() <= 1) {
            return Collections.emptyList();
        }
        graph = graph.subgraph(graph.connectedSets().stream().min(new SetComparator()).get());
        BicomponentSeparator bs = new BicomponentSeparator(graph);
        Graph g = bs.getResult();
        // SignalsGraph sg = new SignalsGraph(origin, signals);
        Set<Edge> edges = bs.getCut();
        // new GraphPrinter(graph, signals, bs.edgesOfCuts()).printGraph("cuts", false);
        // BicomponentSeparator signalsBs = new BicomponentSeparator(sg.getGraph());
        // new GraphPrinter(sg.getGraph(), null, signalsBs.edgesOfCuts()).printGraph("sigcuts", false);
        Graph subgraph = g.subgraph(g.connectedSets().stream().max(new SetComparator()).get());
        for (Edge e : edges) {
            Node n = graph.getEdgeSource(e);
            if (subgraph.containsVertex(n)) {
                roots.add(n);
            }
            n = graph.getEdgeTarget(e);
            if (subgraph.containsVertex(n)) {
                roots.add(n);
            }
        }
        Set<Node> vertexSet = subgraph.vertexSet();
        Set<Unit> subset = new HashSet<>(vertexSet);
        subset.addAll(subgraph.edgeSet());
        for (int s: signals.unitSets(subset)) {
            System.out.println(signals.set(s).size());
        }

        Set<Unit> res1 = runSolver(subgraph, new Signals(signals, subset), roots.get(0));
        Set<Unit> res2 = runSolver(subgraph, new Signals(signals, subset), roots.get(1));
        Signals subSignals = new Signals(signals, subset);
        Edge aux = new Edge(0);
        subgraph.addEdge(roots.get(0), roots.get(1), aux);
        int sig0 = subSignals.addSignal(Double.POSITIVE_INFINITY);
        int sig1 = subSignals.addSignal(Double.POSITIVE_INFINITY);
        signals.add(roots.get(0), sig0);
        subSignals.addAndSetWeight(aux, -0.000000001);
        Set<Unit> res3 = runSolver(subgraph, subSignals, roots.get(1));
        List<Set<Unit>> colors = new ArrayList<>();
        colors.add(res1); colors.add(res2); colors.add(res3);
        if (signals.weightSum(signals.unitSets(res1)) == signals.weightSum(signals.unitSets(res2))
                && signals.weightSum(signals.unitSets(res2)) == signals.weightSum(signals.unitSets(res3)))
            contractSolutions(origin, signals, colors, subset);
        return null;
}

    private void contractSolutions(Graph g, Signals s,
                                   List<Set<Unit>> colors,
                                   Set<Unit> units) {
        Preprocessor p = new Preprocessor(g, s);
        Map<Set<Integer>, List<Unit>> colorUnits = new HashMap<>();
        Map<Unit, Set<Integer>> coloring = new HashMap<>();
        for (Unit unit: units) {
            Set<Integer> uc = new HashSet<>();
            int i = 0;
            for (Set<Unit> c: colors) {
                if (c.contains(unit)) {
                    uc.add(i);
                }
                i++;
            }
            colorUnits.putIfAbsent(uc, new ArrayList<>());
            colorUnits.get(uc).add(unit);
            coloring.put(unit, uc);
        }

        for (Map.Entry<Set<Integer>, List<Unit>> cu:
                colorUnits.entrySet()) {
            Set<Integer> color = cu.getKey();
            List<Unit> us = cu.getValue();
            if (color.isEmpty()) {
                for (Unit u: cu.getValue()) {
                    if (g.containsUnit(u)) {
                        g.removeUnit(u);
                    }
                }
            }
            else {
                for (Unit u: us) {
                    Set<Edge> toContract = new HashSet<>();
                    if (u instanceof Edge || !g.containsUnit(u))
                        continue;
                    Node n = (Node) u;
                    for (Edge e: g.edgesOf(n)) {
                        Node opp = g.getOppositeVertex(n, e);
                        if (coloring.getOrDefault(opp, Collections.singleton(-1)).equals(color)
                                && coloring.get(e).equals(color)) {
                            toContract.add(e);
                        }
                    }
                    toContract.forEach(p::contract);
                }
            }
        }
    }

    private Set<Unit> runSolver(Graph g, Signals s, Node r) throws SolverException {
        AtomicDouble lb = new AtomicDouble(externLB);
        RLTSolver solver = new RLTSolver();
        if (r != null) {
            int sig = s.addSignal(Double.POSITIVE_INFINITY);
            s.add(r, sig);
            solver.setRoot(r);
        }
        solver.setSharedLB(lb);
        solver.setTimeLimit(tl);
        solver.setLogLevel(logLevel);
        return new HashSet<>(solver.solve(g, s));
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

public static class SetComparator implements Comparator<Set<Node>> {
    @Override
    public int compare(Set<Node> o1, Set<Node> o2) {
        return -Integer.compare(o1.size(), o2.size());
    }
}
}
