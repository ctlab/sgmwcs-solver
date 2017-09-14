package ru.ifmo.ctddev.gmwcs.solver;

import ru.ifmo.ctddev.gmwcs.Signals;
import ru.ifmo.ctddev.gmwcs.graph.Edge;
import ru.ifmo.ctddev.gmwcs.graph.Graph;
import ru.ifmo.ctddev.gmwcs.graph.Node;
import ru.ifmo.ctddev.gmwcs.graph.Unit;

import java.util.*;

public class Utils {
    public static double sum(Collection<? extends Unit> units, Signals signals) {
        if (units == null) {
            return 0;
        }
        double result = 0;
        Set<Unit> us = new HashSet<>();
        us.addAll(units);
        for (int i = 0; i < signals.size(); i++) {
            List<Unit> set = signals.set(i);
            for (Unit unit : set) {
                if (us.contains(unit)) {
                    result += signals.weight(i);
                    break;
                }
            }
        }
        return result;
    }

    public static void copy(Graph inGr, Signals inS, Graph outGr, Signals outS) {
        Map<Unit, Unit> oldToNew = new HashMap<>();
        inGr.vertexSet().forEach(v -> {
            Node nv = new Node(v) ;
            outGr.addVertex(nv);
            oldToNew.put(v, nv);
        });
        inGr.edgeSet().forEach(e -> {
            Node nv = (Node) oldToNew.get(inGr.getEdgeSource(e))
               , nu = (Node) oldToNew.get(inGr.getEdgeTarget(e));
            Edge ne = new Edge(e);
            outGr.addEdge(nv, nu, ne);
            oldToNew.put(e, ne);
        });
        for (int i = 0; i < inS.size(); ++i) {
            final int sz = i;
            List<Unit> oldUnits = inS.set(i);
            oldUnits.forEach(u -> {
                Unit nu = oldToNew.get(u);
                if (outS.size() < sz + 1) {
                    outS.addAndSetWeight(nu, inS.weight(sz));
                } else {
                    outS.add(nu, sz);
                }
            });
        }
    }

    public static <T extends Unit> Set<Set<T>> subsets(Set<T> set) {
        Set<Set<T>> result = new HashSet<>();
        if (set.isEmpty()) {
            result.add(new HashSet<>());
            return result;
        }
        List<T> units = new ArrayList<>(set);
        T head = units.get(0);
        units.remove(0);
        Set<Set<T>> ss = subsets(new HashSet<>(units));
        for (Set<T> s: ss) {
            Set<T> ns = new HashSet<>(s);
            result.add(s);
            ns.add(head);
            result.add(ns);
        }
        return result;
    }

}
