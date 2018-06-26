package ru.ifmo.ctddev.gmwcs.solver;

import ru.ifmo.ctddev.gmwcs.Signals;
import ru.ifmo.ctddev.gmwcs.graph.*;

import java.util.*;

public class BlockPreprocessing {
    private Graph graph;
    private Signals signals;
    private Blocks blocks;
    private Set<Node> toRemove = new HashSet<>();

    /**
     * @param root
     */
    public BlockPreprocessing(Graph graph, Signals signals, Node root) {
        this.graph = graph;
        this.signals = signals;
        blocks = new Blocks(graph);
        unreachableNodes(root, bl);
    }

    public Set<Node> result() {
        return toRemove;
    }

    /* private void preprocess(Node root) {
        for (Set<Node> block : blocks.incidentBlocks(root)) {
            dfs(block, root);
        }
    }

    private Set<Node> dfs(Set<Node> block, Node parent) {
        Set<Node> S = new HashSet<>();
        for (Node cp : blocks.cutpointsOf(block)) {
            if (cp == parent) continue;
            for (Set<Node> bl : blocks.incidentBlocks(cp)) {
                if (bl == block) continue;
                S.addAll(dfs(bl, cp));
            }
        }
        S.addAll(block);
        S = doPreprocessing(S, parent);
        return S;
    }
    */

    private void unreachableNodes(Node root, Set<Node> block) {
        Dijkstra dk = new Dijkstra(graph, signals);
        dk.solve(root);
        final Map<Node, Double> bottlenecks = dk.distances();
        Comparator<Node> comparator = Comparator.comparingDouble(bottlenecks::get);
        NavigableSet<Node> dists = new TreeSet<>(comparator);
        Set<Integer> posSigs = signals.positiveUnitSets(graph.vertexSet());
        double S = signals.weightSum(posSigs);
        dists.addAll(bottlenecks.keySet());
        while (true) {
            Node n = dists.pollLast();
            if (n == null || n == root) break;
            if (!bottlenecks.containsKey(n) || bottlenecks.get(n) >= S) {
               toRemove.add(n);
               if (signals.bijection(n)) {
                   S -= signals.maxSum(n);
               }
            } else {
               break;
            }
        }
    }
}
