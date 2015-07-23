package ru.ifmo.ctddev.gmwcs.solver;

import org.jgrapht.Graphs;
import org.jgrapht.UndirectedGraph;
import org.jgrapht.alg.ConnectivityInspector;
import org.jgrapht.graph.SimpleGraph;
import ru.ifmo.ctddev.gmwcs.LDSU;
import ru.ifmo.ctddev.gmwcs.TimeLimit;
import ru.ifmo.ctddev.gmwcs.graph.Edge;
import ru.ifmo.ctddev.gmwcs.graph.Node;
import ru.ifmo.ctddev.gmwcs.graph.Unit;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

public class ComponentSolver implements Solver {
    private final Solver solver;
    private TimeLimit tl;
    private double lb;
    private Node root;
    private Integer BFNum;

    public ComponentSolver(Solver solver) {
        this.solver = solver;
        lb = 0.0;
        tl = new TimeLimit(Double.POSITIVE_INFINITY);
    }

    @Override
    public List<Unit> solve(UndirectedGraph<Node, Edge> origin, LDSU<Unit> synonyms) throws SolverException {
        UndirectedGraph<Node, Edge> graph = new SimpleGraph<>(Edge.class);
        Graphs.addGraph(graph, origin);
        if (graph.vertexSet().isEmpty()) {
            return null;
        }
        if (root != null) {
            return solveRooted(graph, synonyms, lb, tl, root);
        }
        ConnectivityInspector<Node, Edge> inspector = new ConnectivityInspector<>(graph);
        PriorityQueue<Node> nodes = new PriorityQueue<>();
        nodes.addAll(graph.vertexSet());
        List<Unit> solution = new ArrayList<>();
        double lowerBound = lb;
        for (int i = 0; i < BFNum; i++) {
            if (nodes.isEmpty()) {
                break;
            }
            Node node = nodes.poll();
            if (node.getWeight() < 0.0) {
                break;
            }
            UndirectedGraph<Node, Edge> subgraph = Utils.subgraph(graph, inspector.connectedSetOf(node));
            List<Unit> result = solveRooted(subgraph, synonyms, lowerBound, tl, node);
            if (result != null) {
                solution = result;
            }
            graph.removeVertex(node);
            lowerBound = Math.max(lowerBound, Utils.sum(solution, synonyms));
        }
        List<Unit> result = solveRooted(graph, synonyms, lowerBound, tl, null);
        return result == null ? solution : result;
    }

    private List<Unit> solveRooted(UndirectedGraph<Node, Edge> graph, LDSU<Unit> synonyms,
                                   double lb, TimeLimit tl, Node root) throws SolverException {
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

    public void setBFNum(Integer BFNum) {
        this.BFNum = BFNum;
    }
}
