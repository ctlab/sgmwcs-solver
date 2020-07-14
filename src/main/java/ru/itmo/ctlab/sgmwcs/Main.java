package ru.itmo.ctlab.sgmwcs;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import ru.itmo.ctlab.sgmwcs.graph.*;
import ru.itmo.ctlab.sgmwcs.solver.ComponentSolver;
import ru.itmo.ctlab.sgmwcs.solver.SolverException;
import ru.itmo.ctlab.sgmwcs.solver.Utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.ParseException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.Arrays.asList;

public class Main {
    public static final String VERSION = "0.9.9";

    static {
        /*try {
            new IloCplex();
        } catch (UnsatisfiedLinkError e) {
            System.exit(1);
        } catch (IloException ignored) {
        }*/
    }

    private static OptionSet parseArgs(String args[]) throws IOException {
        OptionParser optionParser = new OptionParser();
        optionParser.allowsUnrecognizedOptions();
        optionParser.acceptsAll(asList("h", "help"), "Print a short help message");
        optionParser.accepts("version");
        OptionSet optionSet = optionParser.parse(args);
        optionParser.acceptsAll(asList("n", "nodes"), "Node list file").withRequiredArg().required();
        optionParser.acceptsAll(asList("e", "edges"), "Edge list file").withRequiredArg().required();
        optionParser.acceptsAll(asList("s", "signals"), "Signals file").withRequiredArg().required();
        optionParser.acceptsAll(asList("m", "threads"), "Number of threads")
                .withRequiredArg().ofType(Integer.class).defaultsTo(1);
        optionParser.acceptsAll(asList("t", "timelimit"), "Timelimit in seconds (<= 0 - unlimited)")
                .withRequiredArg().ofType(Long.class).defaultsTo(0L);
        optionParser.accepts("c", "Threshold for CPE solver").withRequiredArg().
                ofType(Integer.class).defaultsTo(25);
        optionParser.acceptsAll(asList("p", "penalty"), "Penalty for each additional edge")
                .withRequiredArg().ofType(Double.class).defaultsTo(.0);
        optionParser.acceptsAll(asList("l", "log"), "Log level")
                .withRequiredArg().ofType(Integer.class).defaultsTo(0);
        optionParser.acceptsAll(asList("bm", "benchmark"), "Benchmark output file")
                .withOptionalArg().defaultsTo("");
        optionParser.acceptsAll(asList("pl", "preprocessing-level"), "Disable preprocessing")
                .withOptionalArg().ofType(Integer.class).defaultsTo(2);
        optionParser.acceptsAll(asList("f", "stats-file"), "Dump stats").withOptionalArg().ofType(String.class).defaultsTo("");
        optionParser.acceptsAll(Collections.singletonList("mst"), "Use primal heuristic only").withOptionalArg().ofType(Integer.class).defaultsTo(0);
        if (optionSet.has("h")) {
            optionParser.printHelpOn(System.out);
            System.exit(0);
        }
        if (optionSet.has("version")) {
            System.out.println("sgmwcs-solver version " + VERSION);
            System.exit(0);
        }
        try {
            optionSet = optionParser.parse(args);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.err.println();
            optionParser.printHelpOn(System.err);
            System.exit(1);
        }
        return optionSet;
    }

