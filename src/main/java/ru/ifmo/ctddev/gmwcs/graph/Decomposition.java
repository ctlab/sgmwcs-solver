package ru.ifmo.ctddev.gmwcs.graph;

import org.jgrapht.Graphs;
import org.jgrapht.UndirectedGraph;
import org.jgrapht.alg.ConnectivityInspector;
import ru.ifmo.ctddev.gmwcs.Pair;
import ru.ifmo.ctddev.gmwcs.solver.Utils;

import java.util.*;

public class Decomposition {
    private Set<Node> biggest;
    private List<Set<Node>> unrootedComponents;
    private List<Pair<Set<Node>, Node>> rootedComponents;
    private UndirectedGraph<Node, Edge> graph;

    public Decomposition(UndirectedGraph<Node, Edge> graph) {
        this.graph = graph;
        unrootedComponents = new ArrayList<>();
        rootedComponents = new ArrayList<>();
        ConnectivityInspector<Node, Edge> inspector = new ConnectivityInspector<>(graph);
        List<Set<Node>> components = inspector.connectedSets();
        Set<Node> bestComponent = null;
        Blocks partBest = null;
        int maxSize = 0;
        for (Set<Node> component : components) {
            UndirectedGraph<Node, Edge> subgraph = Utils.subgraph(graph, component);
            Blocks blocks = new Blocks(subgraph);
            Set<Set<Node>> bicomponents = blocks.components();
            for (Set<Node> bicomponent : bicomponents) {
                if (bicomponent.size() > maxSize) {
                    maxSize = bicomponent.size();
                    biggest = bicomponent;
                    partBest = blocks;
                    bestComponent = component;
                }
            }
        }
        for (Set<Node> component : components) {
            if (component != bestComponent) {
                unrootedComponents.add(component);
            }
        }
        processBest(partBest);
    }

    private void processBest(Blocks partBest) {
        Set<Node> cutpoints = new LinkedHashSet<>();
        for (Node cp : partBest.cutpoints()) {
            if (biggest.contains(cp)) {
                cutpoints.add(cp);
            }
        }
        Node root = biggest.iterator().next();
        Node cutpoint = cutpoints.contains(root) ? root : null;
        Map<Node, Boolean> visited = new LinkedHashMap<>();
        Map<Node, Set<Node>> components = new LinkedHashMap<>();
        for (Node cp : cutpoints) {
            components.put(cp, new LinkedHashSet<>());
            components.get(cp).add(cp);
        }
        dfs(root, cutpoint, cutpoints, visited, components);
        for (Node cp : cutpoints) {
            rootedComponents.add(new Pair<>(components.get(cp), cp));
            unrootedComponents.add(components.get(cp));
        }
    }

    private void dfs(Node v, Node cp, Set<Node> cutpoints, Map<Node, Boolean> visited, Map<Node, Set<Node>> comps) {
        visited.put(v, true);
        if (!biggest.contains(v)) {
            comps.get(cp).add(v);
        }
        for (Node u : Graphs.neighborListOf(graph, v)) {
            if (visited.get(u) == null) {
                dfs(u, cutpoints.contains(v) ? v : cp, cutpoints, visited, comps);
            }
        }
    }

    public Set<Node> getBiggestComponent() {
        return biggest;
    }

    public List<Set<Node>> getUnrootedComponents() {
        return unrootedComponents;
    }

    public List<Pair<Set<Node>, Node>> getRootedComponents() {
        return rootedComponents;
    }
}
