package org.e2immu.analyser.testexample;

import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

public class Lambda_0 {

    public static Collector<Integer, Integer, Integer> collector() {
        return new Collector<>() {
            @Override
            public Supplier<Integer> supplier() {
                return null;
            }

            @Override
            public BiConsumer<Integer, Integer> accumulator() {
                return (i, j) -> System.out.println(i.shortValue() + "?" + j.longValue());
            }

            @Override
            public BinaryOperator<Integer> combiner() {
                return (k, l) -> k+l;
            }

            @Override
            public Function<Integer, Integer> finisher() {
                return null;
            }

            @Override
            public Set<Characteristics> characteristics() {
                return null;
            }
        };
    }
}
