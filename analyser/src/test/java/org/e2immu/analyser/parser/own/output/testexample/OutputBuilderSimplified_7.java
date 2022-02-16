/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyser.parser.own.output.testexample;


import org.e2immu.annotation.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;
import java.util.stream.Collector;
import java.util.stream.Collectors;

@E1Container
public class OutputBuilderSimplified_7 {
    interface OutputElement {
        default String minimal() {
            return "";
        }

        default String debug() {
            return "";
        }
    }

    @ERContainer
    static class Space implements OutputElement {
        static final Space NONE = new Space();
    }

    @Modified
    final List<OutputElement> list = new LinkedList<>();

    @Modified @Fluent @NotNull
    public OutputBuilderSimplified_7 addIfNotNull(OutputBuilderSimplified_7 outputBuilder) {
        if (outputBuilder != null) {
            list.addAll(outputBuilder.list);
        }
        return this;
    }

    public OutputBuilderSimplified_7 add(OutputElement... outputElements) {
        Collections.addAll(list, outputElements);
        return this;
    }

    public OutputBuilderSimplified_7 add(OutputBuilderSimplified_7... outputBuilders) {
        Arrays.stream(outputBuilders).flatMap(ob -> ob.list.stream()).forEach(list::add);
        return this;
    }

    public static Collector<OutputBuilderSimplified_7, OutputBuilderSimplified_7, OutputBuilderSimplified_7> joining() {
        return joining(Space.NONE, Space.NONE, Space.NONE);
    }

    public static Collector<OutputBuilderSimplified_7, OutputBuilderSimplified_7, OutputBuilderSimplified_7> joining(OutputElement separator) {
        return joining(separator, Space.NONE, Space.NONE);
    }

    public static Collector<OutputBuilderSimplified_7, OutputBuilderSimplified_7, OutputBuilderSimplified_7> joining(OutputElement separator,
                                                                                                                     OutputElement start,
                                                                                                                     OutputElement end) {
        return new Collector<>() {
            private final AtomicInteger countMid = new AtomicInteger();

            @Override
            public Supplier<OutputBuilderSimplified_7> supplier() {
                return OutputBuilderSimplified_7::new;
            }

            @Override
            public BiConsumer<OutputBuilderSimplified_7, OutputBuilderSimplified_7> accumulator() {
                return (a, b) -> {
                    if (!b.isEmpty()) {
                        if (a.notStart()) { // means: not empty, not only guides
                            if (separator != Space.NONE) a.add(separator);
                            a.add(Space.NONE);
                            countMid.incrementAndGet();
                        }
                        a.add(b);
                    }
                };
            }

            @Override
            public BinaryOperator<OutputBuilderSimplified_7> combiner() {
                return (a, b) -> {
                    if (a.isEmpty()) return b;
                    if (b.isEmpty()) return a;
                    if (separator != Space.NONE) a.add(separator);
                    countMid.incrementAndGet();
                    return a.add(Space.NONE).add(b);
                };
            }

            @Override
            public Function<OutputBuilderSimplified_7, OutputBuilderSimplified_7> finisher() {
                return t -> {
                    OutputBuilderSimplified_7 result = new OutputBuilderSimplified_7();
                    if (start != Space.NONE) result.add(start);
                    if (countMid.get() > 0 ) {
                       // result.add(guideGenerator.start());
                        result.add(t);
                     //   result.add(guideGenerator.end());
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

    public boolean isEmpty() {
        return list.isEmpty();
    }

    private boolean notStart() {
        return !list.stream().allMatch(outputElement -> outputElement instanceof Space);
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
        return list.stream().map(OutputElement::debug).collect(Collectors.joining("\n"));
    }

    public void replace(UnaryOperator<OutputElement> replacer) {
        list.replaceAll(replacer);
    }
}
