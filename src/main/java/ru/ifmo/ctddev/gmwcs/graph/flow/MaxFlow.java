package ru.ifmo.ctddev.gmwcs.graph.flow;

import ru.ifmo.ctddev.gmwcs.Pair;

import java.util.List;

public interface MaxFlow {
    void addEdge(int i, int j);

    void setCapacity(int i, int j, double c);

    List<Pair<Integer, Integer>> computeMinCut(int s, int t, double threshold);
}
