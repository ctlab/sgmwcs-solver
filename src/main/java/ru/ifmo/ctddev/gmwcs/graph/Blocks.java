package ru.ifmo.ctddev.gmwcs.graph;

import java.util.*;

public class Blocks {
    private Graph graph;
    private Map<Node, Integer> enter;
    private Map<Node, Integer> up;
    private Stack<Edge> stack;
    private Set<Set<Node>> components;
    private Set<Node> cutpoints;
    private Node root;
    private Map<Node, List<Set<Node>>> intersection;
    private Map<Node, Set<Node>> componentOf;
    private Map<Set<Node>, Set<Node>> cpsOf;
    private int rootChildren;
    private int time;

    public Blocks(Graph graph) {
        enter = new LinkedHashMap<>();
        up = new LinkedHashMap<>();
        stack = new Stack<>();
        root = graph.vertexSet().iterator().next();
        components = new LinkedHashSet<>();
        cutpoints = new LinkedHashSet<>();
        this.graph = graph;
        if (graph.vertexSet().size() > 1) {
            dfs(root, null);
        } else {
            Set<Node> component = new LinkedHashSet<>();
            component.add(root);
            components.add(component);
        }
        postProcessing();
    }

    public Set<Node> componentOf(Node node) {
        return componentOf.get(node);
    }

    public Set<Node> cutpointsOf(Set<Node> component) {
        return cpsOf.get(component);
    }

    public List<Set<Node>> incidentBlocks(Node cp) {
        return intersection.get(cp);
    }

    private void postProcessing() {
        cpsOf = new HashMap<>();
        componentOf = new HashMap<>();
        intersection = new HashMap<>();
        for (Set<Node> component : components) {
            for (Node cp : cutpoints) {
                if (component.contains(cp)) {
                    if (!intersection.containsKey(cp)) {
                        intersection.put(cp, new ArrayList<>());
                    }
                    intersection.get(cp).add(component);
                    if (!cpsOf.containsKey(component)) {
                        cpsOf.put(component, new HashSet<>());
                    }
                    cpsOf.get(component).add(cp);
                }
            }
        }
        for (Set<Node> component : components()) {
            for (Node node : component) {
                componentOf.put(node, component);
            }
        }
    }

    public void dfs(Node v, Node parent) {
        time++;
        enter.put(v, time);
        up.put(v, time);
        for (Node u : graph.neighborListOf(v)) {
            if (u == parent) {
                continue;
            }
            if (!enter.containsKey(u)) {
                stack.add(graph.getEdge(v, u));
                if (v == root) {
                    ++rootChildren;
                }
                dfs(u, v);
                if (up.get(u) >= enter.get(v)) {
                    Set<Node> component = new LinkedHashSet<>();
                    Edge expected = graph.getEdge(v, u);
                    while (true) {
                        Edge edge = stack.pop();
                        component.add(graph.getEdgeSource(edge));
                        component.add(graph.getEdgeTarget(edge));
                        if (edge == expected) {
                            break;
                        }
                    }
                    components.add(component);
                    cutpoints.add(v);
                }
                if (up.get(u) < up.get(v)) {
                    up.put(v, up.get(u));
                }
            } else {
                if (up.get(v) > enter.get(u)) {
                    up.put(v, enter.get(u));
                }
            }
        }
        if (rootChildren < 2) {
            cutpoints.remove(root);
        }
    }

    public Set<Set<Node>> components() {
        return components;
    }

    public Set<Node> cutpoints() {
        return cutpoints;
    }
}
