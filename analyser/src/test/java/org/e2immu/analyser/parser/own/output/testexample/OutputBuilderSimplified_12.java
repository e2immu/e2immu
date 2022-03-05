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
public class OutputBuilderSimplified_12 {
    interface OutputElement {
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

    public OutputBuilderSimplified_12 add(OutputElement... outputElements) {
        Collections.addAll(list, outputElements);
        return this;
    }

    public OutputBuilderSimplified_12 add(OutputBuilderSimplified_12... outputBuilders) {
        Arrays.stream(outputBuilders).flatMap(ob -> ob.list.stream()).forEach(list::add);
        return this;
    }

    public static Collector<OutputBuilderSimplified_12, OutputBuilderSimplified_12, OutputBuilderSimplified_12> joining(OutputElement separator,
                                                                                                                        OutputElement start,
                                                                                                                        OutputElement end) {
        return new Collector<>() {
            private final AtomicInteger countMid = new AtomicInteger();

            @Override
            public Supplier<OutputBuilderSimplified_12> supplier() {
                return OutputBuilderSimplified_12::new;
            }

            @Override
            public BiConsumer<OutputBuilderSimplified_12, OutputBuilderSimplified_12> accumulator() {
                return (a, b) -> {
                    if (!b.list.isEmpty()) {
                        boolean notStart =  !a.list.stream().allMatch(outputElement -> outputElement instanceof Space);
                        if (notStart) { // means: not empty, not only guides
                            if (separator != Space.NONE) a.add(separator);
                            a.add(Space.NONE);
                            countMid.incrementAndGet();
                        }
                        a.add(b);
                    }
                };
            }

            @Override
            public BinaryOperator<OutputBuilderSimplified_12> combiner() {
                return (aa, bb) -> {
                    if (aa.list.isEmpty()) return bb;
                    if (bb.list.isEmpty()) return aa;
                    if (separator != Space.NONE) aa.add(separator);
                    countMid.incrementAndGet();
                    return aa.add(Space.NONE).add(bb);
                };
            }

            @Override
            public Function<OutputBuilderSimplified_12, OutputBuilderSimplified_12> finisher() {
                return t -> {
                    OutputBuilderSimplified_12 result = new OutputBuilderSimplified_12();
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
}
