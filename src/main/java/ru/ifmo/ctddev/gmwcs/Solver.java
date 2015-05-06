package ru.ifmo.ctddev.gmwcs;

import ilog.concert.IloException;
import org.jgrapht.UndirectedGraph;
import org.jgrapht.alg.BiconnectivityInspector;
import org.jgrapht.alg.ConnectivityInspector;
import org.jgrapht.graph.SimpleGraph;
import org.jgrapht.graph.UndirectedSubgraph;

import java.util.*;

public abstract class Solver {
    protected int threads;

    protected abstract List<Unit> solveBiComponent(UndirectedGraph<Node, Edge> graph, Node root, double tl) throws IloException;

    public List<Unit> solve(UndirectedGraph<Node, Edge> graph, int threads, double tl) throws IloException {
        List<Unit> best = null;
        this.threads = threads;
        double maxWeight = 0.0;
        ConnectivityInspector<Node, Edge> inspector = new ConnectivityInspector<>(graph);
        TimeLimiter limiter = new TimeLimiter(tl);
        int nodeRemains = graph.vertexSet().size();
        List<Set<Node>> connectedSets = new ArrayList<>();
        connectedSets.addAll(inspector.connectedSets());
        Collections.sort(connectedSets, new SetComparator<Node>());
        for (Set<Node> component : connectedSets) {
            double fraction = (limiter.getRemainingTime() / nodeRemains) * component.size();
            nodeRemains -= component.size();
            Set<Edge> edges = new LinkedHashSet<>();
            for (Edge edge : graph.edgeSet()) {
                if (component.contains(graph.getEdgeSource(edge))) {
                    edges.add(edge);
                }
            }
            UndirectedGraph<Node, Edge> subgraph = new UndirectedSubgraph<>(graph, component, edges);
            List<Unit> solution = solveComponent(clone(subgraph), tl < 0 ? -1 : fraction, limiter);
            if (sum(solution) > maxWeight) {
                maxWeight = sum(solution);
                best = solution;
            }
        }
        checkConnectivity(graph, best);
        return best;
    }

    private List<Unit> solveComponent(UndirectedGraph<Node, Edge> graph, double tl,
                                      TimeLimiter limiter) throws IloException {
        BiconnectivityInspector<Node, Edge> inspector = new BiconnectivityInspector<>(graph);
        Set<Node> cutpoints = inspector.getCutpoints();
        List<Set<Node>> components = new ArrayList<>();
        components.addAll(inspector.getBiconnectedVertexComponents());
        Collections.sort(components, new SetComparator<Node>());
        List<Unit> best = null;
        int nodeRemains = graph.vertexSet().size() + components.size() - 1;
        while (components.size() > 1) {
            Set<Node> component = null;
            Node cutpoint = null;
            for (Set<Node> comp : components) {
                int cutNodes = 0;
                for (Node point : cutpoints) {
                    if (comp.contains(point)) {
                        cutNodes++;
                        cutpoint = point;
                        if (cutNodes == 2) {
                            break;
                        }
                    }
                }
                if (cutNodes == 1) {
                    component = new LinkedHashSet<>();
                    component.addAll(comp);
                    break;
                }
            }
            if (component.contains(new Node(1223, 0.0))) {
                int hh = 1;
            }
            components.remove(component);
            int cnt = 0;
            for (Set<Node> comp : components) {
                if (comp.contains(cutpoint)) {
                    cnt++;
                    if (cnt > 1) {
                        break;
                    }
                }
            }
            if (cnt < 2) {
                cutpoints.remove(cutpoint);
            }
            Set<Edge> edgeSet = new LinkedHashSet<>();
            for (Edge edge : graph.edgeSet()) {
                Node from = graph.getEdgeSource(edge);
                Node to = graph.getEdgeTarget(edge);
                if (component.contains(from) && component.contains(to)) {
                    edgeSet.add(edge);
                }
            }
            double fraction = ((tl / nodeRemains) * component.size()) / 2;
            UndirectedGraph<Node, Edge> subgraph = new UndirectedSubgraph<>(graph, component, edgeSet);
            long timeBefore = System.currentTimeMillis();
            List<Unit> unrooted = solveBiComponent(subgraph, null, tl < 0 ? -1 : fraction);
            if (sum(unrooted) > sum(best)) {
                best = getResult(unrooted);
            }
            List<Unit> rooted;
            if (!unrooted.contains(cutpoint)) {
                rooted = solveBiComponent(subgraph, cutpoint, tl < 0 ? -1 : fraction);
            } else {
                rooted = unrooted;
            }
            long timeAfter = System.currentTimeMillis();
            if (tl > 0) {
                nodeRemains -= component.size();
                double spent = Math.min((timeAfter - timeBefore) / 1000.0, fraction * 2);
                limiter.spend(spent);
                tl -= spent;
            }
            for (Node node : component) {
                if (node != cutpoint) {
                    graph.removeVertex(node);
                }
            }
            if (rooted != null) {
                for (Unit unit : rooted) {
                    if (unit != cutpoint) {
                        cutpoint.addAbsorbedUnit(unit);
                        cutpoint.addAllAbsorbedUnits(unit.getAbsorbedUnits());
                        cutpoint.setWeight(cutpoint.getWeight() + unit.getWeight());
                    }
                }
            }
        }
        long timeBefore = System.currentTimeMillis();
        List<Unit> unrooted = solveBiComponent(graph, null, tl < 0 ? -1 : tl);
        long timeAfter = System.currentTimeMillis();
        if (tl > 0) {
            limiter.spend(Math.min((timeAfter - timeBefore) / 1000.0, tl));
        }
        if (sum(unrooted) > sum(best)) {
            best = getResult(unrooted);
        }
        return best;
    }

