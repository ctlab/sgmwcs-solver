package ru.ifmo.ctddev.gmwcs.graph;

import ru.ifmo.ctddev.gmwcs.Signals;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public abstract class Unit implements Comparable<Unit> {
    protected int num;
    protected List<Unit> absorbed;

    public Unit(int num) {
        this.num = num;
        absorbed = new ArrayList<>();
    }

    public Unit(Unit that) {
        this(that.num);
    }

    public void absorb(Unit unit) {
        absorbed.addAll(unit.getAbsorbed());
        unit.clear();
        absorbed.add(unit);
    }

    public void clear() {
        absorbed.clear();
    }

    public List<Unit> getAbsorbed() {
        return new ArrayList<>(absorbed);
    }

    @Override
    public int hashCode() {
        return num;
    }

    public int getNum() {
        return num;
    }


    @Override
    public boolean equals(Object o) {
        return (o.getClass() == getClass() && num == ((Unit) o).num);
    }

    @Override
    public int compareTo(Unit u) {
        return Integer.compare(u.getNum(), num);
    }
}