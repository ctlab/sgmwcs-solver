package ru.ifmo.ctddev.gmwcs;

import ilog.concert.IloException;
import ilog.cplex.IloCplex;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import ru.ifmo.ctddev.gmwcs.graph.Graph;
import ru.ifmo.ctddev.gmwcs.graph.GraphIO;
import ru.ifmo.ctddev.gmwcs.graph.Unit;
import ru.ifmo.ctddev.gmwcs.solver.ComponentSolver;
import ru.ifmo.ctddev.gmwcs.solver.SolverException;
import ru.ifmo.ctddev.gmwcs.solver.Utils;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.List;

import static java.util.Arrays.asList;

public class Main {
    public static final String VERSION = "0.9.2";


    static {
        try {
            new IloCplex();
        } catch (UnsatisfiedLinkError e) {
            System.exit(1);
        } catch (IloException ignored) {
        }
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
                ofType(Integer.class).defaultsTo(500);
        optionParser.acceptsAll(asList("p", "penalty"), "Penalty for each additional edge")
                .withRequiredArg().ofType(Double.class).defaultsTo(.0);
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
        if (edgePenalty < 0) {
            System.err.println("Edge penalty can't be negative");
            System.exit(1);
        }
        ComponentSolver solver = new ComponentSolver(threshold);
        solver.setThreadsNum(threads);
        solver.setTimeLimit(tl);
        solver.setEdgePenalty(edgePenalty);
        solver.setLogLevel(1);
        GraphIO graphIO = new GraphIO(nodeFile, edgeFile, signalFile);
        try {
            Graph graph = graphIO.read();
            Signals signals = graphIO.getSignals();
            List<Unit> units = solver.solve(graph, signals);
            System.out.println("Final score: " + Utils.sum(units, signals));
            if (solver.isSolvedToOptimality()) {
                System.out.println("SOLVED TO OPTIMALITY");
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
}
