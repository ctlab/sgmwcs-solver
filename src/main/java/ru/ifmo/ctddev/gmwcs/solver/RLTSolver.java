package ru.ifmo.ctddev.gmwcs.solver;

import ilog.concert.IloException;
import ilog.concert.IloNumExpr;
import ilog.concert.IloNumVar;
import ilog.cplex.IloCplex;
import ru.ifmo.ctddev.gmwcs.Pair;
import ru.ifmo.ctddev.gmwcs.Signals;
import ru.ifmo.ctddev.gmwcs.TimeLimit;
import ru.ifmo.ctddev.gmwcs.graph.*;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.Arrays.fill;
import static ru.ifmo.ctddev.gmwcs.solver.TreeSolver.*;

public class RLTSolver implements RootedSolver {
    private static final double EPS = 0.01;
    private IloCplex cplex;
    private Map<Node, IloNumVar> y;
    private Map<Edge, IloNumVar> w;
    private Map<Edge, Pair<IloNumVar, IloNumVar>> x;
    private Map<Node, IloNumVar> d;
    private Map<Node, IloNumVar> x0;
    private TimeLimit tl;
    private int threads;
    private int logLevel;
    private Graph graph;
    private Signals signals;
    private Node root;
    private boolean isSolvedToOptimality;
    private int maxToAddCuts;
    private int considerCuts;
    private AtomicDouble lb;
    private double externLB;
    private boolean isLBShared;
    private IloNumVar sum;

    public RLTSolver() {
        tl = new TimeLimit(Double.POSITIVE_INFINITY);
        threads = 1;
        externLB = 0.0;
        maxToAddCuts = considerCuts = Integer.MAX_VALUE;
    }

    public void setMaxToAddCuts(int num) {
        maxToAddCuts = num;
    }

    public void setConsideringCuts(int num) {
        considerCuts = num;
    }

    @Override
    public TimeLimit getTimeLimit() {
        return tl;
    }

    public void setTimeLimit(TimeLimit tl) {
        this.tl = tl;
    }

    public void setThreadsNum(int threads) {
        if (threads < 1) {
            throw new IllegalArgumentException();
        }
        this.threads = threads;
    }

    public void setRoot(Node root) {
        this.root = root;
    }

    @Override
    public List<Unit> solve(Graph graph, Signals signals) throws SolverException {
        try {
            isSolvedToOptimality = false;
            if (!isLBShared) {
                lb = new AtomicDouble(externLB);
            }
            cplex = new IloCplex();
            this.graph = graph;
            this.signals = signals;
            initVariables();
            addConstraints();
            addObjective(signals);
            maxSizeConstraints(signals);
            if (root == null) {
                breakRootSymmetry();
            } else {
                tighten();
            }
            breakTreeSymmetries();
            tuning(cplex);
            List<IloNumVar> solVariables = new ArrayList<>();
            List<Double> solValues = new ArrayList<>();
            MSTHeuristic(solVariables, solValues);
            double[] sol = new double[solValues.size()];
            for (int i = 0; i < solValues.size(); ++i) {
                sol[i] = solValues.get(i);
            }
            cplex.addMIPStart(solVariables.toArray(new IloNumVar[0]), sol);
            boolean solFound = cplex.solve();
            if (cplex.getCplexStatus() != IloCplex.CplexStatus.AbortTimeLim) {
                isSolvedToOptimality = true;
            }
            if (solFound) {
                return getResult();
            }
            return Collections.emptyList();
        } catch (IloException e) {
            throw new SolverException(e.getMessage());
        } finally {
            cplex.end();
        }
    }

    private Set<Unit> usePrimalHeuristic(Node treeRoot) {
        Map<Edge, Double> edgeWeights = makeHeuristicWeights();
        MSTSolver mst = new MSTSolver(graph, edgeWeights, treeRoot);
        mst.solve();
        Graph tree = graph.subgraph(graph.vertexSet(), mst.getEdges());
        TreeSolver.Solution sol = new TreeSolver(tree, signals).solveRooted(treeRoot);
        System.err.println("MST heuristic solution: " + signals.weightSum(sol.sets()));
        return sol.units;
    }

