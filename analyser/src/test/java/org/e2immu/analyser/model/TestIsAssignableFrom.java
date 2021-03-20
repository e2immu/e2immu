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
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

public class TestIsAssignableFrom {

    private static TypeContext typeContext;
    private static Primitives primitives;

    @BeforeAll
    public static void beforeClass() throws IOException {
        org.e2immu.analyser.util.Logger.activate(Logger.LogTarget.INSPECT,
                Logger.LogTarget.BYTECODE_INSPECTOR);
        Parser parser = new Parser();
        typeContext = parser.getTypeContext();
        primitives = typeContext.getPrimitives();
        parser.getByteCodeInspector().inspectFromPath("java/util/List");
    }

    // int <- String should fail, int <- Integer should not
    @Test
    public void test() {
        ParameterizedType stringPt = Objects.requireNonNull(typeContext.typeMapBuilder.get("java.lang.String").asParameterizedType(typeContext));
        ParameterizedType integerPt = Objects.requireNonNull(typeContext.typeMapBuilder.get("java.lang.Integer").asParameterizedType(typeContext));

        assertTrue(integerPt.isAssignableFrom(typeContext, primitives.intParameterizedType));
        assertTrue(primitives.intParameterizedType.isAssignableFrom(typeContext, primitives.intParameterizedType));
        assertTrue(primitives.intParameterizedType.isAssignableFrom(typeContext, integerPt));

        assertFalse(primitives.intParameterizedType.isAssignableFrom(typeContext, stringPt));
        assertFalse(stringPt.isAssignableFrom(typeContext, primitives.intParameterizedType));
    }

    // CharSequence[] <- String[] should be allowed
    @Test
    public void testArray() {
        ParameterizedType stringArrayPt = new ParameterizedType(Objects.requireNonNull(typeContext.typeMapBuilder.get("java.lang.String")), 1);
        ParameterizedType charSeqArrayPt = new ParameterizedType(Objects.requireNonNull(typeContext.typeMapBuilder.get("java.lang.CharSequence")), 1);

        CharSequence[] sequences = new String[]{"a", "b"};
        for (CharSequence sequence : sequences) {
            assertEquals(1, sequence.length());
        }
        assertFalse(stringArrayPt.isAssignableFrom(typeContext, charSeqArrayPt));
        assertTrue(charSeqArrayPt.isAssignableFrom(typeContext, stringArrayPt));
    }

    // String <- null should be allowed, but int <- null should fail
    @Test
    public void testNull() {
        ParameterizedType stringPt = Objects.requireNonNull(typeContext.typeMapBuilder.get("java.lang.String").asParameterizedType(typeContext));

        assertFalse(ParameterizedType.NULL_CONSTANT.isAssignableFrom(typeContext, stringPt));
        assertTrue(stringPt.isAssignableFrom(typeContext, ParameterizedType.NULL_CONSTANT));

        assertFalse(ParameterizedType.NULL_CONSTANT.isAssignableFrom(typeContext, primitives.intParameterizedType));
        assertFalse(primitives.intParameterizedType.isAssignableFrom(typeContext, ParameterizedType.NULL_CONSTANT));
    }

    // E <- String, E <- Integer, E <- int, E <- int[] should work
    @Test
    public void testBoxing() {
        ParameterizedType stringPt = Objects.requireNonNull(typeContext.typeMapBuilder.get("java.lang.String").asParameterizedType(typeContext));
        ParameterizedType integerPt = Objects.requireNonNull(typeContext.typeMapBuilder.get("java.lang.Integer").asParameterizedType(typeContext));
        ParameterizedType listPt = Objects.requireNonNull(typeContext.typeMapBuilder.get("java.util.List").asParameterizedType(typeContext));
        ParameterizedType typeParam = listPt.parameters.get(0);
        assertNotNull(typeParam);

        assertTrue(typeParam.isAssignableFrom(typeContext, stringPt));
        assertFalse(stringPt.isAssignableFrom(typeContext, typeParam));

        assertTrue(typeParam.isAssignableFrom(typeContext, integerPt));
        assertFalse(integerPt.isAssignableFrom(typeContext, typeParam));

        assertTrue(typeParam.isAssignableFrom(typeContext, primitives.intParameterizedType));
        assertFalse(primitives.intParameterizedType.isAssignableFrom(typeContext, typeParam));
    }
}
