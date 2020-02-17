package ru.itmo.ctlab.sgmwcs.graph;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by Nikolay Poperechnyi on 15.11.19.
 */
public class BicomponentSeparator {

    private Graph g;
    private Set<Edge> cut;


    public BicomponentSeparator(Graph graph) {
        this.g = new Graph(graph);
        Blocks b = new Blocks(g);
        Set<Node> max = new HashSet<>();
        for (Set<Node> c: b.components()) {
            if (c.size() > max.size()) // TODO: из таких разрезов искать
                max = c;               // наиболее сбалансированный
        }
        g = g.subgraph(max);
        System.err.println("cs size: " + g.connectedSets().size());
        System.err.println("graph size: " + g.units().size());
        if (b.cutpoints().size() == 0 || b.cutpointsOf(max).size() > 1) {
            cut = new KargerStein(g).minCut();
            cut.forEach(g::removeEdge);
        }
    }

    public Graph getBicomp() {
        return g;
    }

    public Set<Edge> getCut() {
        return cut;
    }
}
