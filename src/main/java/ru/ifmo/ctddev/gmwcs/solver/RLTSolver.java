package ru.ifmo.ctddev.gmwcs.solver;

import ilog.concert.IloException;
import ilog.concert.IloLinearNumExpr;
import ilog.concert.IloNumExpr;
import ilog.concert.IloNumVar;
import ilog.cplex.IloCplex;
import org.jgrapht.Graphs;
import org.jgrapht.UndirectedGraph;
import ru.ifmo.ctddev.gmwcs.LDSU;
import ru.ifmo.ctddev.gmwcs.Pair;
import ru.ifmo.ctddev.gmwcs.TimeLimit;
import ru.ifmo.ctddev.gmwcs.graph.Blocks;
import ru.ifmo.ctddev.gmwcs.graph.Edge;
import ru.ifmo.ctddev.gmwcs.graph.Node;
import ru.ifmo.ctddev.gmwcs.graph.Unit;

import java.io.IOException;
import java.io.OutputStream;
import java.util.*;

public class RLTSolver implements RootedSolver {
    public static final double EPS = 0.01;
    private IloCplex cplex;
    private Map<Node, IloNumVar> y;
    private Map<Edge, IloNumVar> w;
    private Map<Node, IloNumVar> v;
    private Map<Edge, Pair<IloNumVar, IloNumVar>> t;
    private Map<Edge, Pair<IloNumVar, IloNumVar>> x;
    private Map<Node, IloNumVar> x0;
    private TimeLimit tl;
    private int threads;
    private boolean suppressOutput;
    private UndirectedGraph<Node, Edge> graph;
    private Node root;
    private SolutionCallback solutionCallback;
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

    static IloNumVar[] getVars(Set<? extends Unit> units, Map<? extends Unit, IloNumVar> vars) {
        IloNumVar[] result = new IloNumVar[units.size()];
        int i = 0;
        for (Unit unit : units) {
            result[i++] = vars.get(unit);
        }
        return result;
    }

    public void setMaxToAddCuts(int num) {
        maxToAddCuts = num;
    }

    public void setConsideringCuts(int num) {
        considerCuts = num;
    }

    public void setTimeLimit(TimeLimit tl) {
        this.tl = tl;
    }

