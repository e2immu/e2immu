package org.e2immu.analyser.testexample;

import java.util.function.Supplier;

// variant on 4, with fields, non-static
public class OutputBuilderSimplified_6 {

    public Supplier<OutputBuilderSimplified_6> j1() {
        return j2();
    }

    // WEIRD WEIRD making j2 static causes the whole delay loop
    public Supplier<OutputBuilderSimplified_6> j2() {
        return new Supplier<>() {

            @Override
            public OutputBuilderSimplified_6 get() {
                return null;
            }
        };
    }
}
