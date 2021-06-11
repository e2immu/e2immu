package org.e2immu.analyser.testexample;


import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;
import java.util.stream.Collector;
import java.util.stream.Collectors;

// bug in @Identity on joining()

public class OutputBuilderSimplified_8 {
    interface OutputElement {
    }

    public static Collector<OutputBuilderSimplified_8, OutputBuilderSimplified_8, OutputBuilderSimplified_8> joining() {
        return joining();
    }

    public static Collector<OutputBuilderSimplified_8, OutputBuilderSimplified_8, OutputBuilderSimplified_8> joining(OutputElement separator) {
        return new Collector<>() {

            @Override
            public Supplier<OutputBuilderSimplified_8> supplier() {
                return null;
            }

            @Override
            public BiConsumer<OutputBuilderSimplified_8, OutputBuilderSimplified_8> accumulator() {
                return null;
            }

            @Override
            public BinaryOperator<OutputBuilderSimplified_8> combiner() {
                return null;
            }

            @Override
            public Function<OutputBuilderSimplified_8, OutputBuilderSimplified_8> finisher() {
              return null;
            }

            @Override
            public Set<Characteristics> characteristics() {
                return null;
            }
        };
    }

}
