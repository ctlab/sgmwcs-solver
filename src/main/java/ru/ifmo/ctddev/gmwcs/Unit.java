package ru.ifmo.ctddev.gmwcs;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public abstract class Unit {
    protected int num;
    protected double weight;
    protected Set<Unit> absorbed;

    public Unit(int num, double weight) {
        this.num = num;
        this.weight = weight;
        absorbed = new LinkedHashSet<>();
    }

    public Set<Unit> getAbsorbedUnits() {
        return Collections.unmodifiableSet(absorbed);
    }

    public void addAbsorbedUnit(Unit unit) {
        absorbed.add(unit);
    }

    public void addAllAbsorbedUnits(Set<Unit> units) {
        absorbed.addAll(units);
    }

    public void removeAbsorbedUnit(Unit unit) {
        absorbed.remove(unit);
    }

    public void removeAllAbsorbedUnit() {
        absorbed.clear();
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
}