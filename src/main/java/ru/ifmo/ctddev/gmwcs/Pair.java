package ru.ifmo.ctddev.gmwcs;

public class Pair<F, S> {
    public final F first;
    public final S second;

    public Pair(F first, S second) {
        this.first = first;
        this.second = second;
    }

    @Override
    public int hashCode() {
        return first.hashCode() ^ second.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Pair)) {
            return false;
        }
        Pair<?, ?> p = (Pair<?, ?>) obj;
        return first.equals(p.first) && second.equals(p.second);
    }
}
