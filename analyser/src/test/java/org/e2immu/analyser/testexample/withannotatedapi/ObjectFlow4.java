package org.e2immu.analyser.testexample.withannotatedapi;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

/*
 object flows:

 new HashSet (origin: ObjectCreation), assigned to 'set'

 set.add() adds modifying access to the field's object flow... HOW??

 set.stream() adds method access to the set of the field
 */
public class ObjectFlow4 {

    private final Set<String> set = new HashSet<>();

    public void add(String s) {
        set.add(s);
    }

    public Stream<String> stream() {
        return set.stream();
    }
}
