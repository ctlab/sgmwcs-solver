package ru.ifmo.ctddev.gmwcs;

import ilog.concert.IloException;
import org.jgrapht.UndirectedGraph;
import org.jgrapht.alg.BiconnectivityInspector;
import org.jgrapht.alg.ConnectivityInspector;
import org.jgrapht.graph.SimpleGraph;
import org.jgrapht.graph.UndirectedSubgraph;
import ru.ifmo.ctddev.gmwcs.graph.Edge;
import ru.ifmo.ctddev.gmwcs.graph.Node;
import ru.ifmo.ctddev.gmwcs.graph.Unit;

import java.util.*;

public abstract class Solver {
    protected int threads;
    private double mainFraction;

    protected abstract List<Unit> solveBiComponent(UndirectedGraph<Node, Edge> graph, Node root, double tl) throws IloException;

    public List<Unit> solve(UndirectedGraph<Node, Edge> graph, int threads,
                            double tl, double mainFraction) throws IloException {
        List<Unit> best = null;
        this.threads = threads;
        this.mainFraction = mainFraction;
        double maxWeight = 0.0;
        ConnectivityInspector<Node, Edge> inspector = new ConnectivityInspector<>(graph);
        TimeLimit limit = new TimeLimit(tl);
        int nodeRemains = graph.vertexSet().size();
        List<Set<Node>> connectedSets = new ArrayList<>();
        connectedSets.addAll(inspector.connectedSets());
        Collections.sort(connectedSets, new SetComparator<Node>());
        int i = 1;
        for (Set<Node> component : connectedSets) {
            double fraction = (double) component.size() / nodeRemains;
            nodeRemains -= component.size();
            Set<Edge> edges = new LinkedHashSet<>();
            for (Edge edge : graph.edgeSet()) {
                if (component.contains(graph.getEdgeSource(edge))) {
                    edges.add(edge);
                }
            }
            UndirectedGraph<Node, Edge> subgraph = new UndirectedSubgraph<>(graph, component, edges);
            System.out.println("Considering component " + (i++) + "/" + connectedSets.size() + ":");
            List<Unit> solution = solveComponent(clone(subgraph), limit.subLimit(fraction));
            if (sum(solution) > maxWeight) {
                maxWeight = sum(solution);
                best = solution;
            }
        }
        checkConnectivity(graph, best);
        return best;
    }

    private List<Unit> solveComponent(UndirectedGraph<Node, Edge> graph, TimeLimit limit) throws IloException {
        if (graph.vertexSet().size() == 1) {
            return Arrays.<Unit>asList(graph.vertexSet().iterator().next());
        }
        TimeLimit tlFotSmall = limit.subLimit(1 - mainFraction);
        LeafBiComponentIterator iterator = new LeafBiComponentIterator(graph);
        int nodeRemains = iterator.nodesToProcess();
        List<Unit> bestSolution = null;
        int i = 1;
        int all = iterator.bicomponentsCount();
        while (iterator.hasNext()) {
            Pair<Node, Set<Node>> component = iterator.next();
            Node cutpoint = component.first;
            Set<Node> nodes = component.second;
            TimeLimit tl = tlFotSmall.subLimit((double) nodes.size() / nodeRemains);
            nodeRemains -= nodes.size();
            UndirectedGraph<Node, Edge> subgraph = subgraph(graph, nodes);
            System.out.print("Considering bicomponent " + i + "/" + all + ": unrooted(");
            List<Unit> unrooted = solveBiComponent(subgraph, null, tl.subLimit(0.5));
            if (sum(unrooted) > sum(bestSolution)) {
                bestSolution = getResult(unrooted);
            }
            List<Unit> rooted;
            System.out.print("), rooted(");
            if (unrooted != null && unrooted.contains(cutpoint)) {
                System.out.println("=unrooted)");
                rooted = unrooted;
            } else {
                rooted = solveBiComponent(subgraph, cutpoint, tl);
                System.out.println(")");
            }
            collapse(graph, rooted, nodes, cutpoint);
            i++;
        }
        System.out.print("Considering bicomponent " + all + "/" + all + ": ");
        List<Unit> biggest = solveBiComponent(graph, null, limit);
        System.out.println(")");
        if (sum(biggest) > sum(bestSolution)) {
            bestSolution = getResult(biggest);
        }
        return bestSolution;
    }

    private List<Unit> solveBiComponent(UndirectedGraph<Node, Edge> graph, Node root, TimeLimit tl) throws IloException {
        long timeBefore = System.currentTimeMillis();
        List<Unit> result = solveBiComponent(graph, root, tl.getRemainingTime());
        double duration = (System.currentTimeMillis() - timeBefore) / 1000.0;
        System.out.format("time: %.2f/%.2f", duration, tl.getRemainingTime() + 0.005);
        tl.spend(Math.min(tl.getRemainingTime(), duration));
        return result;
    }

    private void collapse(UndirectedGraph<Node, Edge> graph, List<Unit> sol, Set<Node> component, Node cutpoint) {
        Set<Node> toRemove = new LinkedHashSet<>();
        toRemove.addAll(component);
        for (Node node : toRemove) {
            if (node != cutpoint) {
                graph.removeVertex(node);
            }
        }
        if (sol != null) {
            for (Unit unit : sol) {
                if (unit != cutpoint) {
                    cutpoint.addAllAbsorbedUnits(unit.getAbsorbedUnits());
                    cutpoint.setWeight(cutpoint.getWeight() + unit.getWeight());
                }
            }
        }
    }

