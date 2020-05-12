package ru.itmo.ctlab.sgmwcs.solver;

import ru.itmo.ctlab.sgmwcs.Signals;
import ru.itmo.ctlab.sgmwcs.TimeLimit;
import ru.itmo.ctlab.sgmwcs.graph.*;

import java.util.*;

/**
 * Created by Nikolay Poperechnyi on 03.06.19.
 */
public class BlockSolver implements Solver {
    @Override
    public List<Unit> solve(Graph graph, Signals signals) throws SolverException {
        new Preprocessor(graph, signals, 4, 2).preprocess(2);
        for (Node v : new HashSet<>(graph.vertexSet())) {
            if (graph.neighborListOf(v).isEmpty()) {
                graph.removeVertex(v);
            }
        }
        PriorityQueue<Set<Node>> result = new PriorityQueue<>(new ComponentSolver.SetComparator());
        result.addAll(graph.connectedSets());
        List<Unit> best = Collections.emptyList();
        while (!result.isEmpty()) {
            Graph subgraph = graph.subgraph(result.poll());
            final Blocks blocks = new Blocks(subgraph);
            List<Set<Node>> components = new ArrayList<>(blocks.components());
            components.sort(Comparator.comparingInt(c -> blocks.cutpointsOf(c).size()));
            Set<Unit> subunits = new HashSet<>(subgraph.vertexSet());
            subunits.addAll(subgraph.edgeSet());
            Signals sigs = new Signals(signals, subunits);
            for (Set<Node> c : components) {
                Set<Integer> repeatingSignals = new HashSet<>();
                Set<Node> cp = blocks.cutpointsOf(c);
                if (cp == null)
                    continue;
                if (cp.size() > 1)
                    break;
                Node p = cp.iterator().next();
                if (!subgraph.containsVertex(p))
                    continue;
                for (int s : sigs.positiveUnitSets(c)) {
                    for (Unit e : sigs.set(s)) {
                        if (e instanceof Edge) {
                            if (!c.contains(subgraph.getEdgeTarget((Edge) e)) ||
                                    !c.contains(subgraph.getEdgeSource((Edge) e))) {
                                repeatingSignals.add(s);
                            }
                        } else {
                            if (!c.contains((Node) e)) {
                                repeatingSignals.add(s);
                            }
                        }
                    }
                    if (repeatingSignals.size() > 0)
                        break;
                }
                if (repeatingSignals.size() > 0)
                    continue;
                Graph small = subgraph.subgraph(c);
                Set<Unit> subset = new HashSet<>(small.vertexSet());
                subset.addAll(small.edgeSet());
                Signals smallSig = new Signals(sigs, subset);
                RLTSolver rlt = new RLTSolver();
                rlt.setRoot(p);
                List<Unit> smallRes = rlt.solve(small, smallSig);
                Set<Integer> smallSignals = sigs.unitSets(smallRes);
                double smallScore = sigs.weightSum(smallSignals);
                subset.remove(p);
                // removeUnits(signals, subset);
                for (Node v : small.vertexSet()) {
                    if (v != p) {
                        subgraph.removeVertex(v);
                    }
                }
                if (smallScore > 0) {
                    int newSig = sigs.addSignal(smallScore);
                    sigs.remove(p);
                    sigs.add(p, newSig);
                }
            }
            Solver cs = new RLTSolver();
            cs.setLogLevel(2);
            List<Unit> sol = cs.solve(subgraph, sigs);
            if(sol != null && sigs.sum(sol) > signals.sum(best)) {
                best = sol;
            }
        }
        return best;
    }


    private void removeUnits(Signals signals, Set<Unit> toRemove) {
        for (Unit u : toRemove) {
            signals.remove(u);
        }
    }

    @Override
    public boolean isSolvedToOptimality() {
        return false;
    }

    @Override
    public TimeLimit getTimeLimit() {
        return null;
    }

    @Override
    public void setTimeLimit(TimeLimit tl) {

    }

    @Override
    public void setLogLevel(int logLevel) {

    }

    @Override
    public void setLB(double lb) {

    }

    @Override
    public double getLB() {
        return 0;
    }
}
