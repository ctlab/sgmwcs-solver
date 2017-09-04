package ru.ifmo.ctddev.gmwcs.graph;

public class Node extends Unit {

    public Node(Node that) {
        super(that);
    }

    public Node(int num) {
        super(num);
    }

    @Override
    public String toString() {
        return "N(" + String.valueOf(num) + ")";
    }
}
