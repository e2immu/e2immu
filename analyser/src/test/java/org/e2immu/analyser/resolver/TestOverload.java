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


import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.TypeInspection;
import org.e2immu.analyser.model.expression.BinaryOperator;
import org.e2immu.analyser.model.expression.MethodCall;
import org.e2immu.analyser.model.statement.Block;
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
}
