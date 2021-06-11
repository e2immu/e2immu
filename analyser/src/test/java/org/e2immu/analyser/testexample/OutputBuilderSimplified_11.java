package org.e2immu.analyser.testexample;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

// Remove countMid and the delay is gone...
// alternatively, remove the (a, b) bi-consumer and replace by 'null'

// variant of 9, without modifying countMid

public class OutputBuilderSimplified_11 {

    public static Supplier<BiConsumer<OutputBuilderSimplified_11, OutputBuilderSimplified_11>> joining() {
        return new Supplier<>() {
            private final AtomicInteger countMid = new AtomicInteger();

            @Override
            public BiConsumer<OutputBuilderSimplified_11, OutputBuilderSimplified_11> get() {
                return (a, b) -> {
                    countMid.get();
                };
            }
        };
    }
}
