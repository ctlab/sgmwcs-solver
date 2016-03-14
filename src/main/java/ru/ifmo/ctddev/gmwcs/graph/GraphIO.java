package ru.ifmo.ctddev.gmwcs.graph;

import java.io.IOException;
import java.text.ParseException;
import java.util.List;

public interface GraphIO {
    Graph read() throws IOException, ParseException;

    void write(List<Unit> units) throws IOException;

    Node getNode(String rootName);
}
