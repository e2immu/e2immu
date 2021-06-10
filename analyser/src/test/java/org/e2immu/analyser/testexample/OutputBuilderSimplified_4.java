package org.e2immu.analyser.testexample;

import java.util.function.Supplier;

// drastically simplified, infinite loop
public class OutputBuilderSimplified_4 {

    public static Supplier<OutputBuilderSimplified_4> j1() {
        return j2();
    }

    public static Supplier<OutputBuilderSimplified_4> j2() {
        return new Supplier<>() {

            @Override
            public OutputBuilderSimplified_4 get() {
                return null;
            }
        };
    }
}
