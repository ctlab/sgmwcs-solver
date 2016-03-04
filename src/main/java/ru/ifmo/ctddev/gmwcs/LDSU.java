package ru.ifmo.ctddev.gmwcs;

import java.util.*;

public class LDSU<T> {
    private List<Set<T>> sets;
    private Map<T, List<Integer>> unitsSets;
    private List<Double> weights;

    public LDSU() {
        sets = new ArrayList<>();
        unitsSets = new HashMap<>();
        weights = new ArrayList<>();
    }

    public LDSU(LDSU<T> ldsu, Set<T> subset){
        sets = new ArrayList<>();
        unitsSets = new HashMap<>();
        weights = new ArrayList<>();
        for(T unit : subset){
            unitsSets.put(unit, new ArrayList<>());
        }
        int j = 0;
        for(int i = 0; i < ldsu.size(); i++){
            Set<T> set = new HashSet<>();
            for(T unit : ldsu.set(i)){
                if(subset.contains(unit)){
                    set.add(unit);
                    unitsSets.get(unit).add(j);
                }
            }
            if(!set.isEmpty()){
                sets.add(set);
                weights.add(ldsu.weight(i));
                j++;
            }
        }
    }

    public int size(){
        return sets.size();
    }

    public double weight(int num){
        return weights.get(num);
    }

    public void join(T what, T with){
        List<Integer> x = unitsSets.get(what);
        List<Integer> main = unitsSets.get(with);
        int i = 0, j = 0;
        List<Integer> result = new ArrayList<>();
        while(i != x.size() || j != main.size()){
            int set;
            if(!(j == main.size()) && (i == x.size() || main.get(j) < x.get(i))){
                set = main.get(j);
                ++j;
            } else {
                set = x.get(i);
                sets.get(set).remove(what);
                sets.get(set).add(with);
                ++i;
            }
            if(result.isEmpty() || result.get(result.size() - 1) != set){
                result.add(set);
            }
        }
        unitsSets.put(with, result);
        unitsSets.remove(what);
    }

    public void joinSet(T what, T with) {
        if(unitsSets.get(with).size() != 1 || unitsSets.get(what).size() != 1) {
            throw new IllegalArgumentException();
        }
        int x = unitsSets.get(what).get(0);
        int main = unitsSets.get(with).get(0);
        if(x == main){
            return;
        }
        Set<T> mainSet = sets.get(main);
        for(T unit : sets.get(x)){
            mainSet.add(unit);
            unitsSets.get(unit).set(0, main);
        }
        sets.get(x).clear();
    }

    public List<T> set(int num){
        List<T> result = new ArrayList<>();
        result.addAll(sets.get(num));
        return result;
    }

    public void add(T obj, double weight) {
        Set<T> s = new HashSet<>();
        s.add(obj);
        sets.add(s);
        weights.add(weight);
        List<Integer> l = new ArrayList<>();
        l.add(sets.size() - 1);
        unitsSets.put(obj, l);
    }
}
