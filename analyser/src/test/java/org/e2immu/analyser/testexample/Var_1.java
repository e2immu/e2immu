package org.e2immu.analyser.testexample;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

public class Var_1<X> {

    public final List<X> xes;

    public Var_1(Collection<X> xes) {
        var set = new HashSet<>(xes); // no X here
        this.xes = new LinkedList<>(set);
    }

}
