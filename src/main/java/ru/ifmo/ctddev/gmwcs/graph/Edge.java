package ru.ifmo.ctddev.gmwcs.graph;

public class Edge extends Unit {

    public Edge(Edge that) {
        super(that);
    }

    public Edge(int num, double weight) {
        super(num, weight);
    }

    @Override
    public String toString() {
        return "E(" + String.valueOf(num) + ", " + String.valueOf(weight) + ")";
    }
}
