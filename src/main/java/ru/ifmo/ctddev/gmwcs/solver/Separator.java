package ru.ifmo.ctddev.gmwcs.solver;

import ilog.concert.IloException;
import ilog.concert.IloNumVar;
import ilog.cplex.IloCplex;
import org.jgrapht.UndirectedGraph;
import ru.ifmo.ctddev.gmwcs.graph.Edge;
import ru.ifmo.ctddev.gmwcs.graph.Node;

import java.util.*;

import static ru.ifmo.ctddev.gmwcs.solver.RLTSolver.getVars;

public class Separator extends IloCplex.UserCutCallback {
    public static final double ADDITION_CAPACITY = 1e-6;
    public static final double STEP = 0.1;
    public static final double EPS = 1e-5;
    private Map<Node, CutGenerator> generators;
    private int maxToAdd;
    private int minToConsider;
    private List<Node> nodes;
    private List<CutGenerator> generatorList;
    private Map<Node, IloNumVar> y;
    private Map<Edge, IloNumVar> w;
    private IloCplex cplex;
    private int waited;
    private double period;
    private UndirectedGraph<Node, Edge> graph;

    public Separator(Map<Node, IloNumVar> y, Map<Edge, IloNumVar> w, IloCplex cplex, UndirectedGraph<Node, Edge> graph) {
        this.y = y;
        this.w = w;
        generators = new HashMap<>();
        generatorList = new ArrayList<>();
        nodes = new ArrayList<>();
        maxToAdd = Integer.MAX_VALUE;
        minToConsider = Integer.MAX_VALUE;
        this.cplex = cplex;
        this.graph = graph;
    }

    public void setMaxToAdd(int n) {
        maxToAdd = n;
    }

    public void setMinToConsider(int n) {
        minToConsider = n;
    }

    private synchronized boolean isCutsAllowed() {
        waited++;
        if (waited > period) {
            waited = 0;
            period += STEP;
            return true;
        }
        return false;
    }

    public Separator clone() {
        Separator result = new Separator(y, w, cplex, graph);
        for (CutGenerator generator : generatorList) {
            result.addComponent(Utils.subgraph(graph, generator.getNodes()), generator.getRoot());
        }
        return result;
    }

    @Override
    protected void main() throws IloException {
        if (!isCutsAllowed()) {
            return;
        }
        init();
        Collections.shuffle(nodes);
        List<Node> now = nodes.subList(0, Math.min(nodes.size(), minToConsider));
        int added = 0;
        for (Node node : now) {
            CutGenerator generator = generators.get(node);
            List<Edge> cut = generator.findCut(node);
            if (cut != null) {
                Set<Edge> minCut = new HashSet<>();
                minCut.addAll(cut);
                add(cplex.le(cplex.diff(y.get(node), cplex.sum(getVars(minCut, w))), 0));
                added++;
            }
            if (added == maxToAdd) {
                break;
            }
        }
    }

    private void init() throws IloException {
        for (CutGenerator generator : generatorList) {
            for (Edge edge : generator.getEdges()) {
                generator.setCapacity(edge, getValue(w.get(edge)) + ADDITION_CAPACITY);
            }
            for (Node node : generator.getNodes()) {
                generator.setVertexCapacity(node, getValue(y.get(node)) - EPS);
            }
        }
    }

    public void addComponent(UndirectedGraph<Node, Edge> graph, Node root) {
        CutGenerator generator = new CutGenerator(graph, root);
        generatorList.add(generator);
        for (Node node : generator.getNodes()) {
            if (node != generator.getRoot()) {
                generators.put(node, generator);
                nodes.add(node);
            }
        }
    }
}
