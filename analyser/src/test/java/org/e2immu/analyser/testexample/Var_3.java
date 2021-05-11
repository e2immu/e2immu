package org.e2immu.analyser.testexample;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

public class Var_3<X> {

    public final List<X> xes;

    public Var_3(Collection<X> xes) {
        var set = new HashSet<X>(xes); // keep the X here
        this.xes = new LinkedList<>(set);
    }

    public String method() {
        var all = new StringBuilder();
        for (var i : new int[]{1, 2, 3}) {
            all.append(i);
        }
        for (var s : new String[]{"abc", "def"}) {
            all.append(s);
        }
        // List<E> implements Collection<E> implements Iterable<E>
        for (var x : xes) {
            all.append(x.toString());
        }
        return all.toString();
    }

}
