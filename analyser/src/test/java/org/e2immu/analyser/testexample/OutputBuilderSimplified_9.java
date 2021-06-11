package org.e2immu.analyser.testexample;

import org.e2immu.annotation.E1Immutable;
import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

// Remove countMid and the delay is gone...
// alternatively, remove the (a, b) bi-consumer and replace by 'null'

// the supplier should not be immutable, since it contains a @Modified field
// as a consequence, the BiConsumer should not be immutable either

public class OutputBuilderSimplified_9 {

    @E1Immutable
    @NotNull
    @NotModified
    public static Supplier<BiConsumer<OutputBuilderSimplified_9, OutputBuilderSimplified_9>> joining() {
        return new Supplier<>() {

            @Modified
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
