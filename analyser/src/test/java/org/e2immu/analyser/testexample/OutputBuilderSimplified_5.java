package org.e2immu.analyser.testexample;

import java.util.function.Supplier;

// variant on 4, no problems.
public class OutputBuilderSimplified_5 {

    public static Supplier<OutputBuilderSimplified_5> j1() {
        return j2();
    }

    public static Supplier<OutputBuilderSimplified_5> j2() {
        return () -> null;
    }
}
