package ru.ifmo.ctddev.gmwcs.solver;

import java.util.concurrent.atomic.AtomicLong;

public class AtomicDouble {
    private AtomicLong rep;

    public AtomicDouble(double init){
        rep = new AtomicLong(Double.doubleToLongBits(init));
    }

    public double get(){
        return Double.longBitsToDouble(rep.get());
    }

    public boolean compareAndSet(double expected, double update){
        long exp = Double.doubleToLongBits(expected);
        long upd = Double.doubleToLongBits(update);
        return rep.compareAndSet(exp, upd);
    }
}
