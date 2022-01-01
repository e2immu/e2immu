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

package org.e2immu.analyser.model;

import org.e2immu.analyser.inspector.InspectionState;
import org.e2immu.analyser.inspector.TypeInspectionImpl;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.parser.impl.PrimitivesImpl;
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
        Primitives primitives = new PrimitivesImpl();
        TypeInfo clazz = new TypeInfo("a.b", "Clazz");
        TypeParameter t = new TypeParameterImpl(clazz, "T", 0);
        TypeParameter s = new TypeParameterImpl(clazz, "S", 1);
        TypeInspectionImpl.Builder clazzInspection = new TypeInspectionImpl.Builder(clazz, InspectionState.BY_HAND)
                .noParent(primitives)
                .addTypeParameter(t)
                .addTypeParameter(s);
        clazz.typeInspection.set(clazzInspection.build());
        ParameterizedType clazzTS = new ParameterizedType(clazz, List.of(
                new ParameterizedType(t, 0, ParameterizedType.WildCard.NONE),
                new ParameterizedType(s, 0, ParameterizedType.WildCard.NONE)));
        assertEquals("a.b.Clazz<T,S>", clazzTS.detailedString());

        TypeInfo sub = new TypeInfo(clazz, "Sub");

        TypeInspectionImpl.Builder subInspection = new TypeInspectionImpl.Builder(sub, InspectionState.BY_HAND)
                .noParent(primitives);
        sub.typeInspection.set(subInspection.build());
        ParameterizedType clazzTSubS = new ParameterizedType(sub, List.of(
                new ParameterizedType(t, 0, ParameterizedType.WildCard.NONE),
                new ParameterizedType(s, 0, ParameterizedType.WildCard.NONE)));
        assertEquals("a.b.Clazz<T,S>.Sub", clazzTSubS.detailedString());
    }

    @Test
    public void testClazzTSubS() {
        Primitives primitives = new PrimitivesImpl();
        TypeInfo clazz = new TypeInfo("a.b", "Clazz");
        TypeParameter t = new TypeParameterImpl(clazz, "T", 0);
        TypeInspectionImpl.Builder clazzInspection = new TypeInspectionImpl.Builder(clazz, InspectionState.BY_HAND)
                .noParent(primitives)
                .addTypeParameter(t);
        clazz.typeInspection.set(clazzInspection.build());
        ParameterizedType clazzT = new ParameterizedType(clazz, List.of(new ParameterizedType(t, 0, ParameterizedType.WildCard.NONE)));
        assertEquals("a.b.Clazz<T>", clazzT.detailedString());

        TypeInfo sub = new TypeInfo(clazz, "Sub");
        TypeParameter s = new TypeParameterImpl(sub, "S", 0);
        TypeInspectionImpl.Builder subInspection = new TypeInspectionImpl.Builder(sub, InspectionState.BY_HAND)
                .noParent(primitives)
                .addTypeParameter(s);
        sub.typeInspection.set(subInspection.build());
        ParameterizedType clazzTSubS = new ParameterizedType(sub, List.of(
                new ParameterizedType(t, 0, ParameterizedType.WildCard.NONE),
                new ParameterizedType(s, 0, ParameterizedType.WildCard.NONE)));
        assertEquals("a.b.Clazz<T>.Sub<S>", clazzTSubS.detailedString());
    }
}
