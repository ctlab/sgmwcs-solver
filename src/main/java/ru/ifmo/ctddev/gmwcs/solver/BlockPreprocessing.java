package ru.ifmo.ctddev.gmwcs.solver;

import ru.ifmo.ctddev.gmwcs.Signals;
import ru.ifmo.ctddev.gmwcs.graph.*;

import java.util.*;

public class BlockPreprocessing {
    private Graph graph;
    private Signals signals;
    private Blocks blocks;
    private Set<Node> toRemove = new HashSet<>();

    private double lb;

    /**
     * @param root
     */
    public BlockPreprocessing(Graph graph, Signals signals, Node root) {
        this.graph = graph;
        this.signals = signals;
        blocks = new Blocks(graph);
//        unreachableNodes(root, graph.vertexSet(), blocks.cutpoints());
        dfs(blocks.componentOf(root), root);
    }

    public void setLB(double lb) {
        this.lb = lb;
    }

    public Set<Node> result() {
        return toRemove;
    }

    private Set<Node> dfs(Set<Node> block, Node parent) {
        Set<Node> S = new HashSet<>(block);
        for (Node cp : blocks.cutpointsOf(block)) {
            if (cp == parent) continue;
            for (Set<Node> bl : blocks.incidentBlocks(cp)) {
                if (bl == block) continue;
                S.addAll(dfs(bl, cp));
            }
        }
        unreachableNodes(parent, S, blocks.cutpointsOf(block));
        return S;
    }

    private void unreachableNodes(Node root, Set<Node> block, Set<Node> cps) {
        PSD psd = new PSD(graph.subgraph(block), signals);
        psd.decompose();
        Dijkstra dk = new Dijkstra(graph.subgraph(block), signals);
        dk.solve(root);
        final Map<Node, Double> bottlenecks = dk.distances();
        Comparator<Node> comparator = Comparator.comparingDouble(bottlenecks::get);
        NavigableSet<Node> dists = new TreeSet<>(comparator);
        double ub = psd.ub();
        dists.addAll(bottlenecks.keySet());
        while (true) {
            Node n = dists.pollLast();
            if (n == null || n == root) break;
            if (cps.contains(n)) continue;
            if (!bottlenecks.containsKey(n) || this.lb >= ub - bottlenecks.get(n)) {
               toRemove.add(n);
            } else {
               break;
            }
        }
    }
}
