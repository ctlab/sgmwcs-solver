package ru.ifmo.ctddev.gmwcs;

import ilog.concert.IloException;
import ilog.concert.IloLinearNumExpr;
import ilog.concert.IloNumExpr;
import ilog.concert.IloNumVar;
import ilog.cplex.IloCplex;
import org.jgrapht.UndirectedGraph;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.*;

public class RLTSolver extends Solver {
    public static final double EPS = 0.1;
    public static final int SILENCE_SIZE = 30;
    private IloCplex cplex;
    private Map<Node, IloNumVar> y;
    private Map<Edge, IloNumVar> w;
    private Map<Node, IloNumVar> v;
    private Map<Edge, Pair<IloNumVar, IloNumVar>> t;
    private Map<Edge, Pair<IloNumVar, IloNumVar>> x;
    private Map<Node, IloNumVar> x0;
    private Node root;

    @Override
    protected List<Unit> solveBiComponent(UndirectedGraph<Node, Edge> graph, Node root, double tl) throws IloException {
        cplex = new IloCplex();
        if (graph.vertexSet().size() <= SILENCE_SIZE) {
            cplex.setOut(new PrintStream(new OutputStream() {
                @Override
                public void write(int b) throws IOException {

                }
            }));
        }
        IloCplex.ParameterSet parameters = new IloCplex.ParameterSet();
        parameters.setParam(IloCplex.IntParam.Threads, threads);
        if (tl > 0) {
            parameters.setParam(IloCplex.DoubleParam.TiLim, tl);
        }
        cplex.tuneParam(parameters);
        y = new LinkedHashMap<>();
        w = new LinkedHashMap<>();
        v = new LinkedHashMap<>();
        t = new LinkedHashMap<>();
        x = new LinkedHashMap<>();
        x0 = new LinkedHashMap<>();
        this.root = root;
        for (Node node : graph.vertexSet()) {
            String nodeName = Integer.toString(node.getNum() + 1);
            v.put(node, cplex.intVar(0, Integer.MAX_VALUE, "v" + nodeName));
            y.put(node, cplex.boolVar("y" + nodeName));
            x0.put(node, cplex.boolVar("x_0_" + (node.getNum() + 1)));
        }
        for (Edge edge : graph.edgeSet()) {
            Node from = graph.getEdgeSource(edge);
            Node to = graph.getEdgeTarget(edge);
            String edgeName = (from.getNum() + 1) + "_" + (to.getNum() + 1);
            w.put(edge, cplex.boolVar("w_" + edgeName));
            IloNumVar in = cplex.intVar(0, Integer.MAX_VALUE, "t_" + (to.getNum() + 1) + "_" + (from.getNum() + 1));
            IloNumVar out = cplex.intVar(0, Integer.MAX_VALUE, "t_" + (from.getNum() + 1) + "_" + (to.getNum() + 1));
            t.put(edge, new Pair<>(in, out));
            in = cplex.intVar(0, Integer.MAX_VALUE, "x_" + (to.getNum() + 1) + "_" + (from.getNum() + 1));
            out = cplex.intVar(0, Integer.MAX_VALUE, "x_" + (from.getNum() + 1) + "_" + (to.getNum() + 1));
            x.put(edge, new Pair<>(in, out));
        }
        addConstraints(graph);
        IloNumExpr nodeObj = unitScalProd(graph.vertexSet(), y);
        IloNumExpr edgeObj = unitScalProd(graph.edgeSet(), w);
        cplex.addMaximize(cplex.sum(nodeObj, edgeObj));
        if (cplex.solve()) {
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
            cplex.end();
            return result;
        }
        return null;
    }

    private void addConstraints(UndirectedGraph<Node, Edge> graph) throws IloException {
        sumConstraints(graph);
        otherConstraints(graph);
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
        cplex.addEq(cplex.sum(x0.values().toArray(new IloNumVar[]{})), 1);
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
}
