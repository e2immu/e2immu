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

import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.config.InputConfiguration;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.parser.Parser;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class TestIsAssignableFrom {

    public static final String JAVA_LANG_CHAR_SEQUENCE = "java.lang.CharSequence";
    public static final String JAVA_LANG_STRING = "java.lang.String";
    public static final String JAVA_LANG_INTEGER = "java.lang.Integer";
    public static final String JAVA_UTIL_LIST = "java.util.List";
    private static TypeContext typeContext;
    private static Primitives primitives;

    @BeforeAll
    public static void beforeClass() throws IOException {
        org.e2immu.analyser.util.Logger.activate(Logger.LogTarget.INSPECTOR,
                Logger.LogTarget.BYTECODE_INSPECTOR);
        InputConfiguration inputConfiguration = new InputConfiguration.Builder()
                .addClassPath(InputConfiguration.DEFAULT_CLASSPATH)
                .build();
        Configuration configuration = new Configuration.Builder()
                .setInputConfiguration(inputConfiguration)
                .build();
        Parser parser = new Parser(configuration);
        typeContext = parser.getTypeContext();
        primitives = typeContext.getPrimitives();
        parser.getByteCodeInspector().inspectFromPath("java/util/List");
        parser.getByteCodeInspector().inspectFromPath("java/util/LinkedList");
    }

    // int <- String should fail, int <- Integer should not
    @Test
    public void test() {
        ParameterizedType stringPt = type(JAVA_LANG_STRING);
        ParameterizedType integerPt = type(JAVA_LANG_INTEGER);

        assertTrue(integerPt.isAssignableFrom(typeContext, primitives.intParameterizedType));
        assertTrue(primitives.intParameterizedType.isAssignableFrom(typeContext, primitives.intParameterizedType));
        assertTrue(primitives.intParameterizedType.isAssignableFrom(typeContext, integerPt));

        assertFalse(primitives.intParameterizedType.isAssignableFrom(typeContext, stringPt));
        assertFalse(stringPt.isAssignableFrom(typeContext, primitives.intParameterizedType));
    }

    // CharSequence[] <- String[] should be allowed
    @Test
    public void testArray() {
        ParameterizedType stringPt = type(JAVA_LANG_STRING);
        ParameterizedType charSeqPt = type(JAVA_LANG_CHAR_SEQUENCE);
        assertFalse(stringPt.isAssignableFrom(typeContext, charSeqPt));
        assertTrue(charSeqPt.isAssignableFrom(typeContext, stringPt));

        ParameterizedType stringArrayPt = new ParameterizedType(Objects.requireNonNull(typeContext.typeMapBuilder.get(JAVA_LANG_STRING)), 1);
        ParameterizedType charSeqArrayPt = new ParameterizedType(Objects.requireNonNull(typeContext.typeMapBuilder.get(JAVA_LANG_CHAR_SEQUENCE)), 1);

        CharSequence[] sequences = new String[]{"a", "b"};
        for (CharSequence sequence : sequences) {
            assertEquals(1, sequence.length());
        }
        assertFalse(stringArrayPt.isAssignableFrom(typeContext, charSeqArrayPt));
        assertTrue(charSeqArrayPt.isAssignableFrom(typeContext, stringArrayPt));
        assertTrue(charSeqArrayPt.isAssignableFrom(typeContext, ParameterizedType.NULL_CONSTANT));
    }

    // String <- null should be allowed, but int <- null should fail
    @Test
    public void testNull() {
        ParameterizedType stringPt = type(JAVA_LANG_STRING);

        assertFalse(ParameterizedType.NULL_CONSTANT.isAssignableFrom(typeContext, stringPt));
        assertTrue(stringPt.isAssignableFrom(typeContext, ParameterizedType.NULL_CONSTANT));

        assertFalse(ParameterizedType.NULL_CONSTANT.isAssignableFrom(typeContext, primitives.intParameterizedType));
        assertFalse(primitives.intParameterizedType.isAssignableFrom(typeContext, ParameterizedType.NULL_CONSTANT));
    }

    // List<String>, LinkedList<String>
    @Test
    public void testTypeParameters1() {
        ParameterizedType stringPt = type(JAVA_LANG_STRING);
        ParameterizedType listString = new ParameterizedType(typeContext.typeMapBuilder.get(List.class), List.of(stringPt));
        ParameterizedType linkedListString = new ParameterizedType(typeContext.typeMapBuilder.get(LinkedList.class), List.of(stringPt));

        assertTrue(listString.isAssignableFrom(typeContext, linkedListString));
        assertFalse(linkedListString.isAssignableFrom(typeContext, listString));
        assertTrue(primitives.objectParameterizedType.isAssignableFrom(typeContext, stringPt));
        assertTrue(primitives.objectParameterizedType.isAssignableFrom(typeContext, linkedListString));
    }

    // E <- String, E <- Integer, E <- int, E <- int[] should work
    // String <- E, Integer <- E, int <- E should fail
    @Test
    public void testTypeParameters2() {
        ParameterizedType stringPt = type(JAVA_LANG_STRING);
        ParameterizedType integerPt = type(JAVA_LANG_INTEGER);
        ParameterizedType listPt = type(JAVA_UTIL_LIST);
        ParameterizedType typeParam = listPt.parameters.get(0);
        assertNotNull(typeParam);

        assertTrue(typeParam.isAssignableFrom(typeContext, stringPt));
        assertFalse(stringPt.isAssignableFrom(typeContext, typeParam));

        assertTrue(typeParam.isAssignableFrom(typeContext, integerPt));
        assertFalse(integerPt.isAssignableFrom(typeContext, typeParam));

        assertTrue(typeParam.isAssignableFrom(typeContext, primitives.intParameterizedType));
        assertFalse(primitives.intParameterizedType.isAssignableFrom(typeContext, typeParam));
    }

    private ParameterizedType type(String name) {
        TypeInfo typeInfo = Objects.requireNonNull(typeContext.typeMapBuilder.get(name), "Cannot find " + name);
        return Objects.requireNonNull(typeInfo.asParameterizedType(typeContext));
    }

    @Test
    public void testReverse() {
        ParameterizedType stringPt = type(JAVA_LANG_STRING);
        ParameterizedType listPt = type(JAVA_UTIL_LIST);
        ParameterizedType typeParam = listPt.parameters.get(0);

        assertFalse(stringPt.isAssignableFrom(typeContext, typeParam));
        assert typeParam.typeParameter != null;
        assertEquals(100, stringPt.numericIsAssignableFrom(typeContext, typeParam, false,
                ParameterizedType.Mode.COVARIANT, Set.of(typeParam.typeParameter)));
    }
}