    private void breakTreeSymmetries() throws IloException {
        int n = graph.vertexSet().size();
        for (Edge e : graph.edgeSet()) {
            Node from = graph.getEdgeSource(e);
            Node to = graph.getEdgeTarget(e);
            cplex.addLe(cplex.sum(d.get(from), cplex.prod(n - 1, w.get(e))), cplex.sum(n, d.get(to)));
            cplex.addLe(cplex.sum(d.get(to), cplex.prod(n - 1, w.get(e))), cplex.sum(n, d.get(from)));
        }
    }

    private void tighten() throws IloException {
        Blocks blocks = new Blocks(graph);
        if (!blocks.cutpoints().contains(root)) {
            return;
//            throw new IllegalArgumentException();
        }
        Separator separator = new Separator(y, w, cplex, graph, sum, lb);
        separator.setMaxToAdd(maxToAddCuts);
        separator.setMinToConsider(considerCuts);
        for (Set<Node> component : blocks.incidentBlocks(root)) {
            dfs(root, component, true, blocks, separator);
        }
        cplex.use(separator);
    }

    private void dfs(Node root, Set<Node> component, boolean fake, Blocks bs, Separator separator) throws IloException {
        separator.addComponent(graph.subgraph(component), root);
        if (!fake) {
            for (Node node : component) {
                cplex.addLe(cplex.diff(y.get(node), y.get(root)), 0);
            }
        }
        for (Edge e : graph.edgesOf(root)) {
            if (!component.contains(graph.getOppositeVertex(root, e))) {
                continue;
            }
            cplex.addEq(getX(e, root), 0);
        }
        for (Node cp : bs.cutpointsOf(component)) {
            if (root != cp) {
                for (Set<Node> comp : bs.incidentBlocks(cp)) {
                    if (comp != component) {
                        dfs(cp, comp, false, bs, separator);
                    }
                }
            }
        }
    }

    public boolean isSolvedToOptimality() {
        return isSolvedToOptimality;
    }

    private List<Unit> getResult() throws IloException {
        List<Unit> result = new ArrayList<>();
        for (Node node : graph.vertexSet()) {
            if (cplex.getValue(y.get(node)) > EPS) {
                result.add(node);
            }
        }
        for (Edge edge : graph.edgeSet()) {
            if (cplex.getValue(w.get(edge)) > EPS) {
                result.add(edge);
            }
        }
        return result;
    }

    private void initVariables() throws IloException {
        y = new LinkedHashMap<>();
        w = new LinkedHashMap<>();
        d = new LinkedHashMap<>();
        x = new LinkedHashMap<>();
        x0 = new LinkedHashMap<>();
        for (Node node : graph.vertexSet()) {
            String nodeName = Integer.toString(node.getNum() + 1);
            d.put(node, cplex.numVar(0, Double.MAX_VALUE, "d" + nodeName));
            y.put(node, cplex.boolVar("y" + nodeName));
            x0.put(node, cplex.boolVar("x_0_" + (node.getNum() + 1)));
        }
        for (Edge edge : graph.edgeSet()) {
            Node from = graph.getEdgeSource(edge);
            Node to = graph.getEdgeTarget(edge);
            String edgeName = (from.getNum() + 1) + "_" + (to.getNum() + 1);
            w.put(edge, cplex.boolVar("w_" + edgeName));
            IloNumVar in = cplex.boolVar("x_" + edgeName + "_in");
            IloNumVar out = cplex.boolVar("x_" + edgeName + "_out");
            x.put(edge, new Pair<>(in, out));
        }
    }

    private void tuning(IloCplex cplex) throws IloException {
        if (logLevel < 2) {
            cplex.setOut(null);
            cplex.setWarning(null);
        }
        if (isLBShared) {
            cplex.use(new MIPCallback(logLevel == 0));
        }
        cplex.setParam(IloCplex.IntParam.Threads, threads);
        cplex.setParam(IloCplex.IntParam.ParallelMode, -1);
        cplex.setParam(IloCplex.IntParam.MIPOrdType, 3);
        if (tl.getRemainingTime() <= 0) {
            cplex.setParam(IloCplex.DoubleParam.TiLim, EPS);
        } else if (tl.getRemainingTime() != Double.POSITIVE_INFINITY) {
            cplex.setParam(IloCplex.DoubleParam.TiLim, tl.getRemainingTime());
        }
    }