    private void checkConnectivity(UndirectedGraph<Node, Edge> graph, List<Unit> result) {
        Set<Node> nodes = new LinkedHashSet<>();
        Set<Edge> edges = new LinkedHashSet<>();
        if (result == null) {
            return;
        }
        for (Unit unit : result) {
            if (unit instanceof Node) {
                nodes.add((Node) unit);
            } else {
                edges.add((Edge) unit);
            }
        }
        UndirectedGraph<Node, Edge> subgraph = new UndirectedSubgraph<>(graph, nodes, edges);
        ConnectivityInspector<Node, Edge> inspector = new ConnectivityInspector<>(subgraph);
        if (!inspector.isGraphConnected()) {
            throw new IllegalStateException();
        }
    }

    private List<Unit> getResult(List<Unit> units) {
        if (units == null) {
            return null;
        }
        List<Unit> result = new ArrayList<>();
        for (Unit unit : units) {
            result.addAll(unit.getAbsorbedUnits());
        }
        return result;
    }

    private UndirectedGraph<Node, Edge> clone(UndirectedGraph<Node, Edge> source) {
        UndirectedGraph<Node, Edge> graph = new SimpleGraph<>(Edge.class);
        Map<Node, Node> old2new = new LinkedHashMap<>();
        for (Node node : source.vertexSet()) {
            Node newNode = new Node(node.getNum(), node.getWeight());
            newNode.addAbsorbedUnit(node);
            old2new.put(node, newNode);
            graph.addVertex(newNode);
        }
        for (Edge edge : source.edgeSet()) {
            Node from = old2new.get(source.getEdgeSource(edge));
            Node to = old2new.get(source.getEdgeTarget(edge));
            Edge newEdge = new Edge(edge.getNum(), edge.getWeight());
            newEdge.addAbsorbedUnit(edge);
            graph.addEdge(from, to, newEdge);
        }
        return graph;
    }

    private double sum(List<Unit> units) {
        if (units == null) {
            return 0;
        }
        double res = 0;
        for (Unit unit : units) {
            res += unit.getWeight();
        }
        return res;
    }

    private class SetComparator<E> implements Comparator<Set<E>> {
        @Override
        public int compare(Set<E> o1, Set<E> o2) {
            if (o1.size() < o2.size()) {
                return -1;
            }
            if (o1.size() > o2.size()) {
                return 1;
            }
            return 0;
        }
    }

    private class TimeLimiter {
        private double tl;

        public TimeLimiter(double tl) {
            this.tl = tl;
        }

        public void spend(double time) {
            tl -= time;
        }

        public double getRemainingTime() {
            return tl;
        }
    }
}
