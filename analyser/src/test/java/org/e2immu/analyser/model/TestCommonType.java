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

import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.parser.Parser;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestCommonType {

    private static TypeContext typeContext;
    private static Primitives primitives;

    @BeforeAll
    public static void beforeClass() throws IOException {
        org.e2immu.analyser.util.Logger.activate(Logger.LogTarget.INSPECT, Logger.LogTarget.BYTECODE_INSPECTOR);
        Parser parser = new Parser();
        typeContext = parser.getTypeContext();
        primitives = typeContext.getPrimitives();
    }

    @Test
    public void testPrimitivesAmongEachOther() {
        assertEquals(primitives.intParameterizedType, primitives.intParameterizedType.commonType(typeContext, primitives.intParameterizedType));
        assertEquals(primitives.doubleParameterizedType, primitives.intParameterizedType.commonType(typeContext, primitives.doubleParameterizedType));
        assertEquals(primitives.doubleParameterizedType, primitives.doubleParameterizedType.commonType(typeContext, primitives.intParameterizedType));
        assertEquals(primitives.intParameterizedType, primitives.intParameterizedType.commonType(typeContext, primitives.shortParameterizedType));
    }

    @Test
    public void testPrimitivesAndNull() {
        assertEquals(primitives.integerTypeInfo.asParameterizedType(typeContext),
                primitives.intParameterizedType.commonType(typeContext, ParameterizedType.NULL_CONSTANT));
        assertEquals(primitives.integerTypeInfo.asParameterizedType(typeContext),
                ParameterizedType.NULL_CONSTANT.commonType(typeContext, primitives.intParameterizedType));
        assertEquals(primitives.boxedBooleanTypeInfo,
                primitives.booleanParameterizedType.commonType(typeContext, ParameterizedType.NULL_CONSTANT).bestTypeInfo());
        assertEquals(primitives.boxedBooleanTypeInfo,
                ParameterizedType.NULL_CONSTANT.commonType(typeContext, primitives.booleanParameterizedType).bestTypeInfo());
    }

    @Test
    public void testStringAndNull() {
        assertEquals(primitives.stringParameterizedType,
                primitives.stringParameterizedType.commonType(typeContext, ParameterizedType.NULL_CONSTANT));
        assertEquals(primitives.stringParameterizedType,
                ParameterizedType.NULL_CONSTANT.commonType(typeContext, primitives.stringParameterizedType));
    }

    @Test
    public void testNullAndNull() {
        assertEquals(ParameterizedType.NULL_CONSTANT,
                ParameterizedType.NULL_CONSTANT.commonType(typeContext, ParameterizedType.NULL_CONSTANT));
    }

    @Test
    public void testIncompatible() {
        assertEquals(primitives.objectParameterizedType,
                primitives.stringParameterizedType.commonType(typeContext, primitives.intParameterizedType));
    }

    @Test
    public void testWithObject() {
        assertEquals(primitives.objectParameterizedType,
                primitives.stringParameterizedType.commonType(typeContext, primitives.objectParameterizedType));
    }
}
