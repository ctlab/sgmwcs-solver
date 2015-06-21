package ru.ifmo.ctddev.gmwcs;

import ilog.concert.IloException;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.jgrapht.UndirectedGraph;
import ru.ifmo.ctddev.gmwcs.graph.*;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.Collections;
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
        optionParser.acceptsAll(Collections.singletonList("b"), "Break symmetries");
        optionParser.acceptsAll(Collections.singletonList("f"),
                "Fraction of time allocated for the biggest bicomponent in each component")
                .withRequiredArg().ofType(Double.class).defaultsTo(0.8);
        optionParser.acceptsAll(asList("s", "silence"), "print only short description if size of bicomponent less " +
                "than <silence>")
                .withRequiredArg().ofType(Integer.class).defaultsTo(50);
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

    public static void main(String[] args) {
        OptionSet optionSet = null;
        try {
            optionSet = parseArgs(args);
        } catch (IOException e) {
            // We can't say anything. Error occurred while printing to stderr.
            System.exit(2);
        }
        int tl = (Integer) optionSet.valueOf("timelimit");
        int threadNum = (Integer) optionSet.valueOf("threads");
        int silence = (Integer) optionSet.valueOf("silence");
        double mainFraction = (Double) optionSet.valueOf("f");
        File nodeFile = new File((String) optionSet.valueOf("nodes"));
        File edgeFile = new File((String) optionSet.valueOf("edges"));
        Solver solver = new RLTSolver(optionSet.has("b"), silence);
        GraphIO graphIO = new SimpleIO(nodeFile, new File(nodeFile.toString() + ".out"),
                edgeFile, new File(edgeFile.toString() + ".out"));
        try {
            UndirectedGraph<Node, Edge> graph = graphIO.read();
            double timeLimit = tl <= 0 ? Double.POSITIVE_INFINITY : tl;
            List<Unit> units = solver.solve(graph, threadNum, timeLimit, mainFraction);
            graphIO.write(units);
        } catch (ParseException e) {
            System.err.println("Couldn't parse input files: " + e.getMessage() + " " + e.getErrorOffset());
        } catch (IloException e) {
            System.err.println("CPLEX error:" + e.getMessage());
        } catch (IOException e) {
            System.err.println("Error occurred while reading/writing input/output files");
        }
    }
}
