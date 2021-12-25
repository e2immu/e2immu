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


import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.parser.TypeMap;
import org.e2immu.analyser.resolver.testexample.FieldAccess_0;
import org.e2immu.analyser.resolver.testexample.FieldAccess_1;
import org.e2immu.analyser.resolver.testexample.FieldAccess_2;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestFieldAccess extends CommonTest {

    @Test
    public void test_0() throws IOException {
        inspectAndResolve(FieldAccess_0.class);
    }

    @Test
    public void test_1() throws IOException {
        TypeMap typeMap = inspectAndResolve(FieldAccess_1.class, TestImport.IMPORT_HELPER);
        TypeInfo subType = typeMap.get(FieldAccess_1.ComputedParameterAnalyser.class);
        MethodInfo method = subType.findUniqueMethod("method", 0);
        assertNotNull(method);
    }

    @Test
    public void test_2() throws IOException {
        inspectAndResolve(FieldAccess_2.class);
    }
}
