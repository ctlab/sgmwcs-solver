package ru.itmo.ctlab.sgmwcs;

import ru.itmo.ctlab.sgmwcs.graph.Graph;
import ru.itmo.ctlab.sgmwcs.solver.ComponentSolver;
import ru.itmo.ctlab.sgmwcs.solver.Preprocessor;
import ru.itmo.ctlab.sgmwcs.solver.SolverException;
import ru.itmo.ctlab.sgmwcs.solver.Utils;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Created by Nikolay Poperechnyi on 02.09.19.
 */
public class Benchmark {

    private final Graph graph;
    private final Signals signals;
    private final String outPath;

    public Benchmark(Graph graph, Signals signals, String outPath) {
        this.graph = graph;
        this.signals = signals;
        this.outPath = outPath;
    }

    void run() throws IOException {
        try (PrintWriter pw = new PrintWriter(outPath)) {
            withPreprocessing(pw);
            withoutPreprocessing(pw);
        }
    }

    private void withPreprocessing(PrintWriter pw) throws IOException {
        Graph g = new Graph();
        Signals s = new Signals();
        Utils.copy(graph, signals, g, s);
        long timeBefore = System.currentTimeMillis();
        new Preprocessor(g, s).preprocess();
        double prepTime = (System.currentTimeMillis() - timeBefore) / 1000;
        solve(g, s);
        double mipTime = (System.currentTimeMillis() - timeBefore) / 1000 - prepTime;
        pw.print(prepTime + "\t" + mipTime);
    }

    private void withoutPreprocessing(PrintWriter pw) throws IOException {
        Graph g = new Graph();
        Signals s = new Signals();
        Utils.copy(graph, signals, g, s);
        long timeBefore = System.currentTimeMillis();
        solve(g, s);
        double mipTime = (System.currentTimeMillis() - timeBefore) / 1000;
        pw.println("\t" + mipTime);
    }

    private void solve(Graph g, Signals s) {
        try {
            ComponentSolver sol = new ComponentSolver(50, false, false);
            sol.setTimeLimit(new TimeLimit(1000));
            sol.solve(g, s);
        } catch (SolverException e) {
            e.printStackTrace();
        }
    }

}
