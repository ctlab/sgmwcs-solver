package ru.ifmo.ctddev.gmwcs.solver;

import org.jgrapht.UndirectedGraph;
import org.jgrapht.alg.ConnectivityInspector;
import ru.ifmo.ctddev.gmwcs.LDSU;
import ru.ifmo.ctddev.gmwcs.TimeLimit;
import ru.ifmo.ctddev.gmwcs.graph.Edge;
import ru.ifmo.ctddev.gmwcs.graph.Node;
import ru.ifmo.ctddev.gmwcs.graph.Unit;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ComponentSolver implements Solver {
    private final Solver solver;
    private TimeLimit tl;
    private double lb;
    private double fraction;
    private Node root;

    public ComponentSolver(Solver solver) {
        this.solver = solver;
        lb = 0.0;
        tl = new TimeLimit(Double.POSITIVE_INFINITY);
    }

    @Override
    public List<Unit> solve(UndirectedGraph<Node, Edge> graph, LDSU<Unit> synonyms) throws SolverException {
        if (graph.vertexSet().isEmpty()) {
            return null;
        }
        if (root != null) {
            return solveRooted(graph, synonyms);
        }
        ConnectivityInspector<Node, Edge> inspector = new ConnectivityInspector<>(graph);
        Set<Node> biggest = biggestComponent(inspector);
        TimeLimit mainTL = tl.subLimit(fraction);
        solver.setLB(lb);
        solver.setTimeLimit(mainTL);
        List<Unit> solBiggest = solver.solve(Utils.subgraph(graph, biggest), synonyms);
        solver.setLB(Utils.sum(solBiggest, synonyms));
        solver.setTimeLimit(tl);
        Set<Node> auxSubset = new LinkedHashSet<>();
        auxSubset.addAll(graph.vertexSet());
        auxSubset.removeAll(biggest);
        List<Unit> auxSolution = solver.solve(Utils.subgraph(graph, auxSubset), synonyms);
        return auxSolution != null ? auxSolution : solBiggest;
    }

    private Set<Node> biggestComponent(ConnectivityInspector<Node, Edge> inspector) {
        Set<Node> max = Collections.emptySet();
        for (Set<Node> component : inspector.connectedSets()) {
            if (component.size() > max.size()) {
                max = component;
            }
        }
        return max;
    }

    private List<Unit> solveRooted(UndirectedGraph<Node, Edge> graph, LDSU<Unit> synonyms) throws SolverException {
        ConnectivityInspector<Node, Edge> inspector = new ConnectivityInspector<>(graph);
        Set<Node> subset = inspector.connectedSetOf(root);
        if (subset == null) {
            return null;
        }
        solver.setLB(lb);
        solver.setTimeLimit(tl);
        solver.setRoot(root);
        return solver.solve(Utils.subgraph(graph, subset), synonyms);
    }

    @Override
    public void setTimeLimit(TimeLimit tl) {
        this.tl = tl;
    }

    @Override
    public void suppressOutput() {
        solver.suppressOutput();
    }

    @Override
    public void setLB(double lb) {
        if (lb < 0.0) {
            return;
        }
        this.lb = lb;
    }

    @Override
    public void setRoot(Node root) {
        this.root = root;
    }

    public void setMainFraction(double fraction) {
        this.fraction = fraction;
    }
}
