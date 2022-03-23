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


import org.e2immu.annotation.E1Immutable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Stream;

// variant on InlinedMethod_13 where subElements is not expanded because it has implementations

public class InlinedMethod_14 {
    record TypeInfo(String name) {
    }
    record MethodInfo(String name, TypeInfo typeInfo){

    }

    interface Expression {
        // not implemented elsewhere, so inlining works
        default List<? extends Expression> subElements() {
            return List.of();
        }

        // not implemented elsewhere, so inlining works
        default UpgradableBooleanMap<TypeInfo> typesReferenced() {
            return subElements().stream().flatMap(e -> e.typesReferenced().stream()).collect(UpgradableBooleanMap.collector());
        }
    }

    // this type is not a container, the implicit single argument constructor is linked to the map field which is modified
    @E1Immutable
    record UpgradableBooleanMap<T>(Map<T, Boolean> map) {

        public UpgradableBooleanMap() {
            this(new HashMap<>());
        }

        @SafeVarargs
        static <T> UpgradableBooleanMap<T> of(UpgradableBooleanMap<T>... maps) {
            UpgradableBooleanMap<T> result = new UpgradableBooleanMap<>(new HashMap<>());
            if (maps != null) {
                for (UpgradableBooleanMap<T> map : maps) {
                    result.putAll(map);
                }
            }
            return result;
        }

        public void put(T t, boolean b) {
            map.put(t, b);
        }

        private UpgradableBooleanMap<T> putAll(UpgradableBooleanMap<T> other) {
            other.stream().forEach(e -> this.put(e.getKey(), e.getValue()));
            return this;
        }

        public Stream<Map.Entry<T, Boolean>> stream() {
            return map.entrySet().stream();
        }

        public static <T> Collector<? super Map.Entry<T, Boolean>, UpgradableBooleanMap<T>, UpgradableBooleanMap<T>> collector() {
            return new Collector<>() {
                @Override
                public Supplier<UpgradableBooleanMap<T>> supplier() {
                    return UpgradableBooleanMap::new;
                }

                @Override
                public BiConsumer<UpgradableBooleanMap<T>, Map.Entry<T, Boolean>> accumulator() {
                    return (map, e) -> map.put(e.getKey(), e.getValue());
                }

                @Override
                public BinaryOperator<UpgradableBooleanMap<T>> combiner() {
                    return UpgradableBooleanMap::putAll;
                }

                @Override
                public Function<UpgradableBooleanMap<T>, UpgradableBooleanMap<T>> finisher() {
                    return t -> t;
                }

                @Override
                public Set<Characteristics> characteristics() {
                    return Set.of(Characteristics.CONCURRENT, Characteristics.IDENTITY_FINISH, Characteristics.UNORDERED);
                }
            };
        }
    }
    
    record MethodReference(MethodInfo methodInfo, Expression scope, Expression other) implements Expression {

        @Override
        public List<? extends Expression> subElements() {
            return List.of(this);
        }

        public UpgradableBooleanMap<TypeInfo> notTypesReferenced() {
            if (!methodInfo.name.startsWith("b")) return UpgradableBooleanMap.of(scope.typesReferenced());
            return UpgradableBooleanMap.of(other.typesReferenced(), scope.typesReferenced());
        }
    }
}
