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

package org.e2immu.analyser.parser.functional.testexample;

// lambda inside new object creation

import org.e2immu.annotation.Container;
import org.e2immu.annotation.FinalFields;
import org.e2immu.annotation.ImmutableContainer;
import org.e2immu.annotation.Independent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class Lambda_10 {

    interface AnalyserComponents<T, S> {
        Map<T, String> collapsed(S input);
    }

    @FinalFields
    @Container
    record AnalyserComponentsImpl<T, S>(Map<T, Function<S, String>> map) implements AnalyserComponents<T, S> {
        @Override
        public Map<T, String> collapsed(S input) {
            return null;
        }
    }

    @Container
    @FinalFields
    @Independent(hc = true)
    static class Builder<T, S> {
        private final Map<T, Function<S, String>> map = new HashMap<>();

        public Builder<T, S> add(T t, Function<S, String> f) {
            map.put(t, f);
            return this;
        }

        @ImmutableContainer(hc = true)
        public AnalyserComponentsImpl<T, S> build() {
            Map<T, Function<S, String>> immMap = Map.copyOf(map);
            return new AnalyserComponentsImpl<>(immMap);
        }
    }

    @ImmutableContainer
    public final AnalyserComponents<String, Integer> analyserComponents =
            new Builder<String, Integer>()
                    .add("identity", i -> "" + i)
                    .add("plus", i -> "+" + i)
                    .build();
}
