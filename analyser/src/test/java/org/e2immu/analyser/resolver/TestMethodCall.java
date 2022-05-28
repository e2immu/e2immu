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
import org.e2immu.analyser.model.expression.Lambda;
import org.e2immu.analyser.model.expression.LocalVariableCreation;
import org.e2immu.analyser.model.expression.MethodCall;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.statement.ExpressionAsStatement;
import org.e2immu.analyser.model.statement.ForEachStatement;
import org.e2immu.analyser.parser.TypeMap;
import org.e2immu.analyser.resolver.testexample.*;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class TestMethodCall extends CommonTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestMethodCall.class);

    private void inspectAndResolve(Class<?> clazz, String formalParameterType) throws IOException {
        TypeMap typeMap = inspectAndResolve(clazz);
        TypeInfo typeInfo = typeMap.get(clazz);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("test", 0);
        Block block = methodInfo.methodInspection.get().getMethodBody();
        if (block.structure.statements().get(0) instanceof ExpressionAsStatement eas) {
            if (eas.expression instanceof MethodCall methodCall) {
                LOGGER.info("Accept: {}", methodCall);
                ParameterInfo p0 = methodCall.methodInfo.methodInspection.get().getParameters().get(0);
                assertEquals(formalParameterType, p0.parameterizedType.toString());
            } else fail();
        } else fail();
    }

    private void inspectAndResolve(Class<?> clazz, String[] methodFqns, int paramsOfTest) throws IOException {
        TypeMap typeMap = inspectAndResolve(clazz);
        TypeInfo typeInfo = typeMap.get(clazz);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("test", paramsOfTest);
        Block block = methodInfo.methodInspection.get().getMethodBody();
        int i = 0;
        for (Statement statement : block.structure.statements()) {
            if (statement instanceof ExpressionAsStatement eas) {
                if (eas.expression instanceof MethodCall methodCall) {
                    LOGGER.info("Accept: {}", methodCall);
                    String fqn = methodFqns[i++];
                    assertEquals(methodCall.methodInfo.fullyQualifiedName, fqn);
                } // else skip
            } // else skip
        }
    }

    @Test
    public void test_0() throws IOException {
        inspectAndResolve(MethodCall_0.class,
                "Type java.util.List<org.e2immu.analyser.resolver.testexample.MethodCall_0.Get>");
    }

    @Test
    public void test_1() throws IOException {
        inspectAndResolve(MethodCall_1.class,
                "Type java.util.List<org.e2immu.analyser.resolver.testexample.MethodCall_1.Get>");
    }

    @Test
    public void test_2() throws IOException {
        inspectAndResolve(MethodCall_2.class,
                "Type java.util.List<org.e2immu.analyser.resolver.testexample.MethodCall_2.Get>");
    }

    @Test
    public void test_3() throws IOException {
        inspectAndResolve(MethodCall_3.class,
                "Type java.util.List<org.e2immu.analyser.resolver.testexample.MethodCall_3.Get>");
    }

    @Test
    public void test_4() throws IOException {
        inspectAndResolve(MethodCall_4.class);
    }


    @Test
    public void test_5() throws IOException {
        // ensure we've got the one with List, and not the one with Collection!
        inspectAndResolve(MethodCall_5.class,
                "Type java.util.List<org.e2immu.analyser.resolver.testexample.MethodCall_5.Get>");
    }

    @Test
    public void test_6() throws IOException {
        String[] methods = {
                "org.e2immu.analyser.resolver.testexample.MethodCall_6.method(java.util.function.Function<org.e2immu.analyser.resolver.testexample.MethodCall_6.B,org.e2immu.analyser.resolver.testexample.MethodCall_6.A>,org.e2immu.analyser.resolver.testexample.MethodCall_6.B)",
                "org.e2immu.analyser.resolver.testexample.MethodCall_6.method(java.util.function.Function<org.e2immu.analyser.resolver.testexample.MethodCall_6.A,org.e2immu.analyser.resolver.testexample.MethodCall_6.B>,org.e2immu.analyser.resolver.testexample.MethodCall_6.A)"};
        inspectAndResolve(MethodCall_6.class, methods, 0);
    }

    @Test
    public void test_7() throws IOException {
        String[] methods = {
                "org.e2immu.analyser.resolver.testexample.MethodCall_7.method(java.util.List<B>,java.util.function.Consumer<B>)",
                "org.e2immu.analyser.resolver.testexample.MethodCall_7.method(java.util.List<A>,java.util.function.BiConsumer<A,B>)"
        };
        inspectAndResolve(MethodCall_7.class, methods, 2);
    }

    @Test
    public void test_8() throws IOException {
        String[] methods = {
                "org.e2immu.analyser.resolver.testexample.MethodCall_8.method(java.util.List<A>,java.util.List<A>,java.util.List<A>)",
                "org.e2immu.analyser.resolver.testexample.MethodCall_8.method(java.util.List<B>,java.util.Set<A>,java.util.List<B>)",
                "org.e2immu.analyser.resolver.testexample.MethodCall_8.method(java.util.Set<A>,java.util.List<B>,java.util.List<B>)"};
        inspectAndResolve(MethodCall_8.class, methods, 2);
    }

    @Test
    public void test_9() throws IOException {
        inspectAndResolve(MethodCall_9.class,
                "Type java.util.Collection<org.e2immu.analyser.resolver.testexample.MethodCall_9.Get>");
    }

    @Test
    public void test_10() throws IOException {
        inspectAndResolve(MethodCall_10.class);
    }


    @Test
    public void test_11() throws IOException {
        inspectAndResolve(MethodCall_11.class);
    }

    @Test
    public void test_12() throws IOException {
        inspectAndResolve(MethodCall_12.class);
    }

    @Test
    public void test_13() throws IOException {
        inspectAndResolve(MethodCall_13.class);
    }

    @Test
    public void test_14() throws IOException {
        inspectAndResolve(MethodCall_14.class);
    }

    @Test
    public void test_15() throws IOException {
        inspectAndResolve(MethodCall_15.class, "Type java.lang.String");
    }

    @Test
    public void test_16() throws IOException {
        inspectAndResolve(MethodCall_16.class);
    }

    @Test
    public void test_17() throws IOException {
        inspectAndResolve(MethodCall_17.class);
    }

    @Test
    public void test_18() throws IOException {
        TypeMap typeMap = inspectAndResolve(MethodCall_18.class);
        TypeInfo typeInfo = typeMap.get(MethodCall_18.class);
        MethodInfo method1 = typeInfo.findUniqueMethod("method1", 0);
        testConcreteReturnType(method1, "Type int");
        MethodInfo method2 = typeInfo.findUniqueMethod("method2", 0);
        testConcreteReturnType(method2, "Type java.lang.String[]");
        MethodInfo method3 = typeInfo.findUniqueMethod("method3", 0);
        testConcreteReturnType(method3, "Type java.lang.Integer");
    }

    private void testConcreteReturnType(MethodInfo method1, String expected) {
        Block b1 = method1.methodInspection.get().getMethodBody();
        LocalVariableCreation a1 = (LocalVariableCreation) (((ExpressionAsStatement) b1.structure.getStatements().get(0)).expression);
        Lambda l1 = (Lambda) (a1.declarations.get(0).expression());
        Block bl1 = l1.methodInfo.methodInspection.get().getMethodBody();
        LocalVariableCreation al1 = (LocalVariableCreation) (((ExpressionAsStatement) bl1.structure.getStatements().get(0)).expression);
        MethodCall mc1 = (MethodCall) (al1.declarations.get(0).expression());
        assertEquals(expected, mc1.concreteReturnType.toString());
    }

    @Test
    public void test_19() throws IOException {
        inspectAndResolve(MethodCall_19.class);
    }

    @Test
    public void test_20() throws IOException {
        TypeMap typeMap = inspectAndResolve(MethodCall_20.class);
        TypeInfo typeInfo = typeMap.get(MethodCall_20.class);
        MethodInfo method = typeInfo.findUniqueMethod("method", 0);
        Block block = method.methodInspection.get().getMethodBody();
        ForEachStatement forEach = (ForEachStatement) block.structure.getStatements().get(1);
        ParameterizedType pt = forEach.expression.returnType();
        assertEquals("Type java.util.List<java.lang.String>", pt.toString());
        if (forEach.expression instanceof MethodCall mc) {
            assertEquals(pt, mc.returnType());
        } else fail();
    }
}
