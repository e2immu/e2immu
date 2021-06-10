package org.e2immu.analyser.testexample;

import org.e2immu.annotation.E1Container;

import java.util.LinkedList;
import java.util.List;

/*
infinite loop on IMMUTABLE (but that was not what we're looking for)
 */
@E1Container
public class OutputBuilderSimplified_2 {

    final List<String> list = new LinkedList<>();

    boolean isEmpty() {
        return list.isEmpty();
    }

    OutputBuilderSimplified_2 add(String s) {
        list.add(s);
        return this;
    }

    public static OutputBuilderSimplified_2 go(OutputBuilderSimplified_2 o1, OutputBuilderSimplified_2 o2) {
        if (o1.isEmpty()) return o2;
        if (o2.isEmpty()) return o1;
        return new OutputBuilderSimplified_2().add("abc");
    }
}
