package ru.ifmo.ctddev.gmwcs;

import ru.ifmo.ctddev.gmwcs.graph.Graph;
import ru.ifmo.ctddev.gmwcs.graph.Unit;

import java.util.*;

public class TestCase {
    private Graph graph;
    private Signals signals;

    public TestCase(Graph graph, Random random) {
        this.graph = graph;
        signals = new Signals();
        Set<Unit> units = new HashSet<>(graph.vertexSet());
        units.addAll(graph.edgeSet());
        makeSynonyms(signals, random, units);
    }

    private static void makeSynonyms(Signals signals, Random random, Set<Unit> set) {
        if (set.isEmpty()) {
            return;
        }
        List<Unit> units = new ArrayList<>(set);
        Collections.shuffle(units, random);
        Unit last = units.get(0);
        int signal = signals.add(last);
        for (int i = 1; i < units.size(); i++) {
            Unit current = units.get(i);
            if (random.nextBoolean()) {
                signals.add(current, signal);
                current.setWeight(last.getWeight());
            } else {
                signal = signals.add(current);
                last = current;
            }
        }
    }

    public Signals signals() {
        return signals;
    }

    public Graph graph() {
        return graph;
    }
}