    private void breakRootSymmetry() throws IloException {
        int n = graph.vertexSet().size();
        PriorityQueue<Node> nodes = new PriorityQueue<>();
        nodes.addAll(graph.vertexSet());
        int k = n;
        IloNumExpr[] terms = new IloNumExpr[n];
        IloNumExpr[] rs = new IloNumExpr[n];
        while (!nodes.isEmpty()) {
            Node node = nodes.poll();
            terms[k - 1] = cplex.prod(k, x0.get(node));
            rs[k - 1] = cplex.prod(k, y.get(node));
            k--;
        }
        IloNumVar sum = cplex.numVar(0, n, "prSum");
        cplex.addEq(sum, cplex.sum(terms));
        for (int i = 0; i < n; i++) {
            cplex.addGe(sum, rs[i]);
        }
    }

    private void addObjective(Signals signals) throws IloException {
        List<Double> ks = new ArrayList<>();
        List<IloNumVar> vs = new ArrayList<>();
        for (int i = 0; i < signals.size(); i++) {
            double weight = signals.weight(i);
            List<Unit> set = signals.set(i);
            IloNumVar[] vars = set.stream()
                    .map(this::getVar).filter(Objects::nonNull)
                    .toArray(IloNumVar[]::new);
            if (vars.length == 0 || weight == 0.0) {
                continue;
            }
            ks.add(weight);
            if (vars.length == 1) {
                vs.add(vars[0]);
                continue;
            }
            IloNumVar x = cplex.boolVar("s" + i);
            vs.add(x);
            if (weight > 0) {
                cplex.addLe(x, cplex.sum(vars));
            } else {
                cplex.addGe(cplex.prod(vars.length, x), cplex.sum(vars));
            }
        }

        IloNumExpr sum = cplex.scalProd(ks.stream().mapToDouble(d -> d).toArray(),
                vs.toArray(new IloNumVar[vs.size()]));

        this.sum = cplex.numVar(-Double.MAX_VALUE, Double.MAX_VALUE);

        cplex.addGe(sum, lb.get());
        cplex.addEq(this.sum, sum);
        cplex.addMaximize(sum);
    }

    private IloNumVar getVar(Unit unit) {
        return unit instanceof Node ? y.get(unit) : w.get(unit);
    }

    @Override
    public void setLogLevel(int logLevel) {
        this.logLevel = logLevel;
    }

    private void addConstraints() throws IloException {
        sumConstraints();
        otherConstraints();
        distanceConstraints();
    }

    private void distanceConstraints() throws IloException {
        int n = graph.vertexSet().size();
        for (Node v : graph.vertexSet()) {
            cplex.addLe(d.get(v), cplex.diff(n, cplex.prod(n, x0.get(v))));
        }
        for (Edge e : graph.edgeSet()) {
            Node from = graph.getEdgeSource(e);
            Node to = graph.getEdgeTarget(e);
            addEdgeConstraints(e, from, to);
            addEdgeConstraints(e, to, from);
        }
    }

    private void addEdgeConstraints(Edge e, Node from, Node to) throws IloException {
        int n = graph.vertexSet().size();
        IloNumVar z = getX(e, to);
        cplex.addGe(cplex.sum(n, d.get(to)), cplex.sum(d.get(from), cplex.prod(n + 1, z)));
        cplex.addLe(cplex.sum(d.get(to), cplex.prod(n - 1, z)), cplex.sum(d.get(from), n));
    }

    private void maxSizeConstraints(Signals signals) throws IloException {
        for (Node v : graph.vertexSet()) {
            for (Node u : graph.neighborListOf(v)) {
                if (signals.minSum(u) >= 0) {
                    Edge e = graph.getEdge(v, u);
                    if (e != null && signals.minSum(e) >= 0) {
                        cplex.addLe(y.get(v), w.get(e));
                    }
                }
            }
        }
    }

