package org.e2immu.analyser.parser.functional.testexample;

import java.util.function.Function;

public class Lambda_17 {

    record R(String s1, String s2) {
    }

    public static int method(R input) {
        Function<R, Integer> f = r -> r.s1().length() + r.s2().length();
        return f.apply(input);
    }
}
