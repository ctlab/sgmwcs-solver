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
    public static final double STEP = 0.15;
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

    public Separator(Map<Node, IloNumVar> y, Map<Edge, IloNumVar> w, IloCplex cplex) {
        this.y = y;
        this.w = w;
        generators = new HashMap<>();
        generatorList = new ArrayList<>();
        nodes = new ArrayList<>();
        maxToAdd = 6000;
        minToConsider = 6000;
        this.cplex = cplex;
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

    @Override
    protected void main() throws IloException {
        System.err.print("Separating... ");
        if (!isCutsAllowed()) {
            System.err.println("rejected");
            return;
        }
        long before = System.currentTimeMillis();
        init();
        Collections.shuffle(nodes);
        List<Node> now = nodes.subList(0, Math.min(nodes.size(), minToConsider));
        int added = 0;
        for (Node node : now) {
            CutGenerator generator = generators.get(node);
            List<Node> cut = generator.findCut(node);
            if (cut != null) {
                Set<Node> minCut = new HashSet<>();
                minCut.addAll(cut);
                add(cplex.le(cplex.diff(y.get(node), cplex.sum(getVars(minCut, y))), 0));
                added++;
            }
            if (added == maxToAdd) {
                break;
            }
        }
        System.err.print("done in " + (System.currentTimeMillis() - before) + " msecs. ");
        System.err.println("Added " + added + " constraints");
    }

    private void init() throws IloException {
        for (CutGenerator generator : generatorList) {
            for (Node node : generator.getNodes()) {
                generator.setCapacity(node, getValue(y.get(node)) + ADDITION_CAPACITY);
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
