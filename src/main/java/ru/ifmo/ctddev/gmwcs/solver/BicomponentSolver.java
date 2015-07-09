package ru.ifmo.ctddev.gmwcs.solver;

import org.jgrapht.Graphs;
import org.jgrapht.UndirectedGraph;
import org.jgrapht.graph.SimpleGraph;
import ru.ifmo.ctddev.gmwcs.Pair;
import ru.ifmo.ctddev.gmwcs.TimeLimit;
import ru.ifmo.ctddev.gmwcs.graph.Decomposition;
import ru.ifmo.ctddev.gmwcs.graph.Edge;
import ru.ifmo.ctddev.gmwcs.graph.Node;
import ru.ifmo.ctddev.gmwcs.graph.Unit;

import java.util.*;

public class BicomponentSolver {
    private TimeLimit rooted;
    private TimeLimit biggest;
    private TimeLimit unrooted;
    private RLTSolver solver;

    public BicomponentSolver(RLTSolver solver) {
        rooted = new TimeLimit(Double.POSITIVE_INFINITY);
        unrooted = biggest = rooted;
        this.solver = solver;
    }

    public void setRootedTL(TimeLimit tl) {
        this.rooted = tl;
    }

    public void setUnrootedTL(TimeLimit tl) {
        this.unrooted = tl;
    }

    public void setTLForBiggest(TimeLimit tl) {
        this.biggest = tl;
    }

    public List<Unit> solve(UndirectedGraph<Node, Edge> graph) throws SolverException {
        if (graph.vertexSet().size() == 0) {
            return null;
        }
        long timeBefore = System.currentTimeMillis();
        Decomposition decomposition = new Decomposition(graph);
        double duration = (System.currentTimeMillis() - timeBefore) / 1000.0;
        System.out.println("Graph decomposing takes " + duration + " seconds.");
        List<Unit> bestUnrooted = solveUnrooted(graph, decomposition);
        List<Unit> bestBiggest = solveBiggest(graph, decomposition);
        List<Unit> best = Utils.sum(bestBiggest) > Utils.sum(bestUnrooted) ? bestBiggest : bestUnrooted;
        if (Utils.sum(best) < 0) {
            return null;
        }
        return best;
    }

    private Node getRoot(UndirectedGraph<Node, Edge> graph) {
        Set<Node> rootCandidate = new LinkedHashSet<>();
        for (int i = -1; i < graph.vertexSet().size(); i++) {
            rootCandidate.add(new Node(i, 0.0));
        }
        rootCandidate.removeAll(graph.vertexSet());
        return rootCandidate.iterator().next();
    }

    private List<Unit> solveBiggest(UndirectedGraph<Node, Edge> graph, Decomposition decomposition) throws SolverException {
        UndirectedGraph<Node, Edge> tree = new SimpleGraph<>(Edge.class);
        Map<Node, Node> oldCutpoints = new LinkedHashMap<>();
        Node root = getRoot(graph);
        tree.addVertex(root);
        Map<Unit, Node> itsCutpoints = new LinkedHashMap<>();
        for (Pair<Set<Node>, Node> p : decomposition.getRootedComponents()) {
            for (Node node : p.first) {
                for (Edge edge : graph.edgesOf(node)) {
                    itsCutpoints.put(edge, p.second);
                }
                itsCutpoints.put(node, p.second);
            }
            Graphs.addGraph(tree, Utils.subgraph(graph, p.first));
            addAsChild(tree, p.first, p.second, root);
            oldCutpoints.put(p.second, clone(p.second));
        }
        solver.setRoot(root);
        List<Unit> rootedRes = solve("solving rooted parts", tree, rooted);
        solver.setRoot(null);
        UndirectedGraph<Node, Edge> main = Utils.subgraph(graph, decomposition.getBiggestComponent());
        if (rootedRes != null) {
            rootedRes.stream().filter(unit -> unit != root).forEach(unit -> {
                Node cutpoint = itsCutpoints.get(unit);
                cutpoint.addAbsorbedUnit(unit);
                cutpoint.setWeight(cutpoint.getWeight() + unit.getWeight());
            });
        }
        List<Unit> result = solve("solving biggest bicomponent", main, biggest);
        repairCutpoints(oldCutpoints, result);
        return result;
    }

    private void repairCutpoints(Map<Node, Node> oldCutpoints, List<Unit> result) {
        Set<Node> includedCP = new LinkedHashSet<>();
        if (result != null) {
            for (Unit unit : result) {
                if (oldCutpoints.keySet().contains(unit)) {
                    includedCP.add((Node) unit);
                }
            }
        }
        for (Node cp : oldCutpoints.keySet()) {
            boolean addToResult = includedCP.contains(cp);
            Node old = oldCutpoints.get(cp);
            cp.setWeight(old.getWeight());
            Set<Unit> absorbed = new LinkedHashSet<>();
            absorbed.addAll(cp.getAbsorbedUnits());
            for (Unit unit : absorbed) {
                if (!old.getAbsorbedUnits().contains(unit)) {
                    if (addToResult) {
                        result.add(unit);
                    }
                    cp.removeAbsorbedUnit(unit);
                }
            }
        }
    }

    private Node clone(Node node) {
        Node result = new Node(node.getNum(), node.getWeight());
        result.addAllAbsorbedUnits(node.getAbsorbedUnits());
        return result;
    }

    private void addAsChild(UndirectedGraph<Node, Edge> tree, Set<Node> component, Node cutpoint, Node root) {
        for (Node neighbour : Graphs.neighborListOf(tree, cutpoint)) {
            if (!component.contains(neighbour)) {
                continue;
            }
            Edge edge = tree.getEdge(cutpoint, neighbour);
            tree.removeEdge(edge);
            tree.addEdge(root, neighbour, edge);
        }
        tree.removeVertex(cutpoint);
    }

    private List<Unit> solveUnrooted(UndirectedGraph<Node, Edge> graph, Decomposition decomposition) throws SolverException {
        Set<Node> union = new LinkedHashSet<>();
        decomposition.getUnrootedComponents().forEach(union::addAll);
        return solve("solving unrooted parts", Utils.subgraph(graph, union), unrooted);
    }

    public List<Unit> solve(String desc, UndirectedGraph<Node, Edge> graph, TimeLimit tl) throws SolverException {
        solver.setTimeOut(tl.getRemainingTime());
        long timeBefore = System.currentTimeMillis();
        List<Unit> result = solver.solve(graph);
        double duration = (System.currentTimeMillis() - timeBefore) / 1000.0;
        tl.spend(Math.min(duration, tl.getRemainingTime()));
        System.out.println("Operation '" + desc + "' has done. It takes " + duration + " seconds.");
        return result;
    }
}
