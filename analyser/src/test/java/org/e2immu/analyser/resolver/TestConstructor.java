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
import org.e2immu.analyser.model.expression.ConstructorCall;
import org.e2immu.analyser.model.expression.MethodCall;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.statement.*;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.TypeMap;
import org.e2immu.analyser.resolver.testexample.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;


public class TestConstructor extends CommonTest {

    @Test
    public void test_0() throws IOException {
        TypeMap typeMap = inspectAndResolve(Constructor_0.class);
        TypeInfo typeInfo = typeMap.get(Constructor_0.class);
        assertNotNull(typeInfo);
    }

    @Test
    public void test_1() throws IOException {
        TypeMap typeMap = inspectAndResolve(Constructor_1.class);
        TypeInfo typeInfo = typeMap.get(Constructor_1.class);
        assertNotNull(typeInfo);

        TypeInfo parameterized = typeMap.getTypeInspection(typeInfo).subTypes().get(0);
        assertNotNull(parameterized);

        MethodInfo constructor = parameterized.findConstructor(2);
        assertNotNull(constructor);
        assertEquals("org.e2immu.analyser.resolver.testexample.Constructor_1.Parametrized.Parametrized(T,java.util.List<T>)",
                constructor.fullyQualifiedName);
        MethodInspection constructorInspection = typeMap.getMethodInspection(constructor);
        TypeParameter t = constructorInspection.getTypeParameters().get(0);
        assertEquals("T as #0 in org.e2immu.analyser.resolver.testexample.Constructor_1.Parametrized.Parametrized(T,java.util.List<T>)", t.toString());
        assertSame(constructor, t.getOwner().getRight());
    }

    @Test
    public void test_2() throws IOException {
        TypeMap typeMap = inspectAndResolve(Constructor_2.class);
        TypeInfo typeInfo = typeMap.get(Constructor_2.class);
        assertNotNull(typeInfo);
    }

    @Test
    public void test_3() throws IOException {
        TypeMap typeMap = inspectAndResolve(Constructor_3.class);
        TypeInfo typeInfo = typeMap.get(Constructor_3.class);
        assertNotNull(typeInfo);
    }


    @Test
    public void test_4() throws IOException {
        TypeMap typeMap = inspectAndResolve(Constructor_4.class);
        TypeInfo typeInfo = typeMap.get(Constructor_4.class);
        assertNotNull(typeInfo);
    }

    @Test
    public void test_5() throws IOException {
        TypeMap typeMap = inspectAndResolve(Constructor_5.class);
        TypeInfo typeInfo = typeMap.get(Constructor_5.class);
        assertNotNull(typeInfo);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("method", 1);
        Block block = typeMap.getMethodInspection(methodInfo).getMethodBody();
        if (block.structure.statements().get(1) instanceof IfElseStatement ifElse) {
            if (ifElse.structure.block().structure.statements().get(1) instanceof ExpressionAsStatement eas) {
                if (eas.expression instanceof MethodCall methodCall) {
                    Expression p0 = methodCall.parameterExpressions.get(0);
                    if (p0 instanceof ConstructorCall constructorCall) {
                        TypeInfo subType = constructorCall.anonymousClass();
                        MethodInfo get = subType.findUniqueMethod("get", 0);
                        assertNotNull(get);
                        Block getBlock = typeMap.getMethodInspection(get).getMethodBody();
                        if (getBlock.structure.statements().get(0) instanceof ReturnStatement rs) {
                            assertEquals("\"abc \"+s.toLowerCase()", rs.expression.toString());
                        } else fail();
                    } else fail("Have " + p0.getClass());
                } else fail();
            } else fail();
        } else fail();
    }

    @Test
    public void test_6() throws IOException {
        inspectAndResolve(Constructor_6.class);
    }


    @Test
    public void test_7() throws IOException {
        inspectAndResolve(Constructor_7.class);
    }

    @Test
    public void test_8() throws IOException {
        TypeMap typeMap = inspectAndResolve(Constructor_8.class);
        TypeInfo typeInfo = typeMap.get(Constructor_8.class);
        assertNotNull(typeInfo);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("method", 0);
        Block block = typeMap.getMethodInspection(methodInfo).getMethodBody();
        if (block.structure.statements().get(0) instanceof StatementWithExpression eas) {
            if (eas.expression instanceof MethodCall mc) {
                if (mc.object instanceof VariableExpression ve) {
                    if (ve.variable() instanceof FieldReference fr) {
                        assertEquals("new Pair<>(\"3\",3)", fr.scope().toString());
                        // important: test that the 2nd type parameter is Integer rather than int
                        assertEquals("org.e2immu.analyser.resolver.testexample.Constructor_8.Pair<String,Integer>",
                                fr.scope().returnType().detailedString(typeMap));
                    }
                } else fail();
            } else fail();
        } else fail();
    }


    @Test
    public void test_9() throws IOException {
        inspectAndResolve(Constructor_9.class);
    }

    @Test
    public void test_10() throws IOException {
        TypeMap typeMap = inspectAndResolve(Constructor_10Scope.class);
        TypeInfo typeInfo = typeMap.get(Constructor_10Scope.class);
        assertNotNull(typeInfo);

        MethodInfo copy = typeInfo.findUniqueMethod("copy", 1);
        Block copyBlock = typeMap.getMethodInspection(copy).getMethodBody();
        ReturnStatement copyRs = (ReturnStatement) copyBlock.structure.statements().get(0);
        ConstructorCall copyCC = (ConstructorCall) copyRs.expression;
        assertEquals("c", copyCC.scope().toString());

        MethodInfo getSub = typeInfo.findUniqueMethod("getSub", 1);
        Block getSubBlock = typeMap.getMethodInspection(getSub).getMethodBody();
        ReturnStatement getSubRs = (ReturnStatement) getSubBlock.structure.statements().get(0);
        ConstructorCall getSubCC = (ConstructorCall) getSubRs.expression;
        assertEquals("this", getSubCC.scope().toString());
        assertTrue(getSubCC.scope() instanceof VariableExpression ve && ve.variable() instanceof This);
    }

    @Test
    public void test_11() throws IOException {
        TypeMap typeMap = inspectAndResolve(Constructor_11CommonConstructorBlock.class);
        TypeInfo typeInfo = typeMap.get(Constructor_11CommonConstructorBlock.class);
        assertNotNull(typeInfo);

        // TODO https://github.com/e2immu/e2immu/issues/57
    }

    @Test
    public void test_12() throws IOException {
        TypeMap typeMap = inspectAndResolve(Constructor_12DoubleBrace.class);
        TypeInfo typeInfo = typeMap.get(Constructor_12DoubleBrace.class);
        assertNotNull(typeInfo);

        // TODO
    }

    @Test
    public void test_13() throws IOException {
        TypeMap typeMap = inspectAndResolve(null, Constructor_13A.class, Constructor_13B.class);
        TypeInfo typeInfo = typeMap.get(Constructor_13B.class);
        assertNotNull(typeInfo);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("method", 0);
        Block getSubBlock = typeMap.getMethodInspection(methodInfo).getMethodBody();
        assertEquals("{(a.new Inner()).value=3;map.put(\"key\",a.new Inner());}", getSubBlock.minimalOutput());
    }

    @Test
    public void test_14() throws IOException {
        inspectAndResolve(Constructor_14.class);
    }

    @Test
    public void test_15() throws IOException {
        inspectAndResolve(Constructor_15.class);
    }

    @Test
    public void test_16() throws IOException {
        inspectAndResolve(Constructor_16.class);
    }
}
