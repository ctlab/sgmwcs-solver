package ru.ifmo.ctddev.gmwcs.graph;

public abstract class Unit implements Comparable<Unit> {
    protected int num;
    protected double weight;

    public Unit(int num, double weight) {
        this.num = num;
        this.weight = weight;
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