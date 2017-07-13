package ru.ifmo.ctddev.gmwcs;

import ru.ifmo.ctddev.gmwcs.graph.Unit;

import java.io.*;
import java.util.*;

public class Signals {
    private List<Set<Unit>> sets;
    private Map<Unit, List<Integer>> unitsSets;
    private List<Double> weights;

    public Signals() {
        sets = new ArrayList<>();
        unitsSets = new HashMap<>();
        weights = new ArrayList<>();
    }

    public Signals(Signals signals, Set<Unit> subset) {
        this();
        for (Unit unit : subset) {
            unitsSets.put(unit, new ArrayList<>());
        }
        int j = 0;
        for (int i = 0; i < signals.size(); i++) {
            Set<Unit> set = new HashSet<>();
            for (Unit unit : signals.set(i)) {
                if (subset.contains(unit)) {
                    set.add(unit);
                    unitsSets.get(unit).add(j);
                }
            }
            if (!set.isEmpty()) {
                sets.add(set);
                weights.add(signals.weight(i));
                j++;
            }
        }
    }

    public int size() {
        return sets.size();
    }

    public double weight(int num) {
        return weights.get(num);
    }

    public void join(Unit what, Unit with) {
        List<Integer> x = unitsSets.get(what);
        List<Integer> main = unitsSets.get(with);
        int i = 0, j = 0;
        List<Integer> result = new ArrayList<>();
        while (i != x.size() || j != main.size()) {
            int set;
            if (!(j == main.size()) && (i == x.size() || main.get(j) < x.get(i))) {
                set = main.get(j);
                ++j;
            } else {
                set = x.get(i);
                sets.get(set).remove(what);
                sets.get(set).add(with);
                ++i;
            }
            if (result.isEmpty() || result.get(result.size() - 1) != set) {
                result.add(set);
            }
        }
        unitsSets.put(with, result);
        unitsSets.remove(what);
    }

    public List<Unit> set(int num) {
        List<Unit> result = new ArrayList<>();
        result.addAll(sets.get(num));
        return result;
    }

    public void add(Unit unit, int signal) {
        sets.get(signal).add(unit);
        ensureLink(unit, signal);
    }

    public int add(Unit unit) {
        Set<Unit> s = new HashSet<>();
        s.add(unit);
        sets.add(s);
        weights.add(unit.getWeight());
        int num = sets.size() - 1;
        ensureLink(unit, num);
        return num;
    }

    public void setWeight(int set, double weight) {
        weights.set(set, weight);
    }

    private void ensureLink(Unit unit, int signal) {
        if (unitsSets.containsKey(unit)) {
            unitsSets.get(unit).add(signal);
        } else {
            List<Integer> l = new ArrayList<>();
            unitsSets.put(unit, l);
            l.add(signal);
        }
    }
}
