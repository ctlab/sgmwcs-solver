package ru.ifmo.ctddev.gmwcs;

import org.jgrapht.UndirectedGraph;

import java.io.IOException;
import java.text.ParseException;
import java.util.List;

public interface GraphIO {
    UndirectedGraph<Node, Edge> read() throws IOException, ParseException;

    void write(List<Unit> units) throws IOException;
}
