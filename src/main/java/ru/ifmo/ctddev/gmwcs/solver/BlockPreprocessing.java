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
            doPreprocessing(graph.vertexSet(), root);
            //preprocess(root);
    }

    public Set<Node> result() {
        return toRemove;
    }

    private void preprocess(Node root) {
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

    private Set<Node> doPreprocessing(Set<Node> nodes, Node parent) {
        nodes = new HashSet<>(nodes);
        Graph subgraph = graph.subgraph(nodes);
        Set<Unit> units = new HashSet<>(subgraph.edgeSet());
        units.addAll(nodes);
        List<Integer> posSignals = signals.positiveUnitSets(units, false);
        Collections.sort(posSignals);
        double sum = signals.weightSum(posSignals);
        Dijkstra dk = new Dijkstra(subgraph, signals);
        dk.solve(parent);
        List<Map.Entry<Node, Double>> distances = new ArrayList<>(dk.distances().entrySet());
        distances.sort(Comparator.comparingDouble(Map.Entry::getValue));
        for (int i = distances.size() - 1;
             i >= 0 && distances.get(i).getValue() > sum;
             --i) {
            Node node = distances.get(i).getKey();
            /*Set<Edge> edges = subgraph.edgesOf(node);
            List<Integer> nodePos = signals.positiveUnitSets(node);
            nodePos.addAll(signals.positiveUnitSets(edges));
            for (int sig : nodePos) {
                int pos = Collections.binarySearch(posSignals, sig);
                if (pos >= 0) {
                    posSignals.remove(pos);
                    //Don't decrease sum if more than 1 unit contains this signal
                    if (pos < posSignals.size() && posSignals.get(pos) == sig
                            || pos > 0 && posSignals.get(pos - 1) == sig) {
                        continue;
                    }
                    sum -= signals.weight(sig);
                }
            }*/
            nodes.remove(node);
            toRemove.add(node);
        }
        return nodes;
    }

}
