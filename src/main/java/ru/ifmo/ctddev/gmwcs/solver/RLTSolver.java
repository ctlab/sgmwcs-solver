package ru.ifmo.ctddev.gmwcs.solver;

import ilog.concert.*;
import ilog.cplex.IloCplex;
import ru.ifmo.ctddev.gmwcs.Pair;
import ru.ifmo.ctddev.gmwcs.Signals;
import ru.ifmo.ctddev.gmwcs.TimeLimit;
import ru.ifmo.ctddev.gmwcs.graph.*;

import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static ilog.cplex.IloCplex.*;

public class RLTSolver implements RootedSolver {
    private static final double EPS = 0.01;
    private IloCplex cplex;
    private Map<Node, IloNumVar> y;
    private Map<Edge, IloNumVar> w;
    private Map<Edge, Pair<IloNumVar, IloNumVar>> x;
    private Map<Node, IloNumVar> d;
    private Map<Node, IloNumVar> x0;
    private Map<Integer, IloNumVar> s;
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
            PSD psd = new PSD(graph, signals);
            psd.decompose();
            if (psd.ub() < lb.get()) {
                return Collections.emptyList();
            }
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
            if (graph.edgeSet().size() >= 1) {
                cplex.use(new MSTCallback());
                CplexSolution sol = MSTHeuristic(makeHeuristicWeights());
                if (sol != null) {
                    boolean applied = sol.apply((vars, vals) -> {
                                try {
                                    cplex.addMIPStart(vars, vals, MIPStartEffort.Repair);
                                    return true;
                                } catch (IloException e) {
                                    return false;
                                }
                            }
                    );
                    if (!applied) {
                        throw new SolverException("MST Heuristic not applied");
                    }
                }
            }
         /*   List<IloConstraint> constraints = new ArrayList<>();
            for (Iterator it = cplex.rangeIterator(); it.hasNext(); ) {
                Object c = it.next();
                constraints.add((IloRange) c);
            }
            double[] zeros = new double[constraints.size()];
            Arrays.fill(zeros, 0);
            if (cplex.refineMIPStartConflict(0, constraints.toArray(new IloConstraint[0]), zeros)) {
                System.out.println("Conflict refined");
                cplex.writeMIPStarts("../starts.mst");
            } else System.out.println("Conflict not refined");
            cplex.exportModel("../model.lp"); */
            boolean solFound = cplex.solve();
            if (cplex.getCplexStatus() != CplexStatus.AbortTimeLim) {
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

    private Set<Unit> usePrimalHeuristic(Node treeRoot,
                                         Map<Edge, Double> edgeWeights) {
        MSTSolver mst = new MSTSolver(graph, edgeWeights, treeRoot);
        mst.solve();
        Graph tree = graph.subgraph(graph.vertexSet(), mst.getEdges());
        TreeSolver.Solution sol = new TreeSolver(tree, signals).solveRooted(treeRoot);
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
        s = new HashMap<>();
        for (Node node : graph.vertexSet()) {
            String nodeName = Integer.toString(node.getNum() + 1);
            d.put(node, cplex.numVar(0, Double.MAX_VALUE, "d" + nodeName));
            y.put(node, cplex.boolVar("y" + nodeName));
            x0.put(node, cplex.boolVar("x_0_" + (node.getNum() + 1)));
        }
        Set<String> usedEdges = new HashSet<>();
        for (Edge edge : graph.edgeSet()) {
            Node from = graph.getEdgeSource(edge);
            Node to = graph.getEdgeTarget(edge);
            int num = 0;
            String edgeName;
            do {
                edgeName = (from.getNum() + 1) + "_" + (to.getNum() + 1) + "_" + num;
                num++;
            } while (usedEdges.contains(edgeName));
            usedEdges.add(edgeName);
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
        cplex.setParam(IntParam.Threads, threads);
        cplex.setParam(IntParam.ParallelMode, -1);
        cplex.setParam(IntParam.MIPOrdType, 3);
        if (tl.getRemainingTime() <= 0) {
            cplex.setParam(DoubleParam.TiLim, EPS);
        } else if (tl.getRemainingTime() != Double.POSITIVE_INFINITY) {
            cplex.setParam(DoubleParam.TiLim, tl.getRemainingTime());
        }
    }

    private void breakRootSymmetry() throws IloException {
        int n = graph.vertexSet().size();
        PriorityQueue<Node> nodes = new PriorityQueue<>(graph.vertexSet());
        int k = n;
        IloNumExpr[] terms = new IloNumExpr[n];
        IloNumExpr[] rs = new IloNumExpr[n];

        while (!nodes.isEmpty()) {
            Node node = nodes.poll();
            terms[k - 1] = cplex.prod(k, x0.get(node));
            rs[k - 1] = cplex.prod(k, y.get(node));
            k--;
        }
        sum = cplex.numVar(0, n, "prSum");
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
            s.put(i, x);
            vs.add(x);
            if (weight > 0) {
                cplex.addLe(x, cplex.sum(vars));
            } else {
                cplex.addGe(cplex.prod(vars.length, x), cplex.sum(vars));
            }
        }

        IloNumExpr sum = cplex.scalProd(ks.stream().mapToDouble(d -> d).toArray(),
                vs.toArray(new IloNumVar[0]));

        this.sum = cplex.numVar(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, "prSum");
        cplex.addGe(sum, lb.get());
        cplex.addEq(this.sum, sum);
        cplex.addMaximize(this.sum);
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
        cplex.addGe(cplex.sum(n, d.get(to)), cplex.sum(d.get(from), cplex.prod(n + 1, z)),
                "edge_constraints" + e.getNum() + "_1");
        cplex.addLe(cplex.sum(d.get(to), cplex.prod(n - 1, z)), cplex.sum(d.get(from), n),
                "edge_constraints" + e.getNum() + "_2");
    }

    private void maxSizeConstraints(Signals signals) throws IloException {
        for (Node v : graph.vertexSet()) {
            for (Node u : graph.neighborListOf(v)) {
                if (signals.minSum(u) >= 0) {
                    Edge e = graph.getAllEdges(v, u)
                            .stream().max(Comparator.naturalOrder()).get();
                    if (signals.minSum(e) >= 0) {
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

    public double getLB() {
        return lb.get();
    }

    private Map<Edge, Double> makeHeuristicWeights() {
        Map<Edge, Double> weights = new HashMap<>();
        for (Edge e : graph.edgeSet()) {
            Node u = graph.getEdgeSource(e), v = graph.getEdgeTarget(e);
            double weightSum = signals.sum(e, u, v);
            if (weightSum > 0) {
                weights.put(e,  1.0 / weightSum); // Edge is non-negative so it has the highest priority
            } else weights.put(e, 2.0);
        }
        return weights;
    }

    private CplexSolution tryMstSolution(Graph tree, Node root,
                                         Set<Unit> mstSol) {
        CplexSolution solution = new CplexSolution();
        final Set<Edge> unvisitedEdges = new HashSet<>(this.graph.edgeSet());
        final Set<Node> unvisitedNodes = new HashSet<>(this.graph.vertexSet());
        final Deque<Node> deque = new ArrayDeque<>();
        deque.add(root);
        Map<Node, Integer> ds = new HashMap<>();
        ds.put(root, 0);
        Set<Node> visitedNodes = new HashSet<>();
        Set<Edge> visitedEdges = new HashSet<>();
        visitedNodes.add(root);
        mstSol.remove(root);
        while (!deque.isEmpty()) {
            final Node cur = deque.pollFirst();
            solution.addVariable(x0, cur, cur == root ? 1 : 0);
            solution.addVariable(y, cur, 1);
            List<Node> neighbors = tree.neighborListOf(cur)
                    .stream().filter(node -> mstSol.contains(node) ||
                            isGoodNode(node, tree.getEdge(cur, node), visitedNodes)
                    ).collect(Collectors.toList());
            visitedNodes.addAll(neighbors);
            mstSol.removeAll(neighbors);
            for (Node node : neighbors) {
                Edge e = tree.getAllEdges(node, cur)
                        .stream().filter(mstSol::contains).findFirst().get();
                unvisitedEdges.remove(e);
                visitedEdges.add(e);
                solution.addVariable(w, e, 1.0);
                deque.add(node);
            }
        }
        unvisitedNodes.removeAll(visitedNodes);
        for (Edge e : new ArrayList<>(unvisitedEdges)) {
            Node u = graph.getEdgeSource(e), v = graph.getEdgeTarget(e);
            IloNumVar from = getX(e, u), to = getX(e, v);
            if (visitedNodes.contains(u) && visitedNodes.contains(v) && signals.minSum(e) >= 0) {
                unvisitedEdges.remove(e);
                visitedEdges.add(e);
            } else {
                solution.addNullVariables(w.get(e), from, to);
            }
        }
        deque.add(root);
        Set<Unit> solutionUnits = new HashSet<>(visitedNodes);
        solutionUnits.addAll(visitedEdges);
        visitedNodes.remove(root);
        while (!deque.isEmpty()) {
            final Node cur = deque.poll();
            List<Node> neighbors = new ArrayList<>();
            for (Edge e : graph.edgesOf(cur).stream().filter(visitedEdges::contains)
                    .collect(Collectors.toList())) {
                neighbors.add(graph.getOppositeVertex(cur, e));
            }
            for (Node node : neighbors) {
                if (!visitedNodes.contains(node))
                    continue;
                deque.add(node);
                visitedNodes.remove(node);
                Edge e = graph.getAllEdges(node, cur).stream()
                        .filter(visitedEdges::contains).findFirst().get();
                solution.addVariable(getX(e, node), 1);
                solution.addVariable(getX(e, cur), 0);
                visitedEdges.remove(e);
                ds.put(node, ds.get(cur) + 1);
            }
        }
        for (Edge e : visitedEdges) {
            Node u = graph.getEdgeSource(e), v = graph.getEdgeTarget(e);
            IloNumVar from = getX(e, u), to = getX(e, v);
            solution.addVariable(w, e, 1);
            solution.addNullVariables(from, to);
        }
        assert visitedNodes.isEmpty();
        for (Map.Entry<Node, Integer> nd : ds.entrySet()) {
            solution.addVariable(d, nd.getKey(), nd.getValue());
        }
        for (Node node : unvisitedNodes) {
            solution.addNullVariables(x0.get(node), d.get(node), y.get(node));
        }
        for (int sig = 0; sig < signals.size(); sig++) {
            List<Unit> units = signals.set(sig);
            if (units.size() > 1 && signals.weight(sig) != 0) {
                boolean val = units.stream().anyMatch(solutionUnits::contains);
                solution.addVariable(s.get(sig), val ? 1 : 0);
            }
        }
        solution.addVariable(this.sum, signals.sum(solutionUnits));
        return solution;
    }

    private boolean isGoodNode(Node node, Edge edge, Set<Node> visited) {
        return signals.minSum(node, edge) == 0 && !visited.contains(node);
    }

    private CplexSolution MSTHeuristic(Map<Edge, Double> weights) {
        Node treeRoot = Optional.ofNullable(root)
                .orElse(graph.vertexSet().stream().max(Comparator.naturalOrder()).get());
        Set<Unit> units = usePrimalHeuristic(treeRoot, weights);
        if (units.isEmpty()) {
            return null; //TODO!!
        }
        Set<Edge> edges = units.stream().filter(e -> e instanceof Edge)
                .map(e -> (Edge) e).collect(Collectors.toSet());
        Set<Node> nodes = units.stream().filter(e -> e instanceof Node)
                .map(e -> (Node) e).collect(Collectors.toSet());
        return tryMstSolution(graph.subgraph(nodes, edges), treeRoot, units);
    }

    private class MSTCallback extends HeuristicCallback {
        int i = 0;

        @Override
        protected void main() throws IloException {
            i++;
            if ((i - 1) % 1000 != 0 || i > 10000) return;
            Map<Edge, Double> weights = new HashMap<>();
            for (Edge e : graph.edgeSet()) {
                Node u = graph.getEdgeSource(e), v = graph.getEdgeTarget(e);
                double wu = this.getValue(y.get(u)), wv = this.getValue(y.get(v)),
                        we = this.getValue(w.get(e));
                weights.put(e, 3 - wu - we - wv);
            }
            CplexSolution sol = MSTHeuristic(weights);
            assert sol != null && sol.values.size() == sol.variables.size();
            double obj = sol.values.get(sol.values.size() - 1);
            if (obj >= getIncumbentObjValue()) {
//                System.err.println("MST heuristic found solution with objective " + obj);
                setSolution(sol.variables(), sol.values());
            }
        }
    }


    public class CplexSolution {
        private List<IloNumVar> variables = new LinkedList<>();
        private List<Double> values = new LinkedList<>();

        IloNumVar[] variables() {
            return variables.toArray(new IloNumVar[0]);
        }

        double[] values() {
            return values.stream().mapToDouble(d -> d).toArray();
        }

        <U extends Unit> void addVariable(Map<U, IloNumVar> map,
                                          U unit, double val) {
            addVariable(map.get(unit), val);
        }

        void addVariable(IloNumVar var, double val) {
            variables.add(var);
            values.add(val);
        }

        void addNullVariables(IloNumVar... vars) {
            for (IloNumVar var : vars) {
                addVariable(var, 0);
            }
        }

        boolean apply(BiFunction<IloNumVar[], double[], Boolean> set) {
            double[] vals = new double[values.size()];
            for (int i = 0; i < values.size(); ++i) {
                vals[i] = values.get(i);
            }
            return set.apply(variables.toArray(new IloNumVar[0]), vals);
        }
    }


    private class MIPCallback extends IncumbentCallback {
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