    private void otherConstraints() throws IloException {
        // (36), (39)
        for (Edge edge : graph.edgeSet()) {
            Pair<IloNumVar, IloNumVar> arcs = x.get(edge);
            Node from = graph.getEdgeSource(edge);
            Node to = graph.getEdgeTarget(edge);
            cplex.addLe(cplex.sum(arcs.first, arcs.second), w.get(edge));
            cplex.addLe(w.get(edge), y.get(from));
            cplex.addLe(w.get(edge), y.get(to));
        }
    }

    private void sumConstraints() throws IloException {
        // (31)
        cplex.addLe(cplex.sum(graph.vertexSet().stream().map(x -> x0.get(x)).toArray(IloNumVar[]::new)), 1);
        if (root != null) {
            cplex.addEq(x0.get(root), 1);
        }
        // (32)
        for (Node node : graph.vertexSet()) {
            Set<Edge> edges = graph.edgesOf(node);
            IloNumVar xSum[] = new IloNumVar[edges.size() + 1];
            int i = 0;
            for (Edge edge : edges) {
                xSum[i++] = getX(edge, node);
            }
            xSum[xSum.length - 1] = x0.get(node);
            cplex.addEq(cplex.sum(xSum), y.get(node));
        }
    }

    private IloNumVar getX(Edge e, Node to) {
        if (graph.getEdgeSource(e) == to) {
            return x.get(e).first;
        } else {
            return x.get(e).second;
        }
    }

    public void setLB(double lb) {
        this.externLB = lb;
    }

    public void setSharedLB(AtomicDouble lb) {
        isLBShared = true;
        this.lb = lb;
    }

    private Map<Edge, Double> makeHeuristicWeights() {
        Map<Edge, Double> weights = new HashMap<>();
        for (Edge e : graph.edgeSet()) {
            Node u = graph.getEdgeSource(e), v = graph.getEdgeTarget(e);
            double edgeMin = signals.minSum(e), edgeMax = signals.maxSum(e);
            Set<Integer> nns = signals.negativeUnitSets(u, v);
            Set<Integer> ens = signals.negativeUnitSets(e);
            Set<Integer> nps = signals.positiveUnitSets(u, v);
            Set<Integer> eps = signals.positiveUnitSets(e);
            double nodesMin = signals.minSum(e, u, v), nodesMax = signals.maxSum(e, u, v);
            if (edgeMin == 0 || nns.containsAll(ens)) {
                weights.put(e, 0.0); // Edge is non-negative so it has the highest priority
            } else if (edgeMax > 0 && !nps.containsAll(eps)) {
                eps.removeAll(nps); //Edge contains both negative and positive signals
                weights.put(e, 1.0 / signals.weightSum(eps));
            } else {
                ens.removeAll(nns); //Edge contains only negative signals
                weights.put(e, -signals.weightSum(ens));
            }
        }
        return weights;
    }

