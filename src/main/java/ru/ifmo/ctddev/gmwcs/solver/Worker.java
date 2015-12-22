package ru.ifmo.ctddev.gmwcs.solver;

import org.jgrapht.UndirectedGraph;
import ru.ifmo.ctddev.gmwcs.LDSU;
import ru.ifmo.ctddev.gmwcs.TimeLimit;
import ru.ifmo.ctddev.gmwcs.graph.Edge;
import ru.ifmo.ctddev.gmwcs.graph.Node;
import ru.ifmo.ctddev.gmwcs.graph.Unit;

import java.util.List;

public class Worker implements Runnable {
    private final LDSU<Unit> synonyms;
    private final List<UndirectedGraph<Node, Edge>> graphs;
    private final RootedSolver solver;
    private final List<Node> roots;
    private List<Unit> result;
    private boolean isSolvedToOptimality;
    private boolean isOk;

    public Worker(List<UndirectedGraph<Node, Edge>> graphs, List<Node> roots, LDSU<Unit> synonyms, RootedSolver solver){
        this.solver = solver;
        this.graphs = graphs;
        this.synonyms = synonyms;
        this.roots = roots;
        solver.suppressOutput();
        isSolvedToOptimality = true;
        isOk = true;
    }

    @Override
    public void run() {
        for(int i = 0; i < graphs.size(); i++){
            long startTime = System.currentTimeMillis();
            solver.setRoot(roots.get(i));
            try {
                List<Unit> sol = solver.solve(graphs.get(i), synonyms);
                if(Utils.sum(sol, synonyms) > Utils.sum(result, synonyms)){
                    result = sol;
                }
            } catch (SolverException e) {
                isOk = false;
            }
            long time = System.currentTimeMillis();
            if(!solver.isSolvedToOptimality()){
                isSolvedToOptimality = false;
            }
            TimeLimit tl = solver.getTimeLimit();
            tl.spend((time - startTime) / 1000.0);
        }
    }

    public List<Unit> getResult(){
        return result;
    }

    public boolean isSolvedToOptimality(){
        return isSolvedToOptimality;
    }

    public boolean isOk(){
        return isOk;
    }
}
