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
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

@FinalFields
@Container(absent = true)
public class OutputBuilderSimplified_13 {
    // NOT IMMUTABLE
    interface OutputElement {
        default String debug() {
            return "";
        }
    }

    @ImmutableContainer
    static class Space implements OutputElement {
        static final Space NONE = new Space();
    }

    @Modified
    final List<OutputElement> list = new LinkedList<>();

    public OutputBuilderSimplified_13 add(@Independent(absent = true) @NotModified OutputElement... outputElements) {
        Collections.addAll(list, outputElements);
        return this;
    }

    public OutputBuilderSimplified_13 add(@NotModified OutputBuilderSimplified_13... outputBuilders) {
        Arrays.stream(outputBuilders).flatMap(ob -> ob.list.stream()).forEach(list::add);
        return this;
    }

    public static Collector<OutputBuilderSimplified_13, OutputBuilderSimplified_13, OutputBuilderSimplified_13> joining(@Modified OutputElement separator,
                                                                                                                        OutputElement start,
                                                                                                                        OutputElement end) {
        return new Collector<>() {
            private final AtomicInteger countMid = new AtomicInteger();

            @Override
            public Supplier<OutputBuilderSimplified_13> supplier() {
                return OutputBuilderSimplified_13::new;
            }

            @Override
            public BiConsumer<OutputBuilderSimplified_13, OutputBuilderSimplified_13> accumulator() {
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
            public BinaryOperator<OutputBuilderSimplified_13> combiner() {
                return (aa, bb) -> {
                    if (aa.list.isEmpty()) return bb;
                    if (bb.list.isEmpty()) return aa;
                    if (separator != Space.NONE) aa.add(separator);
                    countMid.incrementAndGet();
                    return aa.add(Space.NONE).add(bb);
                };
            }

            @Override
            public Function<OutputBuilderSimplified_13, OutputBuilderSimplified_13> finisher() {
                return t -> {
                    OutputBuilderSimplified_13 result = new OutputBuilderSimplified_13();
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
