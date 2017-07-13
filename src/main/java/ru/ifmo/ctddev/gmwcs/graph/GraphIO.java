package ru.ifmo.ctddev.gmwcs.graph;

import ru.ifmo.ctddev.gmwcs.Signals;

import java.io.*;
import java.text.ParseException;
import java.util.*;

public class GraphIO {
    private File nodeIn;
    private File edgeIn;
    private File signalIn;
    private Map<String, Node> nodeNames;
    private Map<Unit, String> unitMap;
    private Signals signals;
    private Map<String, Integer> signalNames;

    public GraphIO(File nodeIn, File edgeIn, File signalIn) {
        this.nodeIn = nodeIn;
        this.edgeIn = edgeIn;
        this.signalIn = signalIn;
        signals = new Signals();
        nodeNames = new LinkedHashMap<>();
        unitMap = new HashMap<>();
        signalNames = new HashMap<>();
    }

    public Graph read() throws IOException, ParseException {
        try (LineNumberReader nodes = new LineNumberReader(new FileReader(nodeIn));
             LineNumberReader edges = new LineNumberReader(new FileReader(edgeIn));
             LineNumberReader signalsReader = new LineNumberReader(new FileReader(signalIn))) {
            Graph graph = new Graph();
            parseNodes(nodes, graph);
            parseEdges(edges, graph);
            parseSignals(signalsReader);
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
            try {
                Node vertex = new Node(cnt++, 0);
                if (nodeNames.containsKey(node)) {
                    throw new ParseException("Duplicate node " + node, 0);
                }
                nodeNames.put(node, vertex);
                graph.addVertex(vertex);
                processSignals(vertex, tokenizer);
                unitMap.put(vertex, node);
            } catch (ParseException e) {
                throw new ParseException(e.getMessage() + "node file, line", reader.getLineNumber());
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
            try {
                if (!nodeNames.containsKey(first) || !nodeNames.containsKey(second)) {
                    throw new ParseException("There's no such vertex in edge list at line", reader.getLineNumber());
                }
                Edge edge = new Edge(cnt++, 0);
                Node from = nodeNames.get(first);
                Node to = nodeNames.get(second);
                graph.addEdge(from, to, edge);
                unitMap.put(edge, first + "\t" + second);
                processSignals(edge, tokenizer);
            } catch (ParseException e) {
                throw new ParseException(e.getMessage() + "edge file, line", reader.getLineNumber());
            }
        }
    }

    private void processSignals(Unit unit, StringTokenizer tokenizer) throws ParseException {
        List<String> tokens = new ArrayList<>();
        while (tokenizer.hasMoreTokens()) {
            tokens.add(tokenizer.nextToken());
        }
        if (!tokens.isEmpty()) {
            for (String token : tokens) {
                if (signalNames.containsKey(token)) {
                    int signal = signalNames.get(token);
                    signals.add(unit, signal);
                } else {
                    signalNames.put(token, signals.add(unit));
                }
            }
        } else {
            throw new ParseException("Expected signal name: ", 0);
        }
    }


    private void parseSignals(LineNumberReader reader) throws IOException, ParseException {
        String line;
        try {
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#")) {
                    continue;
                }
                StringTokenizer tokenizer = new StringTokenizer(line);
                if (!tokenizer.hasMoreTokens()) {
                    continue;
                }
                String signal = tokenizer.nextToken();
                if (!tokenizer.hasMoreTokens()) {
                    throw new ParseException("Expected weight of signal at line", reader.getLineNumber());
                }
                double weight = Double.parseDouble(tokenizer.nextToken());
                if (!signalNames.containsKey(signal)) {
                    throw new ParseException("Signal " + signal +
                            "doesn't appear in node/edge files", reader.getLineNumber());
                }
                int set = signalNames.get(signal);
                signals.setWeight(set, weight);
                for (Unit u : signals.set(set)) {
                    u.setWeight(u.getWeight() + weight);
                }
            }
        } catch (NumberFormatException e) {
            throw new ParseException("Wrong format of weight of signal at line", reader.getLineNumber());
        }
    }

    public void write(List<Unit> units) throws IOException {
        if (units == null) {
            units = new ArrayList<>();
        }
        try (PrintWriter nodeWriter = new PrintWriter(nodeIn + ".out");
             PrintWriter edgeWriter = new PrintWriter(edgeIn + ".out")) {
            for (Unit unit : units) {
                if (!unitMap.containsKey(unit)) {
                    throw new IllegalStateException();
                }
                if (unit instanceof Node) {
                    nodeWriter.println(unitMap.get(unit));
                } else {
                    edgeWriter.println(unitMap.get(unit));
                }
            }
        }
    }

    public Signals getSignals() {
        return signals;
    }
}