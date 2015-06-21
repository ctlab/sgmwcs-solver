package ru.ifmo.ctddev.gmwcs.graph;

public class Node extends Unit {

    public Node(int num, double weight) {
        super(num, weight);
    }

    @Override
    public String toString() {
        return "N(" + String.valueOf(num) + ", " + String.valueOf(weight) + ")";
    }
}