    private UndirectedGraph<Node, Edge> subgraph(UndirectedGraph<Node, Edge> source, Set<Node> nodes) {
        Set<Edge> edges = new LinkedHashSet<>();
        for (Edge edge : source.edgeSet()) {
            if (nodes.contains(source.getEdgeSource(edge)) && nodes.contains(source.getEdgeTarget(edge))) {
                edges.add(edge);
            }
        }
        return new UndirectedSubgraph<>(source, nodes, edges);
    }

    private void checkConnectivity(UndirectedGraph<Node, Edge> graph, List<Unit> result) {
        Set<Node> nodes = new LinkedHashSet<>();
        Set<Edge> edges = new LinkedHashSet<>();
        if (result == null) {
            return;
        }
        for (Unit unit : result) {
            if (unit instanceof Node) {
                nodes.add((Node) unit);
            } else {
                edges.add((Edge) unit);
            }
        }
        UndirectedGraph<Node, Edge> subgraph = new UndirectedSubgraph<>(graph, nodes, edges);
        ConnectivityInspector<Node, Edge> inspector = new ConnectivityInspector<>(subgraph);
        if (!inspector.isGraphConnected()) {
            throw new IllegalStateException();
        }
    }

    private List<Unit> getResult(List<Unit> units) {
        if (units == null) {
            return null;
        }
        List<Unit> result = new ArrayList<>();
        for (Unit unit : units) {
            result.addAll(unit.getAbsorbedUnits());
        }
        return result;
    }

    private UndirectedGraph<Node, Edge> clone(UndirectedGraph<Node, Edge> source) {
        UndirectedGraph<Node, Edge> graph = new SimpleGraph<>(Edge.class);
        Map<Node, Node> old2new = new LinkedHashMap<>();
        for (Node node : source.vertexSet()) {
            Node newNode = new Node(node.getNum(), node.getWeight());
            newNode.addAbsorbedUnit(node);
            old2new.put(node, newNode);
            graph.addVertex(newNode);
        }
        for (Edge edge : source.edgeSet()) {
            Node from = old2new.get(source.getEdgeSource(edge));
            Node to = old2new.get(source.getEdgeTarget(edge));
            Edge newEdge = new Edge(edge.getNum(), edge.getWeight());
            newEdge.addAbsorbedUnit(edge);
            graph.addEdge(from, to, newEdge);
        }
        return graph;
    }

    private double sum(List<Unit> units) {
        if (units == null) {
            return 0;
        }
        double res = 0;
        for (Unit unit : units) {
            res += unit.getWeight();
        }
        return res;
    }

    private class LeafBiComponentIterator implements Iterator<Pair<Node, Set<Node>>> {
        private Map<Set<Node>, List<Node>> componentCutpoints;
        private Map<Node, List<Set<Node>>> cutpointComponents;
        private PriorityQueue<Set<Node>> leaves;

        public LeafBiComponentIterator(UndirectedGraph<Node, Edge> graph) {
            BiconnectivityInspector<Node, Edge> inspector = new BiconnectivityInspector<>(graph);
            Set<Set<Node>> components = inspector.getBiconnectedVertexComponents();
            Set<Node> cutpoints = inspector.getCutpoints();
            componentCutpoints = new LinkedHashMap<>();
            cutpointComponents = new LinkedHashMap<>();
            leaves = new PriorityQueue<>(new SetComparator<Node>());
            for (Set<Node> component : components) {
                componentCutpoints.put(component, new ArrayList<Node>());
                for (Node node : component) {
                    if (cutpoints.contains(node)) {
                        componentCutpoints.get(component).add(node);
                        if (!cutpointComponents.containsKey(node)) {
                            cutpointComponents.put(node, new ArrayList<Set<Node>>());
                        }
                        cutpointComponents.get(node).add(component);
                    }
                }
                if (componentCutpoints.get(component).size() <= 1) {
                    leaves.add(component);
                }
            }
        }

        public int bicomponentsCount() {
            return componentCutpoints.size();
        }

        public int nodesToProcess() {
            int result = 0;
            int max = -1;
            for (Set<Node> component : componentCutpoints.keySet()) {
                result += component.size();
                if (component.size() > max) {
                    max = component.size();
                }
            }
            result -= max;
            return result;
        }

        @Override
        public boolean hasNext() {
            return leaves.size() > 1;
        }

        @Override
        public Pair<Node, Set<Node>> next() {
            Set<Node> smallest = leaves.poll();
            Node cutpoint = componentCutpoints.get(smallest).iterator().next();
            componentCutpoints.remove(smallest);
            cutpointComponents.get(cutpoint).remove(smallest);
            if (cutpointComponents.get(cutpoint).size() == 1) {
                for (Set<Node> updated : cutpointComponents.get(cutpoint)) {
                    componentCutpoints.get(updated).remove(cutpoint);
                    if (componentCutpoints.get(updated).size() <= 1 && !leaves.contains(updated)) {
                        leaves.add(updated);
                    }
                }
                cutpointComponents.remove(cutpoint);
            }
            return new Pair<>(cutpoint, smallest);
        }
    }

    private class SetComparator<E> implements Comparator<Set<E>> {
        @Override
        public int compare(Set<E> o1, Set<E> o2) {
            if (o1.size() < o2.size()) {
                return -1;
            }
            if (o1.size() > o2.size()) {
                return 1;
            }
            return 0;
        }
    }

    private class TimeLimit {
        private double tl;
        private TimeLimit parent;

        public TimeLimit(double tl) {
            this.tl = tl;
        }

        private TimeLimit(TimeLimit parent, double fraction) {
            this.parent = parent;
            tl = parent.getRemainingTime() * fraction;
        }

        public void spend(double time) {
            tl -= time;
            if (parent != null) {
                parent.spend(time);
            }
        }

        public TimeLimit subLimit(double fraction) {
            return new TimeLimit(this, fraction);
        }

        public double getRemainingTime() {
            return tl;
        }
    }
}