    public static void main(String[] args) {
        OptionSet optionSet = null;
        try {
            optionSet = parseArgs(args);
        } catch (IOException e) {
            // We can't say anything. Error occurred while printing to stderr.
            System.exit(2);
        }
        long timelimit = (Long) optionSet.valueOf("timelimit");
        int threshold = (Integer) optionSet.valueOf("c");
        TimeLimit tl = new TimeLimit(timelimit <= 0 ? Double.POSITIVE_INFINITY : timelimit);
        int threads = (Integer) optionSet.valueOf("m");
        File nodeFile = new File((String) optionSet.valueOf("nodes"));
        File edgeFile = new File((String) optionSet.valueOf("edges"));
        File signalFile = new File((String) optionSet.valueOf("signals"));
        double edgePenalty = (Double) optionSet.valueOf("p");
        int logLevel = (Integer) optionSet.valueOf("l");
        int preprocessLevel = (Integer) optionSet.valueOf("pl");
        int heuristicOnly = (Integer) optionSet.valueOf("mst");
        String bmOutput = (String) optionSet.valueOf("bm");
        String statsFile = (String) optionSet.valueOf("f");
        if (edgePenalty < 0) {
            System.err.println("Edge penalty can't be negative");
            System.exit(1);
        }
        // Solver solver = new BlockSolver();
        ComponentSolver solver = new ComponentSolver(threshold, edgePenalty > 0);
        solver.setThreadsNum(threads);
        solver.setTimeLimit(tl);
        solver.setLogLevel(logLevel);
        solver.setPreprocessingLevel(preprocessLevel);
        solver.setCplexOff(heuristicOnly > 0);
        GraphIO graphIO = new GraphIO(nodeFile, edgeFile, signalFile);
        try {
            long before = System.currentTimeMillis();
            Graph graph = graphIO.read();
            System.out.println("Graph with " +
                    graph.edgeSet().size() + " edges and " +
                    graph.vertexSet().size() + " nodes");
            Signals signals = graphIO.getSignals();
            /*if (edgePenalty > 0) {
                signals.addEdgePenalties(-edgePenalty);
            }*/
            if (!bmOutput.equals("")) {
                new Benchmark(graph, signals, bmOutput).run();
                return;
            }
            List<Unit> units = solver.solve(graph, signals);
            long now = System.currentTimeMillis();
            if (solver.isSolvedToOptimality()) {
                System.out.println("SOLVED TO OPTIMALITY");
            }
            double sum = Utils.sum(units, signals);
            System.out.println(sum);
            if (units != null)
                System.out.println(units.size());
            long timeConsumed = now - before;
            System.out.println("time:" + (now - before));
            Set<Edge> edges = new HashSet<>();
            Set<Node> nodes = new HashSet<>();
            if (logLevel >= 1 && units != null) {
                for (Unit unit : units) {
                    if (unit instanceof Edge) {
                        edges.add((Edge) unit);
                    } else {
                        nodes.add((Node) unit);
                    }
                }
                Graph solGraph = graph.subgraph(nodes, edges);
                if (logLevel == 2)
                    new GraphPrinter(solGraph, signals).toTSV("nodes-sol.tsv", "edges-sol.tsv");
                printStats(solver.isSolvedToOptimality() ? 1 : 0, solver.preprocessedNodes(), solver.preprocessedEdges(),
                        solGraph, timeConsumed, statsFile,
                        nodeFile.getAbsolutePath(), edgeFile.getAbsolutePath(), signalFile.getAbsolutePath());
            }
            graphIO.write(units);
        } catch (ParseException e) {
            System.err.println("Couldn't parse input files: " + e.getMessage() + " " + e.getErrorOffset());
        } catch (SolverException e) {
            System.err.println("Error occurred while solving:" + e.getMessage());
        } catch (IOException e) {
            System.err.println("Error occurred while reading/writing input/output files");
        }
    }

    private static void printStats(int isOpt, int prepNodes, int prepEdges, Graph solGraph,
                                   long timeConsumed, String fileName,
                                   String nodes, String edges, String signals) {
        try (PrintWriter pw = new PrintWriter(fileName)) {
            String header = "isOpt\tVPrep\tEPrep\ttime\tedges\tnodes\tnodefile\tedgefile\tsigfile\tversion\n";
            String out = isOpt + "\t" + prepNodes + "\t" + prepEdges + "\t" + timeConsumed + "\t" +
                    solGraph.edgeSet().size() + "\t" +
                    solGraph.vertexSet().size() + "\t" + nodes + "\t" + edges + "\t" + signals + "\t" + VERSION + "\n";
            pw.write(header);
            pw.write(out);
        } catch (IOException e) {
            System.err.println("Failed to write stats");
        }
    }
}
