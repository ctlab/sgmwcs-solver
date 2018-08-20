package ru.ifmo.ctddev.gmwcs;

import ru.ifmo.ctddev.gmwcs.graph.Graph;
import ru.ifmo.ctddev.gmwcs.graph.Unit;

import java.util.*;

public class TestCase {
    private final Map<Unit, Double> weights;
    private Graph graph;
    private Signals signals;

    public TestCase(Graph graph, Map<Unit, Double> weights, Random random) {
        this.graph = graph;
        this.weights = weights;
        Set<Unit> units = new HashSet<>(graph.vertexSet());
        units.addAll(graph.edgeSet());
        makeSignals(random, units);
    }

    private void makeSignals(Random random, Set<Unit> set) {
        signals = new Signals();
        if (set.isEmpty()) {
            return;
        }
        List<Unit> units = new ArrayList<>(set);
        Collections.shuffle(units, random);
        Unit last = units.get(0);
        int signal = signals.addAndSetWeight(last, weights.get(last));
        for (int i = 1; i < units.size(); i++) {
            Unit current = units.get(i);
            if (random.nextBoolean() && weights.get(last) >= 0) {
                signals.add(current, signal);
                signals.setWeight(signal, signals.weight(last));
            } else {
                signal = signals.addAndSetWeight(current, weights.get(current));
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
