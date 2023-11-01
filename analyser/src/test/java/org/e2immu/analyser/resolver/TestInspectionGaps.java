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


import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.parser.TypeMap;
import org.e2immu.analyser.resolver.testexample.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.e2immu.analyser.resolver.TestImport.A;
import static org.junit.jupiter.api.Assertions.assertNotNull;

// collection of earlier tests

public class TestInspectionGaps extends CommonTest {

    @Test
    public void test_1() throws IOException {
        TypeMap typeMap = inspectAndResolve(InspectionGaps_1.class);
        TypeInfo typeInfo = typeMap.get(InspectionGaps_1.class);
        assertNotNull(typeInfo);
    }

    @Test
    public void test_2() throws IOException {
        TypeMap typeMap = inspectAndResolve(InspectionGaps_2.class);
        TypeInfo typeInfo = typeMap.get(InspectionGaps_2.class);
        assertNotNull(typeInfo);
    }

    @Test
    public void test_3() throws IOException {
        TypeMap typeMap = inspectAndResolve(InspectionGaps_3.class);
        TypeInfo typeInfo = typeMap.get(InspectionGaps_3.class);
        assertNotNull(typeInfo);
    }

    @Test
    public void test_4() throws IOException {
        TypeMap typeMap = inspectAndResolve(InspectionGaps_4.class);
        TypeInfo typeInfo = typeMap.get(InspectionGaps_4.class);
        assertNotNull(typeInfo);
    }

    @Test
    public void test_5() throws IOException {
        TypeMap typeMap = inspectAndResolve(InspectionGaps_5.class);
        TypeInfo typeInfo = typeMap.get(InspectionGaps_5.class);
        assertNotNull(typeInfo);
    }

    @Test
    public void test_6() throws IOException {
        TypeMap typeMap = inspectAndResolve(InspectionGaps_6.class);
        TypeInfo typeInfo = typeMap.get(InspectionGaps_6.class);
        assertNotNull(typeInfo);
    }

    @Test
    public void test_7() throws IOException {
        TypeMap typeMap = inspectAndResolve(InspectionGaps_7.class);
        TypeInfo typeInfo = typeMap.get(InspectionGaps_7.class);
        assertNotNull(typeInfo);
    }

    @Test
    public void test_8() throws IOException {
        TypeMap typeMap = inspectAndResolve(InspectionGaps_8.class);
        TypeInfo typeInfo = typeMap.get(InspectionGaps_8.class);
        assertNotNull(typeInfo);
    }

    @Test
    public void test_9() throws IOException {
        TypeMap typeMap = inspectAndResolve(A, InspectionGaps_9.class);
        TypeInfo typeInfo = typeMap.get(InspectionGaps_9.class);
        assertNotNull(typeInfo);
    }

    @Test
    public void test_10() throws IOException {
        TypeMap typeMap = inspectAndResolve(InspectionGaps_10.class);
        TypeInfo typeInfo = typeMap.get(InspectionGaps_10.class);
        assertNotNull(typeInfo);
    }

    @Test
    public void test_11() throws IOException {
        TypeMap typeMap = inspectAndResolve(InspectionGaps_11.class);
        TypeInfo typeInfo = typeMap.get(InspectionGaps_11.class);
        assertNotNull(typeInfo);
    }

    @Test
    public void test_12() throws IOException {
        TypeMap typeMap = inspectAndResolve(A, InspectionGaps_12.class);
        TypeInfo typeInfo = typeMap.get(InspectionGaps_12.class);
        assertNotNull(typeInfo);
    }

    @Test
    public void test_13() throws IOException {
        TypeMap typeMap = inspectAndResolve(A, InspectionGaps_13.class);
        TypeInfo typeInfo = typeMap.get(InspectionGaps_13.class);
        assertNotNull(typeInfo);
    }

    @Test
    public void test_14() throws IOException {
        TypeMap typeMap = inspectAndResolve(InspectionGaps_14.class);
        TypeInfo typeInfo = typeMap.get(InspectionGaps_14.class);
        assertNotNull(typeInfo);
    }
}
