package org.e2immu.analyser.parser.start.testexample;

import java.util.HashSet;
import java.util.Set;

public class Mutable_0 {

    private final Set<String> set = new HashSet<>();

    public void method(String s) {
        boolean b1 = set.contains(s);
        set.add("hello"); // increases modification time of 'set'
        boolean b2 = set.contains(s);
        assert b1 != b2; // not always true/false
        System.out.println("s is " + s); // increases statement time
        boolean b3 = set.contains(s);
        assert b2 != b3; // not necessarily false
    }

}