    @Override
    public TimeLimit getTimeLimit() {
        return tl;
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
    public List<Unit> solve(UndirectedGraph<Node, Edge> graph, LDSU<Unit> synonyms) throws SolverException {
        try {
            if(!isLBShared){
                lb = new AtomicDouble(externLB);
            }
            cplex = new IloCplex();
            this.graph = graph;
            initVariables();
            addConstraints(graph);
            addObjective(graph, synonyms);
            if (root == null) {
                breakSymmetry(cplex, graph);
            } else {
                tighten();
            }
            breakTreeSymmetries();
            tuning(cplex);
            boolean solFound = cplex.solve();
            if (solFound) {
                return getResult();
            }
            return Collections.emptyList();
        } catch (IloException e) {
            throw new SolverException();
        } finally {
            cplex.end();
        }
    }

    private void tighten() throws IloException {
        Blocks blocks = new Blocks(graph);
        if (!blocks.cutpoints().contains(root)) {
            throw new IllegalArgumentException();
        }
        Separator separator = new Separator(y, w, cplex, graph, sum, lb);
        separator.setMaxToAdd(maxToAddCuts);
        separator.setMinToConsider(considerCuts);
        for (Set<Node> component : blocks.incidentBlocks(root)) {
            dfs(root, component, true, blocks, separator);
        }
        cplex.use(separator);
    }

    private void dfs(Node root, Set<Node> component, boolean fake, Blocks blocks, Separator separator) throws IloException {
        separator.addComponent(Utils.subgraph(graph, component), root);
        if (!fake) {
            for (Node node : component) {
                cplex.addLe(cplex.diff(y.get(node), y.get(root)), 0);
            }
        }
        for (Edge e : graph.edgesOf(root)) {
            if (!component.contains(Graphs.getOppositeVertex(graph, e, root))) {
                continue;
            }
            if (root == graph.getEdgeSource(e)) {
                cplex.addEq(x.get(e).first, 0);
            } else {
                cplex.addEq(x.get(e).second, 0);
            }
        }
        for (Node cp : blocks.cutpointsOf(component)) {
            if (root != cp) {
                for (Set<Node> comp : blocks.incidentBlocks(cp)) {
                    if (comp != component) {
                        dfs(cp, comp, false, blocks, separator);
                    }
                }
            }
        }
    }

    public boolean isSolvedToOptimality() {
        return isSolvedToOptimality;
    }

    private List<Unit> getResult() throws IloException {
        isSolvedToOptimality = false;
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
        if (cplex.getStatus() == IloCplex.Status.Optimal) {
            isSolvedToOptimality = true;
        }
        return result;
    }

    private void initVariables() throws IloException {
        y = new LinkedHashMap<>();
        w = new LinkedHashMap<>();
        v = new LinkedHashMap<>();
        t = new LinkedHashMap<>();
        x = new LinkedHashMap<>();
        x0 = new LinkedHashMap<>();
        for (Node node : graph.vertexSet()) {
            String nodeName = Integer.toString(node.getNum() + 1);
            v.put(node, cplex.numVar(0, Double.MAX_VALUE, "v" + nodeName));
            y.put(node, cplex.boolVar("y" + nodeName));
            x0.put(node, cplex.boolVar("x_0_" + (node.getNum() + 1)));
        }
        for (Edge edge : graph.edgeSet()) {
            Node from = graph.getEdgeSource(edge);
            Node to = graph.getEdgeTarget(edge);
            String edgeName = (from.getNum() + 1) + "_" + (to.getNum() + 1);
            w.put(edge, cplex.boolVar("w_" + edgeName));
            IloNumVar in = cplex.numVar(0, Double.MAX_VALUE, "t_" + (to.getNum() + 1) + "_" + (from.getNum() + 1));
            IloNumVar out = cplex.numVar(0, Double.MAX_VALUE, "t_" + (from.getNum() + 1) + "_" + (to.getNum() + 1));
            t.put(edge, new Pair<>(in, out));
            in = cplex.boolVar();
            out = cplex.boolVar();
            x.put(edge, new Pair<>(in, out));
        }
    }

    private void tuning(IloCplex cplex) throws IloException {
        if (suppressOutput) {
            OutputStream nos = new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                }
            };
            cplex.setOut(nos);
            cplex.setWarning(nos);
        }
        if (solutionCallback != null || isLBShared) {
            cplex.use(new MIPCallback());
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

    private void breakSymmetry(IloCplex cplex, UndirectedGraph<Node, Edge> graph) throws IloException {
        int n = graph.vertexSet().size();
        IloNumVar[] rootMul = new IloNumVar[n];
        IloNumVar[] nodeMul = new IloNumVar[n];
        PriorityQueue<Node> nodes = new PriorityQueue<>();
        nodes.addAll(graph.vertexSet());
        int k = nodes.size();
        int j = nodes.size();
        double last = Double.POSITIVE_INFINITY;
        while (!nodes.isEmpty()) {
            Node node = nodes.poll();
            if (node.getWeight() == last) {
                j++;
            }
            last = node.getWeight();
            nodeMul[k - 1] = cplex.intVar(0, n);
            rootMul[k - 1] = cplex.intVar(0, n);
            cplex.addEq(nodeMul[k - 1], cplex.prod(j, y.get(node)));
            cplex.addEq(rootMul[k - 1], cplex.prod(j, x0.get(node)));
            k--;
            j--;
        }
        IloNumVar rootSum = cplex.intVar(0, n);
        cplex.addEq(rootSum, cplex.sum(rootMul));
        for (int i = 0; i < n; i++) {
            cplex.addGe(rootSum, nodeMul[i]);
        }
    }

    private void breakTreeSymmetries() throws IloException {
        for(Edge edge : graph.edgeSet()){
            Node v = graph.getEdgeSource(edge);
            Node u = graph.getEdgeTarget(edge);
            int n = graph.vertexSet().size();
            IloNumExpr delta = cplex.diff(this.v.get(v), this.v.get(u));
            cplex.addLe(cplex.sum(cplex.prod(n, w.get(edge)), delta), n + 1);
            cplex.addLe(cplex.diff(cplex.prod(n, w.get(edge)), delta), n + 1);
        }
    }

    public void setCallback(SolutionCallback callback) {
        this.solutionCallback = callback;
    }

    private void addObjective(UndirectedGraph<Node, Edge> graph, LDSU<Unit> synonyms) throws IloException {
        Map<Unit, IloNumVar> summands = new LinkedHashMap<>();
        Set<Unit> toConsider = new LinkedHashSet<>();
        toConsider.addAll(graph.vertexSet());
        toConsider.addAll(graph.edgeSet());
        Set<Unit> visited = new LinkedHashSet<>();
        for (Unit unit : toConsider) {
            if (visited.contains(unit)) {
                continue;
            }
            visited.addAll(synonyms.listOf(unit));
            List<Unit> eq = synonyms.listOf(unit);
            if (eq.size() == 1) {
                summands.put(unit, getVar(unit));
                continue;
            }
            IloNumVar var = cplex.boolVar();
            summands.put(unit, var);
            int num = eq.size();
            for (Unit i : eq) {
                if (getVar(i) == null) {
                    num--;
                }
            }
            IloNumVar[] args = new IloNumVar[num];
            int j = 0;
            for (Unit anEq : eq) {
                if (getVar(anEq) == null) {
                    continue;
                }
                args[j++] = getVar(anEq);
            }
            if (unit.getWeight() > 0) {
                cplex.addLe(var, cplex.sum(args));
            } else {
                cplex.addGe(cplex.prod(eq.size() + 0.5, var), cplex.sum(args));
            }
        }
        IloNumExpr sum = unitScalProd(summands.keySet(), summands);
        this.sum = cplex.numVar(-Double.MAX_VALUE, Double.MAX_VALUE);
        cplex.addGe(sum, lb.get());
        cplex.addEq(this.sum, sum);
        cplex.addMaximize(sum);
    }

    private IloNumVar getVar(Unit unit) {
        return unit instanceof Node ? y.get(unit) : w.get(unit);
    }

    @Override
    public void suppressOutput() {
        suppressOutput = true;
    }

    private void addConstraints(UndirectedGraph<Node, Edge> graph) throws IloException {
        sumConstraints(graph);
        otherConstraints(graph);
        maxSizeConstraints(graph);
    }

    private void maxSizeConstraints(UndirectedGraph<Node, Edge> graph) throws IloException {
        for (Node v : graph.vertexSet()) {
            for (Node u : Graphs.neighborListOf(graph, v)) {
                if (u.getWeight() >= 0) {
                    Edge e = graph.getEdge(v, u);
                    if (e != null && e.getWeight() >= 0) {
                        cplex.addLe(y.get(v), w.get(e));
                    }
                }
            }
        }
    }

    private void otherConstraints(UndirectedGraph<Node, Edge> graph) throws IloException {
        int n = graph.vertexSet().size();
        // (34)
        for (Node node : graph.vertexSet()) {
            cplex.addLe(cplex.sum(cplex.prod(2, y.get(node)), cplex.negative(x0.get(node))), v.get(node));
            IloNumExpr sub = cplex.negative(cplex.prod(n - 1, x0.get(node)));
            cplex.addLe(v.get(node), cplex.sum(cplex.prod(n, y.get(node)), sub));
        }
        // (36), (39)
        for (Edge edge : graph.edgeSet()) {
            Pair<IloNumVar, IloNumVar> arcs = x.get(edge);
            Node from = graph.getEdgeSource(edge);
            Node to = graph.getEdgeTarget(edge);
            cplex.addLe(cplex.sum(arcs.first, arcs.second), w.get(edge));
            cplex.addLe(w.get(edge), y.get(from));
            cplex.addLe(w.get(edge), y.get(to));
        }
        // (35)
        List<Pair<IloNumVar, IloNumVar>> xt = new ArrayList<>();
        for (Edge edge : graph.edgeSet()) {
            xt.add(new Pair<>(x.get(edge).first, t.get(edge).first));
            xt.add(new Pair<>(x.get(edge).second, t.get(edge).second));
        }
        for (Pair<IloNumVar, IloNumVar> p : xt) {
            IloNumVar xi = p.first;
            IloNumVar ti = p.second;
            cplex.addLe(xi, ti);
            cplex.addLe(ti, cplex.prod(n - 1, xi));
        }
        // (37), (38)
        for (Node node : graph.vertexSet()) {
            for (Edge edge : graph.edgesOf(node)) {
                IloNumVar xij, xji, tij, tji;
                if (graph.getEdgeSource(edge) == node) {
                    xij = x.get(edge).first;
                    xji = x.get(edge).second;
                    tij = t.get(edge).first;
                    tji = t.get(edge).second;
                } else {
                    xij = x.get(edge).second;
                    xji = x.get(edge).first;
                    tij = t.get(edge).second;
                    tji = t.get(edge).first;
                }
                // (37)
                cplex.addGe(cplex.sum(xji, v.get(node), cplex.negative(y.get(node))), cplex.sum(tij, tji));
                // (38)
                IloNumExpr left = cplex.sum(v.get(node), cplex.negative(cplex.prod(n, y.get(node))));
                IloNumExpr right = cplex.sum(cplex.prod(n - 1, xij), cplex.prod(n, xji));
                cplex.addLe(cplex.sum(left, right), cplex.sum(tij, tji));
            }
        }
    }

    private void sumConstraints(UndirectedGraph<Node, Edge> graph) throws IloException {
        // (31)
        cplex.addEq(cplex.sum(getVars(graph.vertexSet(), x0)), 1);
        if (root != null) {
            cplex.addEq(x0.get(root), 1);
        }
        // (32) (33)
        for (Node node : graph.vertexSet()) {
            Set<Edge> edges = graph.edgesOf(node);
            IloNumVar xSum[] = new IloNumVar[edges.size() + 1];
            IloNumVar tSum[] = new IloNumVar[edges.size()];
            int i = 0;
            for (Edge edge : edges) {
                if (graph.getEdgeSource(edge) == node) {
                    xSum[i] = x.get(edge).first;
                    tSum[i++] = t.get(edge).first;
                } else {
                    xSum[i] = x.get(edge).second;
                    tSum[i++] = t.get(edge).second;
                }
            }
            xSum[xSum.length - 1] = x0.get(node);
            cplex.addEq(cplex.sum(xSum), y.get(node));
            cplex.addEq(cplex.sum(cplex.sum(xSum), cplex.sum(tSum)), v.get(node));
        }
    }

    private IloLinearNumExpr unitScalProd(Set<? extends Unit> units, Map<? extends Unit, IloNumVar> vars) throws IloException {
        int n = units.size();
        double[] coef = new double[n];
        IloNumVar[] variables = new IloNumVar[n];
        int i = 0;
        for (Unit unit : units) {
            coef[i] = unit.getWeight();
            variables[i++] = vars.get(unit);
        }
        return cplex.scalProd(coef, variables);
    }

    public void setLB(double lb) {
        this.externLB = lb;
    }

    public void setSharedLB(AtomicDouble lb){
        isLBShared = true;
        this.lb = lb;
    }

    public static abstract class SolutionCallback {
        public abstract void main(List<Unit> solution);
    }

    private class MIPCallback extends IloCplex.IncumbentCallback {

        @Override
        protected void main() throws IloException {
            if(isLBShared){
                while(true){
                    double currLB = lb.get();
                    if(currLB >= getObjValue() || lb.compareAndSet(currLB, getObjValue())){
                        break;
                    }
                }
            }
            if (solutionCallback == null) {
                return;
            }
            List<Unit> result = new ArrayList<>();
            for (Node node : graph.vertexSet()) {
                if (getValue(y.get(node)) > EPS) {
                    result.add(node);
                }
            }
            for (Edge edge : graph.edgeSet()) {
                if (getValue(w.get(edge)) > EPS) {
                    result.add(edge);
                }
            }
            solutionCallback.main(result);
        }
    }
}
