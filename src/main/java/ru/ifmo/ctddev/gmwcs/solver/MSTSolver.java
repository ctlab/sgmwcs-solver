package ru.ifmo.ctddev.gmwcs.solver;

import ru.ifmo.ctddev.gmwcs.graph.Edge;
import ru.ifmo.ctddev.gmwcs.graph.Graph;
import ru.ifmo.ctddev.gmwcs.graph.Node;

import java.util.*;

/**
 * Created by Nikolay Poperechnyi on 30/01/2018.
 */
public class MSTSolver {
    private final Graph g;
    private final Map<Edge, Double> ws;
    private final Node root;
    private double cost;
    private Set<Edge> res;

    public MSTSolver(Graph g, Map<Edge, Double> edgeWeights, Node root) {
        this.g = g;
        this.ws = edgeWeights;
        this.root = root;
    }

    public double getCost() {
        return cost;
    }

    public Set<Edge> getEdges() {
        return res;
    }

    public void solve() {
        Double cost = 0.0;
        Set<Edge> res = new HashSet<>();
        Set<Node> unvisited = new HashSet<>(g.vertexSet());
        Node cur = root;
        unvisited.remove(root);
        PriorityQueue<Edge> q =
                new PriorityQueue<>(Comparator.comparingDouble(ws::get));
        while (!unvisited.isEmpty()) {
            Node nbor;
            for (Edge e : g.edgesOf(cur)) {
                nbor = g.getOppositeVertex(cur, e);
                if (unvisited.contains(nbor)) {
                    q.add(e);
                }
            }
            final Edge e = q.remove();
            final Node et = g.getEdgeTarget(e);
            final Node es = g.getEdgeSource(e);
            if (unvisited.contains(et) || unvisited.contains(es)) {
                cost += ws.get(e);
                res.add(e);
                cur = unvisited.contains(et) ? et : es;
            }
            unvisited.remove(cur);
        }
        this.cost = cost;
        this.res = res;
    }
}
