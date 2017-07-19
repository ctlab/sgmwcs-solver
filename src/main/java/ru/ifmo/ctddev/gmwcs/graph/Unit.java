package ru.ifmo.ctddev.gmwcs.graph;

import ru.ifmo.ctddev.gmwcs.Signals;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public abstract class Unit implements Comparable<Unit> {
    protected int num;
  //  protected double weight;
    protected List<Unit> absorbed;

    public Unit(int num) {
        this.num = num;
        //this.weight = weight;
        absorbed = new ArrayList<>();
    }

    public Unit(Unit that) {
        this(that.num);
    }

    public void absorb(Unit unit) {
        absorbed.addAll(unit.getAbsorbed());
        unit.clear();
        absorbed.add(unit);
    }

    public void clear() {
        absorbed.clear();
    }

    public List<Unit> getAbsorbed() {
        return new ArrayList<>(absorbed);
    }

    @Override
    public int hashCode() {
        return num;
    }

    public int getNum() {
        return num;
    }

  //  public void setWeight(Signals signals) {
 //       weight = signals.getUnitsSets().get(this)
 //               .stream().mapToDouble(signals::weight)
//                .sum();
 //   }

    //public double getWeight() {
 //       return weight;
   // }

   // public void setWeight(double weight) {
  //      this.weight = weight;
  //  }

    @Override
    public boolean equals(Object o) {
        return (o.getClass() == getClass() && num == ((Unit) o).num);
    }

    @Override
    public int compareTo(Unit u) {
        //if (u.weight != weight) {
       //     return Double.compare(u.weight, weight);
      //  }
        return Integer.compare(u.getNum(), num);
    }
}