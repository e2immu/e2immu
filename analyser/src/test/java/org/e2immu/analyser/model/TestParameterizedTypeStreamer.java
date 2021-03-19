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

package org.e2immu.analyser.model;

import org.e2immu.analyser.inspector.TypeInspectionImpl;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestParameterizedTypeStreamer {

    @BeforeAll
    public static void beforeClass() {
        Logger.activate();
    }

    static class Clazz<T> {
        private final T t;

        public Clazz(T t) {
            this.t = t;
        }

        class Sub<S> {
            private final S s;

            public Sub(S s) {
                this.s = s;
            }

            public S getS() {
                return s;
            }

            @Override
            public String toString() {
                return s + "=" + t;
            }
        }

        public T getT() {
            return t;
        }
    }

    @Test
    public void test() {
        Clazz<Integer> clazz = new Clazz<>(3);
        Clazz<Integer>.Sub<Character> sub = clazz.new Sub<Character>('a');
        assertEquals("a=3", sub.toString());
    }

    @Test
    public void testClazzTSSub() {
        Primitives primitives = new Primitives();
        TypeInfo clazz = new TypeInfo("a.b", "Clazz");
        TypeParameter t = new TypeParameterImpl(clazz, "T", 0);
        TypeParameter s = new TypeParameterImpl(clazz, "S", 1);
        TypeInspectionImpl.Builder clazzInspection = new TypeInspectionImpl.Builder(clazz, TypeInspectionImpl.InspectionState.BY_HAND)
                .setParentClass(primitives.objectParameterizedType)
                .addTypeParameter(t)
                .addTypeParameter(s);
        clazz.typeInspection.set(clazzInspection.build());
        ParameterizedType clazzTS = new ParameterizedType(clazz, List.of(
                new ParameterizedType(t, 0, ParameterizedType.WildCard.NONE),
                new ParameterizedType(s, 0, ParameterizedType.WildCard.NONE)));
        assertEquals("a.b.Clazz<T,S>", clazzTS.detailedString());

        TypeInfo sub = new TypeInfo(clazz, "Sub");

        TypeInspectionImpl.Builder subInspection = new TypeInspectionImpl.Builder(sub, TypeInspectionImpl.InspectionState.BY_HAND)
                .setParentClass(primitives.objectParameterizedType);
        sub.typeInspection.set(subInspection.build());
        ParameterizedType clazzTSubS = new ParameterizedType(sub, List.of(
                new ParameterizedType(t, 0, ParameterizedType.WildCard.NONE),
                new ParameterizedType(s, 0, ParameterizedType.WildCard.NONE)));
        assertEquals("a.b.Clazz<T,S>.Sub", clazzTSubS.detailedString());
    }

    @Test
    public void testClazzTSubS() {
        Primitives primitives = new Primitives();
        TypeInfo clazz = new TypeInfo("a.b", "Clazz");
        TypeParameter t = new TypeParameterImpl(clazz, "T", 0);
        TypeInspectionImpl.Builder clazzInspection = new TypeInspectionImpl.Builder(clazz, TypeInspectionImpl.InspectionState.BY_HAND)
                .setParentClass(primitives.objectParameterizedType)
                .addTypeParameter(t);
        clazz.typeInspection.set(clazzInspection.build());
        ParameterizedType clazzT = new ParameterizedType(clazz, List.of(new ParameterizedType(t, 0, ParameterizedType.WildCard.NONE)));
        assertEquals("a.b.Clazz<T>", clazzT.detailedString());

        TypeInfo sub = new TypeInfo(clazz, "Sub");
        TypeParameter s = new TypeParameterImpl(sub, "S", 0);
        TypeInspectionImpl.Builder subInspection = new TypeInspectionImpl.Builder(sub, TypeInspectionImpl.InspectionState.BY_HAND)
                .setParentClass(primitives.objectParameterizedType)
                .addTypeParameter(s);
        sub.typeInspection.set(subInspection.build());
        ParameterizedType clazzTSubS = new ParameterizedType(sub, List.of(
                new ParameterizedType(t, 0, ParameterizedType.WildCard.NONE),
                new ParameterizedType(s, 0, ParameterizedType.WildCard.NONE)));
        assertEquals("a.b.Clazz<T>.Sub<S>", clazzTSubS.detailedString());
    }
}
