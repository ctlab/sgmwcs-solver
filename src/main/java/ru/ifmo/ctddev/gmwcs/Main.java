package ru.ifmo.ctddev.gmwcs;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.jgrapht.UndirectedGraph;
import ru.ifmo.ctddev.gmwcs.graph.*;
import ru.ifmo.ctddev.gmwcs.solver.ComponentSolver;
import ru.ifmo.ctddev.gmwcs.solver.RLTSolver;
import ru.ifmo.ctddev.gmwcs.solver.SolverException;
import ru.ifmo.ctddev.gmwcs.solver.Utils;

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
        optionParser.acceptsAll(asList("t", "timelimit"), "Timelimit in seconds (<= 0 - unlimited)")
                .withRequiredArg().ofType(Long.class).defaultsTo(0L);
        optionParser.acceptsAll(asList("s", "synonyms"), "Synonym list file").withRequiredArg();
        optionParser.acceptsAll(asList("a", "all"), "Write to out files at each found solution");
        optionParser.accepts("c", "Threshold for CPE solver").withRequiredArg().
                ofType(Integer.class).defaultsTo(500);
        optionParser.acceptsAll(asList("i", "ignore-negatives"), "Don't consider negative signals");
        optionParser.accepts("consider-cuts", "Number of considering vertices to find cuts").withRequiredArg().
                ofType(Integer.class).defaultsTo(Integer.MAX_VALUE);
        optionParser.accepts("max-cuts", "Maximum cuts per cplex iteration").withRequiredArg().ofType(Integer.class).
                defaultsTo(Integer.MAX_VALUE);
        if (optionSet.has("h")) {
            optionParser.printHelpOn(System.out);
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
        int threadsNum = (Integer) optionSet.valueOf("threads");
        File nodeFile = new File((String) optionSet.valueOf("nodes"));
        File edgeFile = new File((String) optionSet.valueOf("edges"));
        RLTSolver rltSolver = new RLTSolver();
        ComponentSolver solver = new ComponentSolver(threshold);
        solver.setTimeLimit(tl);
        rltSolver.setThreadsNum(threadsNum);
        rltSolver.setConsideringCuts((Integer) optionSet.valueOf("consider-cuts"));
        rltSolver.setMaxToAddCuts((Integer) optionSet.valueOf("max-cuts"));
        SimpleIO graphIO = new SimpleIO(nodeFile, new File(nodeFile.toString() + ".out"),
                edgeFile, new File(edgeFile.toString() + ".out"), optionSet.has("i"));
        LDSU<Unit> synonyms = new LDSU<>();
        if (optionSet.has("a")) {
            rltSolver.setCallback(new WritingCallback(graphIO));
        }
        try {
            UndirectedGraph<Node, Edge> graph = graphIO.read();
            if (optionSet.has("s")) {
                synonyms = graphIO.getSynonyms(new File((String) optionSet.valueOf("s")));
            } else {
                for (Node node : graph.vertexSet()) {
                    synonyms.add(node);
                }
                for (Edge edge : graph.edgeSet()) {
                    synonyms.add(edge);
                }
            }
            List<Unit> units = solver.solve(graph, synonyms);
            System.out.println("Final score: " + Utils.sum(units, synonyms));
            if (solver.isSolvedToOptimality()) {
                System.out.println("SOLVED TO OPTIMALITY");
            }
            graphIO.write(units);
        } catch (ParseException e) {
            System.err.println("Couldn't parse input files: " + e.getMessage() + " " + e.getErrorOffset());
        } catch (SolverException e) {
            System.err.println("Error occured while solving:" + e.getMessage());
        } catch (IOException e) {
            System.err.println("Error occurred while reading/writing input/output files");
        }
    }

    private static class WritingCallback extends RLTSolver.SolutionCallback {
        private GraphIO graphIO;

        public WritingCallback(GraphIO graphIO) {
            this.graphIO = graphIO;
        }

        @Override
        public void main(List<Unit> solution) {
            try {
                graphIO.write(solution);
            } catch (IOException e) {
                System.err.println("Solution couldn't be written to disk. Input output error occured.");
            }
        }
    }
}
