package org.e2immu.analyser.testexample;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

// compare to 9, here we return a function rather than a consumer

public class OutputBuilderSimplified_10 {

    public static Supplier<Function<OutputBuilderSimplified_10, Integer>> joining() {
        return new Supplier<>() {
            private final AtomicInteger countMid = new AtomicInteger();

            @Override
            public Function<OutputBuilderSimplified_10, Integer> get() {
                return ob -> countMid.get();
            }
        };
    }
}
