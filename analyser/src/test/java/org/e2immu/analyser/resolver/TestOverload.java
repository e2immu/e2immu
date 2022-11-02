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


import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.BinaryOperator;
import org.e2immu.analyser.model.expression.MethodCall;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.statement.ExpressionAsStatement;
import org.e2immu.analyser.model.statement.IfElseStatement;
import org.e2immu.analyser.parser.TypeMap;
import org.e2immu.analyser.resolver.testexample.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;


public class TestOverload extends CommonTest {

    @Test
    public void test_0() throws IOException {
        TypeMap typeMap = inspectAndResolve(Overload_0.class);
        TypeInfo typeInfo = typeMap.get(Overload_0.class);
        assertNotNull(typeInfo);
    }

    @Test
    public void test_1() throws IOException {
        TypeMap typeMap = inspectAndResolve(Overload_1.class);
        TypeInfo typeInfo = typeMap.get(Overload_1.class);
        assertNotNull(typeInfo);
    }

    @Test
    public void test_2() throws IOException {
        TypeMap typeMap = inspectAndResolve(Overload_2.class);
        TypeInfo typeInfo = typeMap.get(Overload_2.class);
        assertNotNull(typeInfo);
        TypeInfo i2 = typeMap.get(Overload_2.class.getCanonicalName() + ".I2");
        TypeInspection i2i = typeMap.getTypeInspection(i2);
        assertTrue(i2i.isFunctionalInterface());
    }


    @Test
    public void test_3() throws IOException {
        TypeMap typeMap = inspectAndResolve(Overload_3.class);
        TypeInfo typeInfo = typeMap.get(Overload_3.class);
        assertNotNull(typeInfo);
        TypeInfo i2 = typeMap.get(Overload_3.class.getCanonicalName() + ".I2");
        TypeInspection i2i = typeMap.getTypeInspection(i2);
        assertTrue(i2i.isFunctionalInterface());
    }

    @Test
    public void test_4() throws IOException {
        inspectAndResolve(Overload_4.class);
    }

    @Test
    public void test_5() throws IOException {
        TypeMap typeMap = inspectAndResolve(Overload_5.class);
        TypeInfo typeInfo = typeMap.get(Overload_5.class);
        assertNotNull(typeInfo);
        MethodInfo test = typeInfo.findUniqueMethod("test", 0);
        Block block = test.methodInspection.get().getMethodBody();
        IfElseStatement ifStatement = (IfElseStatement) block.structure.statements().get(1);
        Expression expression = ifStatement.expression;
        MethodCall mc = (MethodCall) ((BinaryOperator) expression).lhs;
        MethodInfo methodInfo = mc.methodInfo;
        // we'd rather not have java.lang.AbstractStringBuilder.length(), because that method is not accessible,
        // and we decorated the one in CharSequence
        assertEquals("java.lang.CharSequence.length()", methodInfo.fullyQualifiedName);
    }

    @Test
    public void test_6() throws IOException {
        TypeMap typeMap = inspectAndResolve(Overload_6.class);
        TypeInfo typeInfo = typeMap.get(CharSequence.class);
        TypeInspection typeInspection = typeMap.getTypeInspection(typeInfo);
        assertFalse(typeInspection.isFunctionalInterface());
        assertTrue(typeInspection.isInterface());
        assertTrue(typeInspection.isPublic());
        assertTrue(typeInspection.isStatic());
        assertFalse(typeInspection.isRecord());

        MethodInfo methodInfo = typeInfo.findUniqueMethod(typeMap, "length", 0);
        MethodInspection methodInspection = typeMap.getMethodInspection(methodInfo);
        assertTrue(methodInspection.isAbstract());
        assertTrue(methodInspection.isPublic());
        assertFalse(methodInspection.isDefault());
        assertFalse(methodInspection.isStatic());
    }

    @Test
    public void test_6_2() throws IOException {
        TypeMap typeMap = inspectAndResolve(Overload_6.class);
        TypeInfo typeInfo = typeMap.get("java.lang.AbstractStringBuilder");
        TypeInspection typeInspection = typeMap.getTypeInspection(typeInfo);
        assertFalse(typeInspection.isFunctionalInterface());
        assertFalse(typeInspection.isInterface());
        assertFalse(typeInspection.isPublic());
        assertTrue(typeInspection.isStatic());
        assertFalse(typeInspection.isRecord());

        MethodInfo methodInfo = typeInfo.findUniqueMethod(typeMap, "length", 0);
        MethodInspection methodInspection = typeMap.getMethodInspection(methodInfo);
        assertFalse(methodInspection.isAbstract());
        assertTrue(methodInspection.isPublic()); // FIXME but is not accessible...
        assertFalse(methodInspection.isDefault());
        assertFalse(methodInspection.isStatic());
    }

    @Test
    public void test_7() throws IOException {
        TypeMap typeMap = inspectAndResolve(Overload_7.class);
        TypeInfo typeInfo = typeMap.get(Overload_7.class);
        MethodInfo test1 = typeInfo.findUniqueMethod("test1", 0);
        MethodInspection test1inspection = test1.methodInspection.get();
        Block block = test1inspection.getMethodBody();
        Statement s1 = block.structure.statements().get(1);
        if (s1 instanceof ExpressionAsStatement eas) {
            if (eas.expression instanceof MethodCall mc) {
                // ensure we have the method with the type parameter!
                assertEquals("org.e2immu.analyser.resolver.testexample.Overload_7.replace(S)",
                        mc.methodInfo.fullyQualifiedName);
            } else fail();
        } else fail();
    }
}
