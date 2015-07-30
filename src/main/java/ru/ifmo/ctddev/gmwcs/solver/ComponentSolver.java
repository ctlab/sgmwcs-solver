package ru.ifmo.ctddev.gmwcs.solver;

import org.jgrapht.Graphs;
import org.jgrapht.UndirectedGraph;
import org.jgrapht.graph.SimpleGraph;
import ru.ifmo.ctddev.gmwcs.LDSU;
import ru.ifmo.ctddev.gmwcs.TimeLimit;
import ru.ifmo.ctddev.gmwcs.graph.Edge;
import ru.ifmo.ctddev.gmwcs.graph.Node;
import ru.ifmo.ctddev.gmwcs.graph.Unit;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

public class ComponentSolver implements Solver {
    private final RLTSolver solver;
    private TimeLimit tl;
    private double lb;
    private Integer BFNum;
    private boolean suppressing;

    public ComponentSolver(RLTSolver solver) {
        this.solver = solver;
        lb = 0.0;
        tl = new TimeLimit(Double.POSITIVE_INFINITY);
    }

    @Override
    public List<Unit> solve(UndirectedGraph<Node, Edge> origin, LDSU<Unit> synonyms) throws SolverException {
        solver.clearRoots();
        int BFNum = this.BFNum;
        UndirectedGraph<Node, Edge> graph = new SimpleGraph<>(Edge.class);
        Graphs.addGraph(graph, origin);
        if (graph.vertexSet().isEmpty()) {
            return null;
        }
        List<Unit> best = null;
        solver.setTimeLimit(tl);
        Set<Set<Unit>> groups = getNGroups(graph);
        if (groups.size() < BFNum) {
            BFNum = groups.size();
        }
        groups.forEach(solver::addRoot);
        for (int i = 0; i < BFNum; i++) {
            solver.setBFNum(BFNum - i);
            solver.setLB(Math.max(lb, Utils.sum(best, synonyms)));
            List<Unit> sol = solver.solve(graph, synonyms);
            if (sol != null) {
                best = sol;
            }
        }
        for (Set<Unit> c : groups) {
            for (Unit unit : c) {
                if (unit instanceof Node) {
                    graph.removeVertex((Node) unit);
                } else {
                    graph.removeEdge((Edge) unit);
                }
            }
        }
        solver.clearRoots();
        solver.setBFNum(0);
        solver.setLB(Math.max(lb, Utils.sum(best, synonyms)));
        List<Unit> sol2 = solver.solve(graph, synonyms);
        return sol2 == null ? best : sol2;
    }

    private Set<Set<Unit>> getNGroups(UndirectedGraph<Node, Edge> graph) {
        Set<Set<Unit>> groups = new LinkedHashSet<>();
        PriorityQueue<Node> nodes = new PriorityQueue<>();
        PriorityQueue<Edge> edges = new PriorityQueue<>();
        nodes.addAll(graph.vertexSet());
        edges.addAll(graph.edgeSet());
        for (int i = 0; i < BFNum; i++) {
            PriorityQueue<? extends Unit> current;
            if (nodes.isEmpty() && edges.isEmpty()) {
                break;
            }
            if (nodes.isEmpty()) {
                current = edges;
            } else if (edges.isEmpty()) {
                current = nodes;
            } else {
                if (nodes.peek().getWeight() > edges.peek().getWeight()) {
                    current = nodes;
                } else {
                    current = edges;
                }
            }
            Set<Unit> group = new LinkedHashSet<>();
            double weight = current.peek().getWeight();
            if (!suppressing) {
                if (current == nodes) {
                    System.out.print("Chosen nodes with weight " + weight + " as group: ");
                } else {
                    System.out.print("Chosen edges with weight " + weight + " as group: ");
                }
            }
            while (!current.isEmpty() && current.peek().getWeight() == weight) {
                group.add(current.poll());
            }
            if (!suppressing) {
                System.out.println(group.size() + " elements");
            }
            groups.add(group);
        }
        return groups;
    }

    @Override
    public void setTimeLimit(TimeLimit tl) {
        this.tl = tl;
    }

    @Override
    public void suppressOutput() {
        this.suppressing = true;
        solver.suppressOutput();
    }

    @Override
    public void setLB(double lb) {
        if (lb < 0.0) {
            return;
        }
        this.lb = lb;
    }

    public void setBFNum(Integer BFNum) {
        this.BFNum = BFNum;
    }
}
