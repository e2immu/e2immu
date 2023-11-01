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
import org.e2immu.analyser.model.Statement;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.expression.ConstructorCall;
import org.e2immu.analyser.model.statement.ReturnStatement;
import org.e2immu.analyser.parser.TypeMap;
import org.e2immu.analyser.resolver.testexample.SubType_0;
import org.e2immu.analyser.resolver.testexample.SubType_1;
import org.e2immu.analyser.resolver.testexample.importhelper.SubType_2;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/*
tests nested anonymous subtypes, and their static property.
 */
public class TestSubType extends CommonTest {

    @Test
    public void test0() throws IOException {
        TypeMap typeMap = inspectAndResolve(SubType_0.class);
        TypeInfo typeInfo = typeMap.get(SubType_0.class);
        assertNotNull(typeInfo);
        assertTrue(typeInfo.typeInspection.get().isExtensible());

        MethodInfo makeIterator = typeInfo.findUniqueMethod("makeIterator", 1);
        Statement s1 = makeIterator.methodInspection.get().getMethodBody().getStructure().getStatements().get(1);
        if (s1 instanceof ReturnStatement rs1) {
            if (rs1.expression instanceof ConstructorCall cc) {
                TypeInfo st1 = cc.anonymousClass();
                assertEquals("$1", st1.simpleName);
                assertFalse(st1.typeInspection.get().isExtensible());
                assertFalse(st1.typeInspection.get().isStatic());

                MethodInfo iterator = st1.findUniqueMethod("iterator", 0);
                Statement s0 = iterator.methodInspection.get().getMethodBody().getStructure().getStatements().get(0);
                if (s0 instanceof ReturnStatement rs0) {
                    if (rs0.expression instanceof ConstructorCall cc2) {
                        TypeInfo st2 = cc2.anonymousClass();
                        assertEquals("$2", st2.simpleName);
                        assertFalse(st2.typeInspection.get().isExtensible());
                        assertFalse(st2.typeInspection.get().isStatic());

                    } else fail(rs0.getClass().toString());
                } else fail(s0.getClass().toString());
            } else fail(rs1.getClass().toString());
        } else fail(s1.getClass().toString());
    }


    @Test
    public void test1() throws IOException {
        TypeMap typeMap = inspectAndResolve(SubType_1.class);
        TypeInfo typeInfo = typeMap.get(SubType_1.class);
        assertNotNull(typeInfo);
        assertTrue(typeInfo.typeInspection.get().isExtensible());

    }

    @Test
    public void test2() throws IOException {
        TypeMap typeMap = inspectAndResolve(TestImport.IMPORT_HELPER, SubType_2.class);
        TypeInfo typeInfo = typeMap.get(SubType_2.class);
        assertNotNull(typeInfo);
        assertTrue(typeInfo.typeInspection.get().isExtensible());

    }

}
