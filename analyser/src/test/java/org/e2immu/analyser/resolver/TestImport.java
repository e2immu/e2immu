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

package org.e2immu.analyser.resolver;


import ch.qos.logback.classic.LoggerContext;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.TypeInspection;
import org.e2immu.analyser.parser.TypeMap;
import org.e2immu.analyser.resolver.testexample.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;


public class TestImport extends CommonTest {

    // remember: the . at the end means: also include subpackages.
    public static final String IMPORT_HELPER = "org.e2immu.analyser.resolver.testexample.importhelper.";
    public static final String A = "org.e2immu.analyser.resolver.testexample.a";
    public static final String B = "org.e2immu.analyser.resolver.testexample.b";
    public static final String ACCESS = "org.e2immu.analyser.resolver.testexample.access";

    /**
     * The point of tests 0 and 1 is that Level. is ALWAYS the one in IMPORT_HELPER, and never the one in MultiLevel,
     * because MultiLevel itself has not been imported (only some fields of its nested type, in a static way).
     */
    @Test
    public void test_0() throws IOException {
        TypeMap typeMap = inspectAndResolve(IMPORT_HELPER, Import_0.class);
        TypeInfo typeInfo = typeMap.get(Import_0.class);
        assertNotNull(typeInfo);
    }

    @Test
    public void test_1() throws IOException {
        TypeMap typeMap = inspectAndResolve(IMPORT_HELPER, Import_1.class);
        TypeInfo typeInfo = typeMap.get(Import_1.class);
        assertNotNull(typeInfo);
    }

    @Test
    public void test_2() throws IOException {
        TypeMap typeMap = inspectAndResolve(IMPORT_HELPER, Import_2.class);
        TypeInfo typeInfo = typeMap.get(Import_2.class);
        assertNotNull(typeInfo);
    }

    @Test
    public void test_3() throws IOException {
        inspectAndResolve(IMPORT_HELPER, Import_3.class);
    }

    @Test
    public void test_4() throws IOException {
        inspectAndResolve(IMPORT_HELPER, Import_4.class);
    }

    @Test
    public void test_5() throws IOException {
        inspectAndResolve(IMPORT_HELPER, Import_5.class);
    }

    @Test
    public void test_6() throws IOException {
        TypeMap typeMap = inspectAndResolve(Import_6.class);
        TypeInfo loggerContext = typeMap.get(LoggerContext.class);
        assertNotNull(loggerContext);
        TypeInspection typeInspection = typeMap.getTypeInspection(loggerContext);
        assertTrue(typeInspection.methods().size() > 10);
    }

    @Test
    public void test_7() throws IOException {
        inspectAndResolve(Import_7.class);
    }

    @Test
    public void test_8() throws IOException {
        inspectAndResolve(IMPORT_HELPER, Import_8.class);
    }

    @Test
    public void test_9() throws IOException {
        inspectAndResolve(Import_9.class);
    }

    @Test
    public void test_10() throws IOException {
        inspectAndResolve(IMPORT_HELPER, Import_10.class);
    }

    @Test
    public void test_11() throws IOException {
        inspectAndResolve(A, Import_11.class);
    }

    @Test
    public void test_12() throws IOException {
        inspectAndResolve(ACCESS, Import_12.class);
    }

    @Test
    public void test_13() throws IOException {
        inspectAndResolve(IMPORT_HELPER, Import_13.class);
    }

    @Test
    public void test_14() throws IOException {
        inspectAndResolve(IMPORT_HELPER, Import_14.class);
    }

    @Test
    public void test_15() throws IOException {
        inspectAndResolve(IMPORT_HELPER, Import_15.class);
    }

    @Test
    public void test_16() throws IOException {
        TypeMap typeMap = inspectAndResolve(Import_16.class);
        TypeInfo import16 = typeMap.get(Import_16.class);
        MethodInfo test = import16.findUniqueMethod("method", 2);
        ParameterInfo p1 = test.methodInspection.get().getParameters().get(1);
        assertEquals("Type org.e2immu.analyser.resolver.testexample.Import_16.SortedSet<Integer>",
                p1.parameterizedType.toString());
    }
}
