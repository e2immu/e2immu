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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class OutputBuilder {
    List<OutputElement> list = new LinkedList<>();

    public OutputBuilder addIfNotNull(OutputBuilder outputBuilder) {
        if (outputBuilder != null) {
            list.addAll(outputBuilder.list);
        }
        return this;
    }

    public OutputBuilder add(OutputElement... outputElements) {
        Collections.addAll(list, outputElements);
        return this;
    }

    public OutputBuilder add(OutputBuilder... outputBuilders) {
        Arrays.stream(outputBuilders).flatMap(ob -> ob.list.stream()).forEach(list::add);
        return this;
    }

    public static Collector<OutputBuilder, OutputBuilder, OutputBuilder> joining() {
        return joining(Space.NONE, Space.NONE, Space.NONE, Guide.defaultGuideGenerator());
    }

    public static Collector<OutputBuilder, OutputBuilder, OutputBuilder> joining(OutputElement separator) {
        return joining(separator, Space.NONE, Space.NONE, Guide.defaultGuideGenerator());
    }

    public static Collector<OutputBuilder, OutputBuilder, OutputBuilder> joining(OutputElement separator,
                                                                                 Guide.GuideGenerator guideGenerator) {
        return joining(separator, Space.NONE, Space.NONE, guideGenerator);
    }

    public static Collector<OutputBuilder, OutputBuilder, OutputBuilder> joining(OutputElement separator,
                                                                                 OutputElement start,
                                                                                 OutputElement end,
                                                                                 Guide.GuideGenerator guideGenerator) {
        return new Collector<>() {
            private final AtomicInteger countMid = new AtomicInteger();

            @Override
            public Supplier<OutputBuilder> supplier() {
                return OutputBuilder::new;
            }

            @Override
            public BiConsumer<OutputBuilder, OutputBuilder> accumulator() {
                return (a, b) -> {
                    if (!b.isEmpty()) {
                        if (a.notStart()) { // means: not empty, not only guides
                            if (separator != Space.NONE) a.add(separator);
                            a.add(guideGenerator.mid());
                            countMid.incrementAndGet();
                        }
                        a.add(b);
                    }
                };
            }

            @Override
            public BinaryOperator<OutputBuilder> combiner() {
                return (a, b) -> {
                    if (a.isEmpty()) return b;
                    if (b.isEmpty()) return a;
                    if (separator != Space.NONE) a.add(separator);
                    countMid.incrementAndGet();
                    return a.add(guideGenerator.mid()).add(b);
                };
            }

            @Override
            public Function<OutputBuilder, OutputBuilder> finisher() {
                return t -> {
                    OutputBuilder result = new OutputBuilder();
                    if (start != Space.NONE) result.add(start);
                    if (countMid.get() > 0 || guideGenerator.keepGuidesWithoutMid()) {
                        result.add(guideGenerator.start());
                        result.add(t);
                        result.add(guideGenerator.end());
                    } else {
                        result.add(t); // without the guides
                    }
                    if (end != Space.NONE) result.add(end);
                    return result;
                };
            }

            @Override
            public Set<Characteristics> characteristics() {
                return Set.of(Characteristics.CONCURRENT);
            }
        };
    }

    private boolean isEmpty() {
        return list.isEmpty();
    }

    private boolean notStart() {
        return !list.stream().allMatch(outputElement -> outputElement instanceof Guide);
    }

    @Override
    public String toString() {
        return list.stream().map(OutputElement::minimal).collect(Collectors.joining());
    }

    public String debug() {
        return list.stream().map(OutputElement::debug).collect(Collectors.joining());
    }

    // used for sorting annotations
    public OutputElement get(int i) {
        return list.get(i);
    }

    public String generateJavaForDebugging() {
        return list.stream().map(OutputElement::generateJavaForDebugging).collect(Collectors.joining("\n"));
    }
}
