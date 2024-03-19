package org.e2immu.analyser.resolver.testexample;

import java.util.LinkedList;
import java.util.List;
import java.util.NavigableSet;

public class Import_16 {

    // NOTE: j.u.NavigableSet derives from j.u.SortedSet!
    interface SortedSet<T> extends NavigableSet<T> {
    }

    public void method(List<NavigableSet<String>> in, SortedSet<Integer> set) {
        List list = new LinkedList();
        in.stream().map(s -> s.headSet("a")).forEach(s -> list.add(s));
        System.out.println(set);
    }

}
