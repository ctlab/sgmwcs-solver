package ru.ifmo.ctddev.gmwcs.solver;

import ru.ifmo.ctddev.gmwcs.Signals;
import ru.ifmo.ctddev.gmwcs.graph.Unit;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
}
