package org.e2immu.analyser.parser.start.testexample;

import org.e2immu.annotation.ImmutableContainer;

import java.util.HashSet;
import java.util.Set;

public class Mutable_1 {

    @ImmutableContainer(absent = true)
    private final Set<String> set = new HashSet<>();

    public int method(String s) {
        if (set.contains(s)) {
            return s.length();
        }
        System.out.println("increment statement time");
        // this return should not collapse to 0 (!set.contains(s))
        return set.contains(s) ? 1 : 0;
    }

    public void add(String s) {
        set.add(s);
    }

    public static void static1(String s) {
        Mutable_1 m1 = new Mutable_1();
        m1.add(s);
        // would be nice if this were always true, but we don't have a companion for Mutable_1.add() yet
        assert m1.method(s) == s.length();
    }

    public static void static2(String s) {
        Mutable_1 m1 = new Mutable_1();
        // would be nice if this were always true, time increment on local variable has no effect
        assert m1.method(s) == 0;
    }
}
