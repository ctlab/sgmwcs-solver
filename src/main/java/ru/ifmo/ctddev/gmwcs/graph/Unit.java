package ru.ifmo.ctddev.gmwcs.graph;

import java.util.ArrayList;
import java.util.List;

public abstract class Unit implements Comparable<Unit> {
    protected int num;
    protected double weight;
    protected List<Unit> absorbed;

    public Unit(int num, double weight) {
        this.num = num;
        this.weight = weight;
        absorbed = new ArrayList<>();
    }

    public void absorb(Unit unit) {
        for (Unit u : unit.getAbsorbed()) {
            absorbed.add(u);
            weight += u.weight;
        }
        unit.clear();
        absorbed.add(unit);
        weight += unit.weight;
    }

    public void clear() {
        for (Unit unit : absorbed) {
            weight -= unit.getWeight();
        }
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

    public double getWeight() {
        return weight;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }

    @Override
    public boolean equals(Object o) {
        return (o.getClass() == getClass() && num == ((Unit) o).num);
    }

    @Override
    public int compareTo(Unit u) {
        if (u.weight != weight) {
            return Double.compare(u.weight, weight);
        }
        return Integer.compare(u.getNum(), num);
    }
}