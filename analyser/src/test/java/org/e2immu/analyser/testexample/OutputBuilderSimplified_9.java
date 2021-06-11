package org.e2immu.analyser.testexample;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

// Remove countMid and the delay is gone...
// alternatively, remove the (a, b) bi-consumer and replace by 'null'

// the supplier should not be immutable, since it contains a @Modified field
// as a consequence, the BiConsumer should not be immutable either

public class OutputBuilderSimplified_9 {

    public static Supplier<BiConsumer<OutputBuilderSimplified_9, OutputBuilderSimplified_9>> joining() {
        return new Supplier<>() {
            private final AtomicInteger countMid = new AtomicInteger();

            @Override
            public BiConsumer<OutputBuilderSimplified_9, OutputBuilderSimplified_9> get() {
                return (a, b) -> {
                    countMid.incrementAndGet();
                };
            }
        };
    }
}
