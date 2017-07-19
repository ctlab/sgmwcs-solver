package ru.ifmo.ctddev.gmwcs.graph;

public class Edge extends Unit {

    public Edge(Edge that) {
        super(that);
    }

    public Edge(int num) {
        super(num);
    }

    @Override
    public String toString() {
        return "E(" + String.valueOf(num) + ")";
    }
}
