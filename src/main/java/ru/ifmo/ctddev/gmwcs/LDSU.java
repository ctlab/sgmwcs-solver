package ru.ifmo.ctddev.gmwcs;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class LDSU<T> {
    private Map<T, List<T>> sets;

    public LDSU() {
        sets = new LinkedHashMap<>();
    }

    public void merge(T f, T s) {
        List<T> first = sets.get(f);
        List<T> second = sets.get(s);
        first.addAll(second);
        for (T el : listOf(s)) {
            sets.put(el, first);
        }
    }

    public List<T> listOf(T obj) {
        return sets.get(obj);
    }

    public void add(T obj) {
        List<T> list = new LinkedList<T>();
        list.add(obj);
        sets.put(obj, list);
    }
}
