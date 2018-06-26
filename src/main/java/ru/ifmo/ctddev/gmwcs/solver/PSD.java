package ru.ifmo.ctddev.gmwcs.solver;

import ru.ifmo.ctddev.gmwcs.Signals;
import ru.ifmo.ctddev.gmwcs.graph.Edge;
import ru.ifmo.ctddev.gmwcs.graph.Graph;
import ru.ifmo.ctddev.gmwcs.graph.Node;
import ru.ifmo.ctddev.gmwcs.graph.Unit;

import java.util.*;

public class PSD {
    Graph g;
    Signals s;


    private double[] d;

    private Map<Node, Center> centers;

    private Map<Node, Path> paths;

    private class Path {
        Node n;
        Path parent;
        Center c;
        Set<Integer> sigs;

        Path(Center c, Node n) {
            this.c = c;
            this.n = n;
            this.parent = this;
        }

        Path(Path p, Node n, Edge e) {
            this.c = p.c;
            this.parent = p;
            this.n = n;
            this.sigs = new HashSet<>(p.sigs);
            this.sigs.addAll(s.unitSets(n, e));
        }
    }

    private class Center {
        List<Integer> signals = new ArrayList<>();
        Unit elem;

        Center(Unit elem) {
            this.elem = elem;
            if (elem instanceof Edge) {
                Edge e = (Edge) elem;
                Node u = g.getEdgeSource(e);
                Node v = g.getEdgeTarget(e);
                signals.addAll(s.unitSets(e, u, v));
                d[u.getNum()] = 0;
                d[v.getNum()] = 0;
                centers.putIfAbsent(u, this);
                centers.putIfAbsent(v, this);
            } else {
                centers.put((Node) elem, this);
                signals.addAll(s.unitSets(elem));
                d[elem.getNum()] = 0;
            }
        }
    }

    public PSD(Graph g, Signals s) {
        this.g = g;
        this.s = s;
        this.centers = new HashMap<>();
        d = new double[g.vertexSet().size()];
        Arrays.fill(d, Double.POSITIVE_INFINITY);
        decompose();
    }

    private void decompose() {
        makeCenters();
        dijkstra();
    }

    private Center addCenter(Unit unit) {
        return new Center(unit);
    }

    private void makeCenters() {
        for (Node n : g.vertexSet()) {
            if (s.minSum(n) == 0) {
                Center c = addCenter(n);
                paths.put(n, new Path(c, n));
            }
        }
        for (Edge e : g.edgeSet()) {
            if (s.minSum(e) == 0) {
                addCenter(e);
            }
        }
    }

    private void dijkstra() {
        PriorityQueue<Node> q = new PriorityQueue<>(
                Comparator.comparingDouble(n -> d[n.getNum()])
        );
        q.addAll(centers.keySet());
        while (!q.isEmpty()) {
            Node cur = q.poll();
            Path p = paths.get(cur);
            for (Node n : g.neighborListOf(cur)) {
                Edge e = g.getEdge(n, cur);
                double w = d[cur.getNum()] - s.weight(e);
                if (d[n.getNum()] > w) {
                    // TODO: edge m.b. positive
                    paths.put(n, new Path(p, n, e));
                    d[n.getNum()] = w;
                    q.add(n);
                }
            }
        }
    }
}
