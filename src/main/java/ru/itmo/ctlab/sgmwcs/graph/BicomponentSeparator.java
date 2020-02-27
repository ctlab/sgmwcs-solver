package ru.itmo.ctlab.sgmwcs.graph;

import ru.itmo.ctlab.sgmwcs.Signals;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by Nikolay Poperechnyi on 15.11.19.
 */
public class BicomponentSeparator {

    private Graph g;
    private Set<Edge> cut;
    private KargerStein ks;


    public BicomponentSeparator(Graph graph) {
        this.g = new Graph(graph);
        Blocks b = new Blocks(g);
        Set<Node> max = new HashSet<>();
        for (Set<Node> c : b.components()) {
            if (c.size() > max.size()) // TODO: из таких разрезов искать
                max = c;               // наиболее сбалансированный
        }
        g = g.subgraph(max);
        System.err.println("cs size: " + g.connectedSets().size());
        System.err.println("graph size: " + g.units().size());
//        if (b.cutpoints().size() == 0 || b.cutpointsOf(max).size() > 1) {
        ks = new KargerStein(g);
        cut = ks.bestCut();
        cut.forEach(g::removeEdge);
        //       }
    }

    public Set<Edge> edgesOfCuts() {
        return ks.minCuts().stream()
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
    }

    public Graph getResult() {
        return g;
    }

    public Set<Edge> getCut() {
        return cut;
    }
}