    private void tryMstSolution(Set<Unit> mstSol,
                                Node root,
                                List<IloNumVar> solVars,
                                List<Double> solValues) {
        List<IloNumVar> vars = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        final Double[] w = new Double[this.w.size()];
        final Double[] y = new Double[this.y.size()];
        final Double[] d = new Double[this.d.size()];
        final Double[] x0 = new Double[this.x0.size()];
        final IloNumVar[] w_n = new IloNumVar[this.w.size()];
        final IloNumVar[] y_n = new IloNumVar[this.y.size()];
        final IloNumVar[] d_n = new IloNumVar[this.d.size()];
        final IloNumVar[] x0_n = new IloNumVar[this.x0.size()];
        Node cur;
        final Set<Edge> zeroEdges = new HashSet<>(this.graph.edgeSet());
        final Set<Node> zeroNodes = new HashSet<>(this.graph.vertexSet());
        final Deque<Node> deque = new ArrayDeque<>();
        deque.add(root);
        int n = 0;
        List<IloNumVar> arcs = new ArrayList<>();
        List<Double> arcs_w = new ArrayList<>();
        d[0] = 0.0;
        d_n[0] = this.d.get(deque.getFirst());
        fill(x0, 0.0);
        x0[0] = 1.0;
        int dist = 0;
        Set<Node> visited = new HashSet<>();
        while (!deque.isEmpty()) {
            cur = deque.pollFirst();
            x0_n[n] = this.x0.get(cur);
            y_n[n] = this.y.get(cur);
            y[n] = 1.0;
            n++;
            visited.add(cur);
            mstSol.remove(cur);
            int l = deque.size();
            final Node c = cur;
            List<Node> neighbors = graph.neighborListOf(cur)
                    .stream().filter(node -> mstSol.contains(node) ||
                            isGoodNode(node, graph.getEdge(c, node), visited)
                    ).collect(Collectors.toList());
            if (!neighbors.isEmpty()) {
                dist++;
            }
            for (Node node : neighbors) {
                d_n[n + l] = this.d.get(node);
                d[n + l] = (double) dist;
                l++;
                Edge e = graph.getEdge(node, cur);
                arcs.add(getX(e, node));
                arcs.add(getX(e, cur));
                arcs_w.add(1.0);
                arcs_w.add(0.0);
                zeroEdges.remove(e);
                vars.add(RLTSolver.this.w.get(e));
                weights.add(1.0);
                deque.add(node);
            }
        }
        zeroNodes.removeAll(visited);
        for (Edge e : zeroEdges) {
            vars.add(RLTSolver.this.w.get(e));
            weights.add(0.0);
            Pair<IloNumVar, IloNumVar> p = this.x.get(e);
            arcs.add(p.first);
            arcs.add(p.second);
            arcs_w.add(0.0);
            arcs_w.add(0.0);
        }
        final Double[] x = new Double[arcs.size()];
        final IloNumVar[] x_n = new IloNumVar[arcs.size()];
        for (Node node : zeroNodes) {
            x0_n[n] = RLTSolver.this.x0.get(node);
            d_n[n] = RLTSolver.this.d.get(node);
            d[n] = 0.0;
            y[n] = 0.0;
            y_n[n] = RLTSolver.this.y.get(node);
            n++;
        }
        for (int i = 0; i < arcs.size(); ++i) {
            x_n[i] = arcs.get(i);
            x[i] = arcs_w.get(i);
        }
        for (int i = 0; i < weights.size(); ++i) {
            w[i] = weights.get(i);
            w_n[i] = vars.get(i);
        }
        solVars.addAll(asList(w_n));
        solVars.addAll(asList(y_n));
        solVars.addAll(asList(d_n));
        solVars.addAll(asList(x_n));
        solVars.addAll(asList(x0_n));
        solValues.addAll(asList(w));
        solValues.addAll(asList(y));
        solValues.addAll(asList(d));
        solValues.addAll(asList(x));
        solValues.addAll(asList(x0));

    }

    private boolean isGoodNode(Node node, Edge edge, Set<Node> visited) {
        return signals.minSum(node, edge) == 0 && !visited.contains(node);
    }

    private void MSTHeuristic(List<IloNumVar> solVariables, List<Double> solValues) {
        Node treeRoot = Optional.ofNullable(root)
                .orElse(graph.vertexSet().iterator().next());
        Set<Unit> units = usePrimalHeuristic(treeRoot);
        tryMstSolution(units, treeRoot, solVariables, solValues);
    }

    private class MSTCallback extends IloCplex.HeuristicCallback {
        List<IloNumVar> solVariables = new ArrayList<>();
        List<Double> solValues = new ArrayList<>();

        @Override
        protected void main() throws IloException {
            Node treeRoot = Optional.ofNullable(root)
                    .orElse(graph.vertexSet().iterator().next());
            MSTHeuristic(solVariables, solValues);
            double[] sol = new double[solValues.size()];
            for (int i = 0; i < solValues.size(); ++i) {
                sol[i] = solValues.get(i);
            }
            setSolution(solVariables.toArray(new IloNumVar[0]), sol);
        }
    }


    private class MIPCallback extends IloCplex.IncumbentCallback {
        private boolean silence;

        public MIPCallback(boolean silence) {
            this.silence = silence;
        }

        @Override
        protected void main() throws IloException {

            while (true) {
                double currLB = lb.get();
                if (currLB >= getObjValue()) {
                    break;
                }
                if (lb.compareAndSet(currLB, getObjValue()) && !silence) {
                    System.out.println("Found new solution: " + getObjValue());
                }
            }
        }
    }
}
