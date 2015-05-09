package ru.ifmo.ctddev.gmwcs;

import ilog.concert.IloException;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.jgrapht.UndirectedGraph;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.List;

import static java.util.Arrays.asList;

public class Main {
    public static OptionSet parseArgs(String args[]) throws IOException {
        OptionParser optionParser = new OptionParser();
        optionParser.allowsUnrecognizedOptions();
        optionParser.acceptsAll(asList("h", "help"), "Print a short help message");
        OptionSet optionSet = optionParser.parse(args);
        optionParser.acceptsAll(asList("n", "nodes"), "Node list file").withRequiredArg().required();
        optionParser.acceptsAll(asList("e", "edges"), "Edge list file").withRequiredArg().required();
        optionParser.acceptsAll(asList("m", "threads"), "Number of threads").withRequiredArg()
                .ofType(Integer.class).defaultsTo(1);
        optionParser.acceptsAll(asList("t", "timelimit"), "Time limit in seconds, 0 - unlimited").withRequiredArg()
                .ofType(Integer.class).defaultsTo(0);
        optionParser.acceptsAll(asList("b"), "Break symmetries");
        if (optionSet.has("h")) {
            optionParser.printHelpOn(System.out);
            System.exit(0);
        }
        try {
            optionSet = optionParser.parse(args);
        } catch (OptionException e) {
            System.err.println(e.getMessage());
            System.err.println();
            optionParser.printHelpOn(System.err);
            System.exit(1);
        }
        return optionSet;
    }

    public static void main(String[] args) throws IOException {
        OptionSet optionSet = parseArgs(args);
        int tl = (Integer) optionSet.valueOf("timelimit");
        int threadNum = (Integer) optionSet.valueOf("threads");
        File nodeFile = new File((String) optionSet.valueOf("nodes"));
        File edgeFile = new File((String) optionSet.valueOf("edges"));
        Solver solver = new RLTSolver(optionSet.has("b"));
        GraphIO graphIO = new SimpleIO(nodeFile, new File(nodeFile.toString() + ".out"),
                edgeFile, new File(edgeFile.toString() + ".out"));
        try {
            UndirectedGraph<Node, Edge> graph = graphIO.read();
            List<Unit> units = solver.solve(graph, threadNum, tl == 0 ? -1 : tl);
            graphIO.write(units);
        } catch (ParseException e) {
            System.err.println("Couldn't parse input files: " + e.getMessage() + " " + e.getErrorOffset());
        } catch (IloException e) {
            System.err.println("CPLEX error:" + e.getMessage());
        }
    }
}
