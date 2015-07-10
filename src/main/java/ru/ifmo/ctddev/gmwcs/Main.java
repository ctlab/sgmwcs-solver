package ru.ifmo.ctddev.gmwcs;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.jgrapht.UndirectedGraph;
import ru.ifmo.ctddev.gmwcs.graph.*;
import ru.ifmo.ctddev.gmwcs.solver.BicomponentSolver;
import ru.ifmo.ctddev.gmwcs.solver.RLTSolver;
import ru.ifmo.ctddev.gmwcs.solver.SolverException;

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
        optionParser.acceptsAll(asList("t", "timelimit"), "Timelimit in seconds (<= 0 - unlimited)")
                .withRequiredArg().ofType(Long.class).defaultsTo(0L);
        optionParser.acceptsAll(asList("u", "unrooted"), "Maximum share of time allocated for solving unrooted parts")
                .withRequiredArg().ofType(Double.class).defaultsTo(1.0 / 3.0);
        optionParser.acceptsAll(asList("r", "rooted"), "Maximum share of time allocated for solving rooted parts")
                .withRequiredArg().ofType(Double.class).defaultsTo(1.0 / 3.0);
        optionParser.acceptsAll(Collections.singletonList("b"), "Break symmetries");
        if (optionSet.has("h")) {
            optionParser.printHelpOn(System.out);
            System.exit(0);
        }
        try {
            optionSet = optionParser.parse(args);
            double ush = (Double) optionSet.valueOf("u");
            double rsh = (Double) optionSet.valueOf("r");
            if (ush < 0.0 || ush > 1.0 || rsh < 0.0 || rsh > 1.0) {
                System.err.println("Share must b in range [0,1]");
                System.exit(1);
            }
            if (rsh + ush > 1.0) {
                System.err.println("Sum of shares of rooted and unrooted parts must be <= 1.0");
                System.exit(1);
            }
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
        TimeLimit tl = new TimeLimit(timelimit <= 0 ? Double.POSITIVE_INFINITY : timelimit);
        double rsh = (Double) optionSet.valueOf("r");
        double ush = (Double) optionSet.valueOf("u");
        TimeLimit biggestTL = tl.subLimit(1.0 - ush);
        int threadsNum = (Integer) optionSet.valueOf("threads");
        File nodeFile = new File((String) optionSet.valueOf("nodes"));
        File edgeFile = new File((String) optionSet.valueOf("edges"));
        RLTSolver rltSolver = new RLTSolver(optionSet.has("b"));
        rltSolver.setThreadsNum(threadsNum);
        BicomponentSolver solver = new BicomponentSolver(rltSolver);
        solver.setUnrootedTL(tl);
        solver.setRootedTL(biggestTL.subLimit(ush == 1.0 ? 0 : rsh / (1.0 - ush)));
        solver.setTLForBiggest(biggestTL);
        GraphIO graphIO = new SimpleIO(nodeFile, new File(nodeFile.toString() + ".out"),
                edgeFile, new File(edgeFile.toString() + ".out"));
        try {
            UndirectedGraph<Node, Edge> graph = graphIO.read();
            List<Unit> units = solver.solve(graph);
            graphIO.write(units);
        } catch (ParseException e) {
            System.err.println("Couldn't parse input files: " + e.getMessage() + " " + e.getErrorOffset());
        } catch (SolverException e) {
            System.err.println("Error occur while solving:" + e.getMessage());
        } catch (IOException e) {
            System.err.println("Error occurred while reading/writing input/output files");
        }
    }
}
