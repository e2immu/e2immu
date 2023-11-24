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

import org.e2immu.analyser.inspector.TypeContext;
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

import static org.e2immu.analyser.resolver.TestImport.A;
import static org.junit.jupiter.api.Assertions.*;

public class TestMethodCall extends CommonTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestMethodCall.class);

    private void localInspectAndResolve(Class<?> clazz, String formalParameterType) throws IOException {
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

    private void localInspectAndResolve(Class<?> clazz, String[] expectedMethodFqns, int paramsOfTest) throws IOException {
        TypeMap typeMap = inspectAndResolve(clazz);
        TypeInfo typeInfo = typeMap.get(clazz);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("test", paramsOfTest);
        Block block = methodInfo.methodInspection.get().getMethodBody();
        int i = 0;
        for (Statement statement : block.structure.statements()) {
            if (statement instanceof ExpressionAsStatement eas) {
                if (eas.expression instanceof MethodCall methodCall) {
                    LOGGER.info("Accept: {}", methodCall);
                    String expectedFqn = expectedMethodFqns[i++];
                    assertEquals(expectedFqn, methodCall.methodInfo.fullyQualifiedName);
                } // else skip
            } // else skip
        }
    }

    @Test
    public void test_0() throws IOException {
        localInspectAndResolve(MethodCall_0.class,
                "Type java.util.List<org.e2immu.analyser.resolver.testexample.MethodCall_0.Get>");
    }

    @Test
    public void test_1() throws IOException {
        localInspectAndResolve(MethodCall_1.class,
                "Type java.util.List<org.e2immu.analyser.resolver.testexample.MethodCall_1.Get>");
    }

    @Test
    public void test_2() throws IOException {
        localInspectAndResolve(MethodCall_2.class,
                "Type java.util.List<org.e2immu.analyser.resolver.testexample.MethodCall_2.Get>");
    }

    @Test
    public void test_3() throws IOException {
        localInspectAndResolve(MethodCall_3.class,
                "Type java.util.List<org.e2immu.analyser.resolver.testexample.MethodCall_3.Get>");
    }

    @Test
    public void test_4() throws IOException {
        inspectAndResolve(MethodCall_4.class);
    }


    @Test
    public void test_5() throws IOException {
        // ensure we've got the one with List, and not the one with Collection!
        localInspectAndResolve(MethodCall_5.class,
                "Type java.util.List<org.e2immu.analyser.resolver.testexample.MethodCall_5.Get>");
    }

    @Test
    public void test_6() throws IOException {
        String[] methods = {
                "org.e2immu.analyser.resolver.testexample.MethodCall_6.method(java.util.function.Function<org.e2immu.analyser.resolver.testexample.MethodCall_6.B,org.e2immu.analyser.resolver.testexample.MethodCall_6.A>,org.e2immu.analyser.resolver.testexample.MethodCall_6.B)",
                "org.e2immu.analyser.resolver.testexample.MethodCall_6.method(java.util.function.Function<org.e2immu.analyser.resolver.testexample.MethodCall_6.A,org.e2immu.analyser.resolver.testexample.MethodCall_6.B>,org.e2immu.analyser.resolver.testexample.MethodCall_6.A)"};
        localInspectAndResolve(MethodCall_6.class, methods, 0);
    }

    @Test
    public void test_7() throws IOException {
        String[] methods = {
                "org.e2immu.analyser.resolver.testexample.MethodCall_7.method(java.util.List<B>,java.util.function.Consumer<B>)",
                "org.e2immu.analyser.resolver.testexample.MethodCall_7.method(java.util.List<A>,java.util.function.BiConsumer<A,B>)"
        };
        localInspectAndResolve(MethodCall_7.class, methods, 2);
    }

    @Test
    public void test_8() throws IOException {
        String[] methods = {
                "org.e2immu.analyser.resolver.testexample.MethodCall_8.method(java.util.List<A>,java.util.List<A>,java.util.List<A>)",
                "org.e2immu.analyser.resolver.testexample.MethodCall_8.method(java.util.List<B>,java.util.Set<A>,java.util.List<B>)",
                "org.e2immu.analyser.resolver.testexample.MethodCall_8.method(java.util.Set<A>,java.util.List<B>,java.util.List<B>)"};
        localInspectAndResolve(MethodCall_8.class, methods, 2);
    }

    @Test
    public void test_9() throws IOException {
        localInspectAndResolve(MethodCall_9.class,
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
        localInspectAndResolve(MethodCall_15.class, "Type String");
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
        testConcreteReturnType(method2, "Type String[]");
        MethodInfo method3 = typeInfo.findUniqueMethod("method3", 0);
        testConcreteReturnType(method3, "Type Integer");
    }

    private void testConcreteReturnType(MethodInfo method1, String expected) {
        Block b1 = method1.methodInspection.get().getMethodBody();
        LocalVariableCreation a1 = (LocalVariableCreation) (((ExpressionAsStatement) b1.structure.getStatements().get(0)).expression);
        Lambda l1 = (Lambda) (a1.localVariableReference.assignmentExpression);
        Block bl1 = l1.methodInfo.methodInspection.get().getMethodBody();
        LocalVariableCreation al1 = (LocalVariableCreation) (((ExpressionAsStatement) bl1.structure.getStatements().get(0)).expression);
        MethodCall mc1 = (MethodCall) (al1.localVariableReference.assignmentExpression);
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
        assertEquals("Type java.util.List<String>", pt.toString());
        if (forEach.expression instanceof MethodCall mc) {
            assertEquals(pt, mc.returnType());
        } else fail();
    }

    @Test
    public void test_21() throws IOException {
        inspectAndResolve(MethodCall_21.class);
    }

    @Test
    public void test_22() throws IOException {
        inspectAndResolve(MethodCall_22.class);
    }

    @Test
    public void test_23() throws IOException {
        TypeMap typeMap = inspectAndResolve(MethodCall_23.class);
        TypeInfo typeInfo = typeMap.get(MethodCall_23.class);
        TypeInfo maTypeInfo = typeInfo.typeInspection.get().subTypes().get(1);
        assertEquals("MethodAnalyser", maTypeInfo.simpleName);
        MethodInfo method = maTypeInfo.findUniqueMethod("method", 1);
        assertEquals("org.e2immu.analyser.resolver.testexample.MethodCall_23.MethodAnalyser.SharedState",
                method.methodInspection.get().getParameters().get(0).parameterizedType.fullyQualifiedName());
    }

    @Test
    public void test_24() throws IOException {
        inspectAndResolve(MethodCall_24.class);
    }

    @Test
    public void test_25() throws IOException {
        TypeMap typeMap = inspectAndResolve(MethodCall_25.class);
        TypeInfo typeInfo = typeMap.get(MethodCall_25.class);
        MethodInfo method3 = typeInfo.findUniqueMethod("method3", 1);
        Block block = method3.methodInspection.get().getMethodBody();
        MethodCall visit = (MethodCall) ((ExpressionAsStatement) block.structure.getStatements().get(0)).expression;
        ParameterInfo p0 = visit.methodInfo.methodInspection.get().getParameters().get(0);
        assertEquals("Type java.util.function.Predicate<org.e2immu.analyser.resolver.testexample.MethodCall_25.Element>",
                p0.parameterizedType.toString());
    }

    @Test
    public void test_26() throws IOException {
        inspectAndResolve(MethodCall_26.class);
    }

    @Test
    public void test_27() throws IOException {
        String[] methods = {
                "org.e2immu.analyser.resolver.testexample.MethodCall_27.method(java.util.List<A>,java.util.function.BiConsumer<A,B>)",
                "org.e2immu.analyser.resolver.testexample.MethodCall_27.method(java.util.List<B>,java.util.function.Predicate<B>)"
        };
        localInspectAndResolve(MethodCall_27.class, methods, 2);
    }

    @Test
    public void test_28() throws IOException {
        TypeMap typeMap = inspectAndResolve(MethodCall_28.class);
        TypeInfo typeInfo = typeMap.get("org.e2immu.analyser.resolver.testexample.MethodCall_28.ImmutableArrayOfHasSize");
        MethodInfo constructor = typeInfo.findConstructor(2);
        Block block = constructor.methodInspection.get().getMethodBody();
        MethodCall setAll = (MethodCall) ((ExpressionAsStatement) block.structure.getStatements().get(1)).expression;
        Lambda lambda = (Lambda) setAll.parameterExpressions.get(1);
        assertEquals("Type org.e2immu.analyser.resolver.testexample.MethodCall_28.HasSize",
                lambda.concreteReturnType.toString());
    }

    @Test
    public void test_29() throws IOException {
        TypeMap typeMap = inspectAndResolve(MethodCall_29.class);
        TypeInfo mc29 = typeMap.get(MethodCall_29.class);
        MethodInfo visit = mc29.findUniqueMethod("visit", 1);
        Block block = visit.methodInspection.get().getMethodBody();
        MethodCall forEach = (MethodCall) ((ExpressionAsStatement) block.structure.getStatements().get(0)).expression;
        assertEquals("java.lang.Iterable.forEach(java.util.function.Consumer<? super T>)",
                forEach.methodInfo.fullyQualifiedName);
    }

    @Test
    public void test_30() throws IOException {
        inspectAndResolve(MethodCall_30.class);
    }

    @Test
    public void test_31() throws IOException {
        inspectAndResolve(MethodCall_31.class);
    }

    // methods with bounded type parameters
    @Test
    public void test_32() throws IOException {
        inspectAndResolve(MethodCall_32.class);
    }

    // Arrays.comparable(...)
    @Test
    public void test_33() throws IOException {
        inspectAndResolve(MethodCall_33.class);
    }

    // String or int??
    @Test
    public void test_34() throws IOException {
        inspectAndResolve(MethodCall_34.class);
    }

    // equals() overloads
    @Test
    public void test_35() throws IOException {
        inspectAndResolve(MethodCall_35.class);
    }

    @Test
    public void test_36() throws IOException {
        inspectAndResolve(MethodCall_36.class);
    }

    @Test
    public void test_37() throws IOException {
        inspectAndResolve(MethodCall_37.class);
    }

    @Test
    public void test_38() throws IOException {
        inspectAndResolve(MethodCall_38.class);
    }

    @Test
    public void test_39() throws IOException {
        inspectAndResolve(MethodCall_39.class);
    }

    // erasure and boxing
    @Test
    public void test_40() throws IOException {
        inspectAndResolve(MethodCall_40.class);
    }

    // double Object[]
    @Test
    public void test_41() throws IOException {
        inspectAndResolve(MethodCall_41.class);
    }

    // short vs String in erasure mode
    @Test
    public void test_42() throws IOException {
        inspectAndResolve(MethodCall_42.class);
    }

    // type bounds chang in hierarchy
    @Test
    public void test_43() throws IOException {
        inspectAndResolve(A, MethodCall_43.class);
    }

    // String[][] --> Object[], see TestIsAssignableFrom.testArray2()
    @Test
    public void test_44() throws IOException {
        inspectAndResolve(MethodCall_44.class);
    }

    // arrayPenalty in ParseMethodCallExpr.filterCandidatesByParameters
    @Test
    public void test_45() throws IOException {
        inspectAndResolve(MethodCall_45.class);
    }

    // erasure problem
    @Test
    public void test_46() throws IOException {
        inspectAndResolve(MethodCall_46.class);
    }

    // erasure problem, related to IsAssignableFrom.targetIsATypeParameter; typeBound
    @Test
    public void test_47() throws IOException {
        inspectAndResolve(MethodCall_47.class);
    }

    @Test
    public void test_48() throws IOException {
        inspectAndResolve(MethodCall_48.class);
    }

    // reEvaluateErasedExpression, "cumulative" should help!
    @Test
    public void test_49() throws IOException {
        inspectAndResolve(MethodCall_49.class);
    }

    // arrays and Serializable
    @Test
    public void test_50() throws IOException {
        inspectAndResolve(MethodCall_50.class);
    }

    @Test
    public void test_51() throws IOException {
        TypeMap typeMap = inspectAndResolve(MethodCall_51.class);
        TypeInfo m51 = typeMap.get(MethodCall_51.class);
        TypeInfo rp = m51.typeInspection.get().subTypes().get(0);
        TypeInfo pp = rp.typeInspection.get().subTypes().get(0);
        assertEquals("org.e2immu.analyser.resolver.testexample.MethodCall_51.ResolverPath.PathProcessor",
                pp.fullyQualifiedName);
        assertTrue(pp.typeInspection.get().isFunctionalInterface());
        TypeInfo pp2 = m51.typeInspection.get().subTypes().get(1);
        assertEquals("org.e2immu.analyser.resolver.testexample.MethodCall_51.PathProcessor", pp2.fullyQualifiedName);
        assertFalse(pp2.typeInspection.get().isFunctionalInterface());
    }

    @Test
    public void test_52() throws IOException {
        inspectAndResolve(MethodCall_52.class);
    }

    @Test
    public void test_53() throws IOException {
        inspectAndResolve(MethodCall_53.class);
    }

    @Test
    public void test_54() throws IOException {
        inspectAndResolve(MethodCall_54.class);
    }

    @Test
    public void test_55() throws IOException {
        inspectAndResolve(MethodCall_55.class);
    }

    @Test
    public void test_56() throws IOException {
        inspectAndResolve(MethodCall_56.class);
    }

    @Test
    public void test_57() throws IOException {
        inspectAndResolve(MethodCall_57.class);
    }

    @Test
    public void test_58() throws IOException {
        inspectAndResolve(MethodCall_58.class);
    }

    @Test
    public void test_59() throws IOException {
        inspectAndResolve(MethodCall_59.class);
    }

    // the following 4 tests deal with arrays in the erasure (FilterResult.typeParameterMap)
    @Test
    public void test_60() throws IOException {
        inspectAndResolve(MethodCall_60.class);
    }

    @Test
    public void test_61() throws IOException {
        inspectAndResolve(MethodCall_61.class);
    }

    @Test
    public void test_62() throws IOException {
        inspectAndResolve(MethodCall_62.class);
    }

    @Test
    public void test_63() throws IOException {
        inspectAndResolve(MethodCall_63.class);
    }

    @Test
    public void test_64() throws IOException {
        inspectAndResolve(MethodCall_64.class);
    }

    @Test
    public void test_65() throws IOException {
        inspectAndResolve(MethodCall_65.class);
    }

    @Test
    public void test_66() throws IOException {
        TypeMap typeMap = inspectAndResolve(MethodCall_66.class);
        TypeInfo typeInfo = typeMap.get(MethodCall_66.class);
        MethodInfo m1 = typeInfo.findUniqueMethod("method1", 1);
        MethodCall mc1 = methodCallInFirstStatement(m1);
        assertEquals("org.e2immu.analyser.resolver.testexample.MethodCall_66.Logger.logError(String,org.e2immu.analyser.resolver.testexample.MethodCall_66.SofBoException)",
                mc1.methodInfo.fullyQualifiedName);

        MethodInfo m2 = typeInfo.findUniqueMethod("method2", 1);
        MethodCall mc2 = methodCallInFirstStatement(m2);
        assertEquals("org.e2immu.analyser.resolver.testexample.MethodCall_66.Logger.logError(String,Throwable)",
                mc2.methodInfo.fullyQualifiedName);
    }

    private static MethodCall methodCallInFirstStatement(MethodInfo m1) {
        return m1.methodInspection.get().getMethodBody().structure.getStatements().get(0).asInstanceOf(ExpressionAsStatement.class).expression.asInstanceOf(MethodCall.class);
    }

    // this test essentially checks that the private JDK type SequencedCollection, squeezed between Collection and List,
    // is not a functional interface
    @Test
    public void test_67() throws IOException {
        inspectAndResolve(MethodCall_67.class);
    }

    @Test
    public void test_68() throws IOException {
        inspectAndResolve(MethodCall_68.class);
    }
}
