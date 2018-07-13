package ru.ifmo.ctddev.gmwcs.solver;

import ru.ifmo.ctddev.gmwcs.Signals;

import java.util.Arrays;
import java.util.stream.IntStream;

/**
 * Created by Nikolay Poperechnyi on 12.07.18.
 */
public class DSU {

    private int[] parent;
    private int[] size;
    private int[] min;

    public DSU(Signals s) {
        parent = IntStream.rangeClosed(0, s.size()).toArray();
        min = IntStream.rangeClosed(0, s.size()).toArray();
        size = new int[s.size()];
        Arrays.fill(size, 1);
    }

    private int get(int n) {
        if (n == parent[n])
            return n;
        else return parent[n] = get(parent[n]);
    }

    public void union(int a, int b) {
        a = get(a);
        b = get(b);
        if (a != b) {
            if (size[a] < size[b]) {
                int t = a;
                a = b;
                b = t;
            }
            parent[b] = a;
            size[a] += size[b];
            min[a] = Math.min(min[a], min[b]);
        }
    }

    public int min(int n) {
        return min[get(n)];
    }
}
