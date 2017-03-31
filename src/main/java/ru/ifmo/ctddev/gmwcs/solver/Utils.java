package ru.ifmo.ctddev.gmwcs.solver;

import ru.ifmo.ctddev.gmwcs.Signals;
import ru.ifmo.ctddev.gmwcs.graph.Unit;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Utils {
    public static double sum(Collection<? extends Unit> units, Signals synonyms) {
        if (units == null) {
            return 0;
        }
        double result = 0;
        Set<Unit> us = new HashSet<>();
        us.addAll(units);
        for (int i = 0; i < synonyms.size(); i++) {
            List<Unit> set = synonyms.set(i);
            for (Unit unit : set) {
                if (us.contains(unit)) {
                    result += synonyms.weight(i);
                    break;
                }
            }
        }
        return result;
    }
}
