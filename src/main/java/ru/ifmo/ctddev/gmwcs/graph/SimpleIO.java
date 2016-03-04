package ru.ifmo.ctddev.gmwcs.graph;

import org.jgrapht.UndirectedGraph;
import org.jgrapht.graph.Multigraph;
import ru.ifmo.ctddev.gmwcs.LDSU;
import ru.ifmo.ctddev.gmwcs.Pair;

import java.io.*;
import java.text.ParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SimpleIO implements GraphIO {
    private File nodeIn;
    private File nodeOut;
    private File edgeIn;
    private File edgeOut;
    private List<String> nodeList;
    private List<Pair<String, String>> edgeList;
    private Map<String, Node> nodeMap;
    private Map<String, Map<String, Edge>> edgeMap;
    private boolean ignoreNegatives;

    public SimpleIO(File nodeIn, File nodeOut, File edgeIn, File edgeOut, boolean ignore) {
        this.nodeIn = nodeIn;
        this.edgeOut = edgeOut;
        this.edgeIn = edgeIn;
        this.nodeOut = nodeOut;
        nodeMap = new LinkedHashMap<>();
        nodeList = new ArrayList<>();
        edgeList = new ArrayList<>();
        edgeMap = new LinkedHashMap<>();
        ignoreNegatives = ignore;
    }

    @Override
    public UndirectedGraph<Node, Edge> read() throws FileNotFoundException, ParseException {
        try (Scanner nodes = new Scanner(new BufferedReader(new FileReader(nodeIn)));
             Scanner edges = new Scanner(new BufferedReader(new FileReader(edgeIn)))) {
            UndirectedGraph<Node, Edge> graph = new Multigraph<>(Edge.class);
            parseNodes(nodes, graph);
            parseEdges(edges, graph);
            return graph;
        }
    }

    private void parseNodes(Scanner nodes, UndirectedGraph<Node, Edge> graph) throws ParseException {
        int lnum = 0;
        while (nodes.hasNextLine()) {
            lnum++;
            String line = nodes.nextLine();
            if (line.startsWith("#")) {
                continue;
            }
            StringTokenizer tokenizer = new StringTokenizer(line);
            if (!tokenizer.hasMoreTokens()) {
                continue;
            }
            String node = tokenizer.nextToken();
            nodeList.add(node);
            if (!tokenizer.hasMoreTokens()) {
                throw new ParseException("Expected weight of node in line", lnum);
            }
            String weightStr = tokenizer.nextToken();
            try {
                double weight = Double.parseDouble(weightStr);
                Node vertex = new Node(lnum, weight);
                if (nodeMap.containsKey(node)) {
                    throw new ParseException("Duplicate node " + node, 0);
                }
                nodeMap.put(node, vertex);
                graph.addVertex(vertex);
            } catch (NumberFormatException e) {
                throw new ParseException("Expected floating point value of node weight in line", lnum);
            }
        }
    }

    private void parseEdges(Scanner edges, UndirectedGraph<Node, Edge> graph) throws ParseException {
        int lnum = 0;
        while (edges.hasNextLine()) {
            lnum++;
            String line = edges.nextLine();
            if (line.startsWith("#")) {
                continue;
            }
            StringTokenizer tokenizer = new StringTokenizer(line);
            if (!tokenizer.hasMoreTokens()) {
                continue;
            }
            String first = tokenizer.nextToken();
            if (!tokenizer.hasMoreTokens()) {
                throw new ParseException("Expected name of second node in edge list in line", lnum);
            }
            String second = tokenizer.nextToken();
            if (!tokenizer.hasMoreTokens()) {
                throw new ParseException("Expected weight of edge in line", lnum);
            }
            try {
                double weight = Double.parseDouble(tokenizer.nextToken());
                if (!nodeMap.containsKey(first) || !nodeMap.containsKey(second)) {
                    throw new ParseException("There's no such vertex in edge list in line", lnum);
                }
                Edge edge = new Edge(lnum, weight);
                Node from = nodeMap.get(first);
                Node to = nodeMap.get(second);
                if (edgeMap.get(first) != null && edgeMap.get(first).get(second) != null ||
                        edgeMap.get(second) != null && edgeMap.get(second).get(first) != null) {
                    throw new ParseException("Duplicate edge " + first + " -- " + second, 0);
                }
                graph.addEdge(from, to, edge);
                edgeList.add(new Pair<>(first, second));
                if (!edgeMap.containsKey(first)) {
                    edgeMap.put(first, new LinkedHashMap<>());
                }
                edgeMap.get(first).put(second, edge);
            } catch (NumberFormatException e) {
                throw new ParseException("Expected floating point value of edge in line", lnum);
            }
        }
    }

    @Override
    public void write(List<Unit> units) throws IOException {
        Set<Unit> unitSet = new LinkedHashSet<>();
        if (units == null) {
            units = new ArrayList<>();
        }
        unitSet.addAll(units);
        writeNodes(unitSet);
        writeEdges(unitSet);
    }

    @Override
    public Node getNode(String name) {
        return nodeMap.get(name);
    }

    private void writeEdges(Set<Unit> units) throws IOException {
        try (Writer writer = new BufferedWriter(new FileWriter(edgeOut))) {
            for (Pair<String, String> p : edgeList) {
                Edge edge = edgeMap.get(p.first).get(p.second);
                writer.write(p.first + "\t" + p.second + "\t" + (units.contains(edge) ? edge.getWeight() : "n/a"));
                writer.write("\n");
            }
        }
    }

    private void writeNodes(Set<Unit> units) throws IOException {
        try (Writer writer = new BufferedWriter(new FileWriter(nodeOut))) {
            for (String name : nodeList) {
                Node node = nodeMap.get(name);
                writer.write(name + "\t" + (units.contains(node) ? node.getWeight() : "n/a"));
                writer.write("\n");
            }
        }
    }

    public LDSU<Unit> getSynonyms(File s) throws FileNotFoundException, ParseException {
        LDSU<Unit> synonyms = new LDSU<>();
        nodeMap.values().forEach(synonyms::add);
        for (Pair<String, String> p : edgeList) {
            Edge edge = edgeMap.get(p.first).get(p.second);
            synonyms.add(edge);
        }
        try (Scanner sc = new Scanner(new BufferedReader(new FileReader(s)))) {
            while (sc.hasNextLine()) {
                String line = sc.nextLine();
                List<Unit> eq = new ArrayList<>();
                line = getEdges(line, eq);
                getNodes(line, eq);
                if (eq.isEmpty()) {
                    continue;
                }
                Unit main = eq.get(0);
                if (ignoreNegatives && main.getWeight() <= 0) {
                    continue;
                }
                for (int i = 1; i < eq.size(); i++) {
                    synonyms.merge(main, eq.get(i));
                }
            }
        }
        return synonyms;
    }

    private String getEdges(String line, List<Unit> eq) throws ParseException {
        String edgeRegex = "([^\\s\\-]+)\\s*--\\s*([^\\s\\-]+)";
        Pattern pattern = Pattern.compile(edgeRegex);
        Matcher matcher = pattern.matcher(line);
        while (matcher.find()) {
            String from = matcher.group(1);
            String to = matcher.group(2);
            if (from == null) {
                throw new ParseException("No such node " + from + " but it was occurred in synonym file", 0);
            }
            if (to == null) {
                throw new ParseException("No such node " + to + " but it was occurred in synonym file", 0);
            }
            Edge edge;
            if (edgeMap.get(from).get(to) != null) {
                edge = edgeMap.get(from).get(to);
            } else {
                edge = edgeMap.get(to).get(from);
            }
            if (edge == null) {
                throw new ParseException("No such edge " + from + " -- " + to, 0);
            }
            eq.add(edge);
        }
        line = line.replaceAll(edgeRegex, "");
        return line;
    }

    private void getNodes(String line, List<Unit> eq) throws ParseException {
        Pattern pattern = Pattern.compile("[^\\s]+");
        Matcher matcher = pattern.matcher(line);
        while (matcher.find()) {
            String name = matcher.group();
            Node node = getNode(name);
            if (node == null) {
                throw new ParseException("No such node " + name + " but it was occured in synonym file", 0);
            }
            eq.add(node);
        }
    }
}
