package org.e2immu.analyser.testexample;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

public class Var_2<X> {

    public final Collection<X> xes;

    public Var_2(Collection<X> xes) {
        this.xes = xes;
    }

    public String method() {
        var all = new StringBuilder();
        for (var i : new int[]{1, 2, 3}) {
            all.append(i);
        }
        for (var s : new String[]{"abc", "def"}) {
            all.append(s);
        }
        // Collection<E> implements Iterable<E> directly
        for (var x : xes) {
            all.append(x.toString());
        }
        return all.toString();
    }

}
