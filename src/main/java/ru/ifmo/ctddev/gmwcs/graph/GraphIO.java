package ru.ifmo.ctddev.gmwcs.graph;

import ru.ifmo.ctddev.gmwcs.LDSU;

import java.io.*;
import java.text.ParseException;
import java.util.*;

public class GraphIO {
    private File nodeIn;
    private File edgeIn;
    private Map<String, Node> nodeNames;
    private Map<Unit, String> unitMap;
    private LDSU<Unit> signals;
    private Map<String, Unit> signalNames;

    public GraphIO(File nodeIn, File edgeIn) {
        this.nodeIn = nodeIn;
        this.edgeIn = edgeIn;
        signals = new LDSU<>();
        nodeNames = new LinkedHashMap<>();
        unitMap = new HashMap<>();
        signalNames = new HashMap<>();
    }

    public Graph read() throws IOException, ParseException {
        try (LineNumberReader nodes = new LineNumberReader(new FileReader(nodeIn));
             LineNumberReader edges = new LineNumberReader(new FileReader(edgeIn))) {
            Graph graph = new Graph();
            parseNodes(nodes, graph);
            parseEdges(edges, graph);
            return graph;
        }
    }

    private void parseNodes(LineNumberReader reader, Graph graph) throws ParseException, IOException {
        String line;
        int cnt = 0;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("#")) {
                continue;
            }
            StringTokenizer tokenizer = new StringTokenizer(line);
            if (!tokenizer.hasMoreTokens()) {
                continue;
            }
            String node = tokenizer.nextToken();
            if (!tokenizer.hasMoreTokens()) {
                throw new ParseException("Expected weight of node at line", reader.getLineNumber());
            }
            String weightStr = tokenizer.nextToken();
            try {
                double weight = Double.parseDouble(weightStr);
                Node vertex = new Node(cnt++, weight);
                if (nodeNames.containsKey(node)) {
                    throw new ParseException("Duplicate node " + node, 0);
                }
                nodeNames.put(node, vertex);
                graph.addVertex(vertex);
                processSignals(vertex, tokenizer, weight);
                unitMap.put(vertex, node + "\t" + weightStr);
            } catch (NumberFormatException e) {
                throw new ParseException("Expected floating point value of weight at line", reader.getLineNumber());
            }
        }
    }

    private void parseEdges(LineNumberReader reader, Graph graph) throws ParseException, IOException {
        int cnt = 0;
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("#")) {
                continue;
            }
            StringTokenizer tokenizer = new StringTokenizer(line);
            if (!tokenizer.hasMoreTokens()) {
                continue;
            }
            String first = tokenizer.nextToken();
            if (!tokenizer.hasMoreTokens()) {
                throw new ParseException("Wrong edge format at line", reader.getLineNumber());
            }
            String second = tokenizer.nextToken();
            if (!tokenizer.hasMoreTokens()) {
                throw new ParseException("Expected weight of edge at line", reader.getLineNumber());
            }
            try {
                double weight = Double.parseDouble(tokenizer.nextToken());
                if (!nodeNames.containsKey(first) || !nodeNames.containsKey(second)) {
                    throw new ParseException("There's no such vertex in edge list at line", reader.getLineNumber());
                }
                Edge edge = new Edge(cnt++, weight);
                Node from = nodeNames.get(first);
                Node to = nodeNames.get(second);
                graph.addEdge(from, to, edge);
                unitMap.put(edge, first + "\t" + second + "\t" + weight);
                processSignals(edge, tokenizer, weight);
            } catch (NumberFormatException e) {
                throw new ParseException("Expected floating point value of edge in line", reader.getLineNumber());
            }
        }
    }

    private void processSignals(Unit unit, StringTokenizer tokenizer, double weight) {
        List<String> tokens = new ArrayList<>();
        while(tokenizer.hasMoreTokens()){
            tokens.add(tokenizer.nextToken());
        }
        signals.add(unit, weight);
        if(!tokens.isEmpty()){
            for(String token : tokens){
                if(signalNames.containsKey(token)){

                } else {
                    signalNames.put(token, unit);
                }
            }
        }
    }

    public void write(List<Unit> units, File output) throws IOException {
        if (units == null) {
            units = new ArrayList<>();
        }
        PrintWriter writer = new PrintWriter(output);
        for(Unit unit : units){
            if(!unitMap.containsKey(unit)){
                throw new IllegalStateException();
            }
            writer.println(unitMap.get(unit));
        }
    }

    public LDSU<Unit> getSignals() {
        return signals;
    }
}
