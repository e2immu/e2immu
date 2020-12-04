/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.output;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class OutputBuilder {
    List<OutputElement> list = new LinkedList<>();

    public OutputBuilder add(OutputElement... outputElements) {
        Collections.addAll(list, outputElements);
        return this;
    }

    public OutputBuilder add(OutputBuilder... outputBuilders) {
        Arrays.stream(outputBuilders).flatMap(ob -> ob.list.stream()).forEach(list::add);
        return this;
    }

    public static Collector<OutputBuilder, OutputBuilder, OutputBuilder> joining(OutputElement outputElement) {
        return new Collector<>() {
            @Override
            public Supplier<OutputBuilder> supplier() {
                return OutputBuilder::new;
            }

            @Override
            public BiConsumer<OutputBuilder, OutputBuilder> accumulator() {
                return (a, b) -> new OutputBuilder().add(a).add(outputElement).add(b);
            }

            @Override
            public BinaryOperator<OutputBuilder> combiner() {
                return (a, b) -> new OutputBuilder().add(a, b);
            }

            @Override
            public Function<OutputBuilder, OutputBuilder> finisher() {
                return t -> t;
            }

            @Override
            public Set<Characteristics> characteristics() {
                return Set.of(Characteristics.CONCURRENT, Characteristics.IDENTITY_FINISH);
            }
        };
    }

    @Override
    public String toString() {
        return list.stream().map(OutputElement::minimal).collect(Collectors.joining());
    }

    public String debug() {
        return list.stream().map(OutputElement::debug).collect(Collectors.joining());
    }
}