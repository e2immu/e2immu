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
import org.e2immu.analyser.model.expression.MethodCall;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.statement.ExpressionAsStatement;
import org.e2immu.analyser.parser.TypeMap;
import org.e2immu.analyser.resolver.testexample.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;

import static org.junit.jupiter.api.Assertions.*;


public class TestLambda extends CommonTest {

    @Test
    public void test_0() throws IOException {
        TypeMap typeMap = inspectAndResolve(Lambda_0.class);
        TypeInfo typeInfo = typeMap.get(Lambda_0.class);
        assertNotNull(typeInfo);
        MethodInfo test = typeInfo.findUniqueMethod("test", 1);
        Block block = typeMap.getMethodInspection(test).getMethodBody();
        Statement first = block.structure.statements().get(0);
        List<TypeInfo> lambdas = first.getStructure().findTypeDefinedInStatement();
        assertEquals(1, lambdas.size());
        TypeInfo lambda = lambdas.get(0);
        assertEquals("org.e2immu.analyser.resolver.testexample.Lambda_0.$1", lambda.fullyQualifiedName);
        TypeInspection lambdaInspected = lambda.typeInspection.get();
        assertNotNull(lambdaInspected.methods());
        TypeResolution lambdaResolved = lambda.typeResolution.get();
        assertNotNull(lambdaResolved.sortedType());
    }

    @Test
    public void test_1() throws IOException {
        TypeMap typeMap = inspectAndResolve(Lambda_1.class);
        TypeInfo typeInfo = typeMap.get(Lambda_1.class);
        assertNotNull(typeInfo);
    }

    @Test
    public void test_2() throws IOException {
        TypeMap typeMap = inspectAndResolve(Lambda_2.class);
        TypeInfo typeInfo = typeMap.get(Lambda_2.class);
        assertNotNull(typeInfo);
    }

    @Test
    public void test_3() throws IOException {
        TypeMap typeMap = inspectAndResolve(Lambda_3.class);
        TypeInfo typeInfo = typeMap.get(Lambda_3.class);
        assertNotNull(typeInfo);
    }

    @Test
    public void test_4() throws IOException {
        TypeMap typeMap = inspectAndResolve(Lambda_4.class);
        TypeInfo typeInfo = typeMap.get(Lambda_4.class);
        assertNotNull(typeInfo);
    }

    @Test
    public void test_5() throws IOException {
        TypeMap typeMap = inspectAndResolve(Lambda_5.class);
        TypeInfo typeInfo = typeMap.get(Lambda_5.class);
        assertNotNull(typeInfo);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("method", 1);
        Block block = methodInfo.methodInspection.get().getMethodBody();
        if (block.structure.statements().get(0) instanceof ExpressionAsStatement eas) {
            if (eas.expression instanceof MethodCall forEach) {
                Expression p0 = forEach.parameterExpressions.get(0);
                if (p0 instanceof Lambda lambda) {
                    if (lambda.block.structure.getStatements().get(0) instanceof ExpressionAsStatement s) {
                        assertEquals("System.out.println(\"xx \"+s)", s.expression.toString());
                    }
                } else fail();
            } else fail();
        } else fail();
    }

    @Test
    public void test_6() throws IOException {
        TypeMap typeMap = inspectAndResolve(Lambda_6.class);
        TypeInfo typeInfo = typeMap.get(Lambda_6.class);
        assertNotNull(typeInfo);
    }


    @Test
    public void test_7() throws IOException {
        TypeMap typeMap = inspectAndResolve(Lambda_7.class);
        TypeInfo typeInfo = typeMap.get(Lambda_7.class);
        assertNotNull(typeInfo);

        for (Class<?> clazz : new Class[]{BiConsumer.class, BiFunction.class,
                Comparable.class,
                Comparator.class,
                BinaryOperator.class}) {
            TypeInfo ti = typeMap.get(clazz);
            assertTrue(typeMap.getTypeInspection(ti).isFunctionalInterface(), clazz.toString());
        }
    }

    @Test
    public void test_8() throws IOException {
        TypeMap typeMap = inspectAndResolve(Lambda_8.class);
        TypeInfo typeInfo = typeMap.get(Lambda_8.class);
        assertNotNull(typeInfo);
    }

    @Test
    public void test_9() throws IOException {
        inspectAndResolve(Lambda_9.class);
    }

    @Test
    public void test_10() throws IOException {
        inspectAndResolve(Lambda_10.class);
    }

    @Test
    public void test_11() throws IOException {
        inspectAndResolve(Lambda_11.class);
    }

    @Test
    public void test_12() throws IOException {
        inspectAndResolve(Lambda_12.class);
    }

    @Test
    public void test_13() throws IOException {
        inspectAndResolve(Lambda_13.class);
    }

    @Test
    public void test_14() throws IOException {
        TypeMap typeMap = inspectAndResolve(Lambda_14.class);
        TypeInfo typeInfo = typeMap.get(Lambda_14.class);

        MethodInfo method = typeInfo.findUniqueMethod("methods", 1);
        MethodInspection methodInspection = typeMap.getMethodInspection(method);

        assertTrue(methodInspection.getMethodBody().identifier instanceof Identifier.PositionalIdentifier);
    }

    @Test
    public void test_15() throws IOException {
        inspectAndResolve(Lambda_15.class);
    }
}
