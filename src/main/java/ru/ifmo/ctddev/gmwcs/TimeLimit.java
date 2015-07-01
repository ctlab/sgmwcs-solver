package ru.ifmo.ctddev.gmwcs;

public class TimeLimit {
    private double tl;
    private TimeLimit parent;

    public TimeLimit(double tl) {
        if (tl < 0) {
            throw new IllegalArgumentException();
        }
        this.tl = tl;
    }

    private TimeLimit(TimeLimit parent, double fraction) {
        if (fraction < 0.0 || fraction > 1.0) {
            throw new IllegalArgumentException();
        }
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