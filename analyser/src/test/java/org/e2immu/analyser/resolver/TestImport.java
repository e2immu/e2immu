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
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.TypeInspection;
import org.e2immu.analyser.parser.TypeMap;
import org.e2immu.analyser.resolver.testexample.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;


public class TestImport extends CommonTest {

    public static final String IMPORT_HELPER = "org.e2immu.analyser.resolver.testexample.importhelper";

    /**
     * The point of tests 0 and 1 is that Level. is ALWAYS the one in importhelper, and never the one in MultiLevel,
     * because MultiLevel itself has not been imported (only some of the fields of its nested type, in a static way).
     */
    @Test
    public void test_0() throws IOException {
        TypeMap typeMap = inspectAndResolve(Import_0.class, IMPORT_HELPER);
        TypeInfo typeInfo = typeMap.get(Import_0.class);
        assertNotNull(typeInfo);
    }

    @Test
    public void test_1() throws IOException {
        TypeMap typeMap = inspectAndResolve(Import_1.class, IMPORT_HELPER);
        TypeInfo typeInfo = typeMap.get(Import_1.class);
        assertNotNull(typeInfo);
    }

    @Test
    public void test_2() throws IOException {
        TypeMap typeMap = inspectAndResolve(Import_2.class, IMPORT_HELPER);
        TypeInfo typeInfo = typeMap.get(Import_2.class);
        assertNotNull(typeInfo);
    }

    @Test
    public void test_3() throws IOException {
        inspectAndResolve(Import_3.class, IMPORT_HELPER);
    }

    @Test
    public void test_4() throws IOException {
        inspectAndResolve(Import_4.class, IMPORT_HELPER);
    }

    @Test
    public void test_5() throws IOException {
        inspectAndResolve(Import_5.class, IMPORT_HELPER);
    }

    @Test
    public void test_6() throws IOException {
        TypeMap typeMap = inspectAndResolve(Import_6.class);
        TypeInfo loggerContext = typeMap.get(LoggerContext.class);
        assertNotNull(loggerContext);
        TypeInspection typeInspection = typeMap.getTypeInspection(loggerContext);
        assertEquals(38, typeInspection.methods().size());
    }

    @Test
    public void test_7() throws IOException {
        inspectAndResolve(Import_7.class);
    }
}
