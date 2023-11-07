package org.e2immu.analyser.resolver.testexample;

import java.io.Serializable;
import java.util.Date;
import java.util.SortedMap;
import java.util.TreeMap;

public class MethodCall_58 {

    static abstract class I implements Comparable<I>, Cloneable, Serializable {
    }

    static class D extends I implements Serializable {
        D(Date date) {
        }

        @Override
        public int compareTo(I o) {
            return 0;
        }
    }

    static double add(double x, double y) {
        return x + y;
    }

    public void method1(Date date, double n) {
        SortedMap<D, Double> map = new TreeMap<>();
     //   map.compute(new D(date), (k, v) -> add(v == null ? 0 : (double) v, n));
    }

    public void method2(Date date, double n) {
        SortedMap<D, Double> map = new TreeMap<>();
        map.compute(new D(date), (k, v) -> add(v == null ? 0L : v, n));
    }

    static double setValue(Double d) {
        return d;
    }

    public double method3(String tmp) {
        return setValue(tmp != null && tmp.length() > 0 ? Double.valueOf(tmp) : 0d);
    }
}
