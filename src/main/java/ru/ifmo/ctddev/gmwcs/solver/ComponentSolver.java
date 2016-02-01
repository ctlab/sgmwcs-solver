package ru.ifmo.ctddev.gmwcs.solver;

import org.jgrapht.Graphs;
import org.jgrapht.UndirectedGraph;
import org.jgrapht.alg.ConnectivityInspector;
import org.jgrapht.graph.SimpleGraph;
import ru.ifmo.ctddev.gmwcs.LDSU;
import ru.ifmo.ctddev.gmwcs.TimeLimit;
import ru.ifmo.ctddev.gmwcs.graph.Blocks;
import ru.ifmo.ctddev.gmwcs.graph.Edge;
import ru.ifmo.ctddev.gmwcs.graph.Node;
import ru.ifmo.ctddev.gmwcs.graph.Unit;

import java.util.*;
import java.util.stream.Collectors;

public class ComponentSolver implements Solver {
    public final int threshold;
    private TimeLimit tl;
    private Double externLB;
    private boolean isSolvedToOptimality;
    private boolean suppressingOutput;
    private List<Integer> threadConf;

    public ComponentSolver(int threshold) {
        this.threshold = threshold;
        externLB = 0.0;
        tl = new TimeLimit(Double.POSITIVE_INFINITY);
    }

    @Override
    public List<Unit> solve(UndirectedGraph<Node, Edge> graph, LDSU<Unit> synonyms) throws SolverException {
        AtomicDouble lb = new AtomicDouble(externLB);
        PriorityQueue<Set<Node>> components = getComponents(graph);
        List<Worker> workers = new ArrayList<>();
        List<UndirectedGraph<Node, Edge>> graphs = new ArrayList<>();
        List<Node> roots = new ArrayList<>();
        while(!components.isEmpty()){
            Set<Node> component = components.poll();
            UndirectedGraph<Node, Edge> subgraph = Utils.subgraph(graph, component);
            graphs.add(subgraph);
            Node root = null;
            if(component.size() >= threshold){
                root = getRoot(subgraph);
                if(root != null){
                    addComponents(subgraph, root, components);
                }
            }
            roots.add(root);
        }
        for(int i = 0; i < threadConf.size(); i++){
            if(threadConf.isEmpty()){
                throw new IllegalArgumentException();
            }
            int threads = threadConf.get(i);
            RLTSolver solver = new RLTSolver();
            if(suppressingOutput){
                solver.suppressOutput();
            }
            solver.setSharedLB(lb);
            solver.setTimeLimit(new TimeLimit(tl.getRemainingTime()));
            solver.setThreadsNum(threads);
            if(i == threadConf.size() - 1){
                workers.add(new Worker(graphs, roots, synonyms, solver));
            } else {
                if(graphs.isEmpty()){
                    break;
                }
                List<UndirectedGraph<Node, Edge>> gs = Collections.singletonList(graphs.get(0));
                List<Node> rs = Collections.singletonList(roots.get(0));
                graphs = graphs.subList(1, graphs.size());
                roots = roots.subList(1, roots.size());
                workers.add(new Worker(gs, rs, synonyms, solver));
            }
        }
        return runWorkers(workers, synonyms);
    }

    private List<Unit> runWorkers(List<Worker> workers, LDSU<Unit> synonyms) {
        isSolvedToOptimality = true;
        List<Thread> threads = workers.stream().map(Thread::new).collect(Collectors.toList());
        threads.forEach(Thread::start);
        try {
            for (Thread t : threads) {
                t.join();
            }
        } catch (InterruptedException ignored) {}
        List<Unit> solution = new ArrayList<>();
        for(Worker w : workers){
            if(!w.isSolvedToOptimality()){
                isSolvedToOptimality = false;
            }
            if(Utils.sum(solution, synonyms) < Utils.sum(w.getResult(), synonyms)){
                solution = w.getResult();
            }
        }
        return solution;
    }

    @Override
    public boolean isSolvedToOptimality() {
        return isSolvedToOptimality;
    }

    private void addComponents(UndirectedGraph<Node, Edge> subgraph, Node root, PriorityQueue<Set<Node>> components) {
        UndirectedGraph<Node, Edge> copy = new SimpleGraph<>(Edge.class);
        Graphs.addGraph(copy, subgraph);
        copy.removeVertex(root);
        ConnectivityInspector<Node, Edge> inspector = new ConnectivityInspector<>(copy);
        components.addAll(inspector.connectedSets());
    }

    private Node getRoot(UndirectedGraph<Node, Edge> graph) {
        Blocks blocks = new Blocks(graph);
        if (blocks.cutpoints().isEmpty()) {
            return null;
        }
        int min = Integer.MAX_VALUE;
        Node res = null;
        for (Node cp : blocks.cutpoints()) {
            int curr = dfs(graph, cp, true, new HashSet<>());
            if (curr < min) {
                min = curr;
                res = cp;
            }
        }
        return res;
    }

    public void setThreadConfiguration(List<Integer> conf){
        this.threadConf = conf;
    }

    private int dfs(UndirectedGraph<Node, Edge> graph, Node v, boolean isMax, Set<Node> vis) {
        vis.add(v);
        int res = 0;
        for (Node u : Graphs.neighborListOf(graph, v)) {
            if (!vis.contains(u)) {
                int val = dfs(graph, u, false, vis);
                if (isMax) {
                    res = Math.max(val, res);
                } else {
                    res += val;
                }
            }
        }
        return isMax ? res : res + 1;
    }

    private PriorityQueue<Set<Node>> getComponents(UndirectedGraph<Node, Edge> graph) {
        ConnectivityInspector<Node, Edge> inspector = new ConnectivityInspector<>(graph);
        PriorityQueue<Set<Node>> result = new PriorityQueue<>(new SetComparator());
        result.addAll(inspector.connectedSets());
        return result;
    }

    @Override
    public void setTimeLimit(TimeLimit tl) {
        this.tl = tl;
    }

    @Override
    public TimeLimit getTimeLimit() {
        return tl;
    }

    @Override
    public void suppressOutput() {
        suppressingOutput = true;
    }

    @Override
    public void setLB(double lb) {
        externLB = lb;
    }

    private class SetComparator implements Comparator<Set<Node>> {
        @Override
        public int compare(Set<Node> o1, Set<Node> o2) {
            return -Integer.compare(o1.size(), o2.size());
        }
    }
}
