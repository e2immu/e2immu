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
import org.e2immu.analyser.model.expression.MemberValuePair;
import org.e2immu.analyser.model.expression.MethodCall;
import org.e2immu.analyser.model.expression.UnevaluatedAnnotationParameterValue;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.statement.ExpressionAsStatement;
import org.e2immu.analyser.parser.TypeMap;
import org.e2immu.analyser.resolver.testexample.Basics_0;
import org.e2immu.analyser.resolver.testexample.Basics_1;
import org.e2immu.analyser.resolver.testexample.Basics_2;
import org.e2immu.analyser.resolver.testexample.Basics_3;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TestBasics extends CommonTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestBasics.class);

    private void inspectAndResolve(Class<?> clazz, String methodFqn) throws IOException {
        TypeMap typeMap = inspectAndResolve(clazz);
        TypeInfo typeInfo = typeMap.get(clazz);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("test", 0);
        Block block = methodInfo.methodInspection.get().getMethodBody();
        if (block.structure.statements().get(0) instanceof ExpressionAsStatement eas) {
            if (eas.expression instanceof MethodCall methodCall) {
                LOGGER.info("Accept: {}", methodCall);
                assertEquals(methodFqn, methodCall.methodInfo.fullyQualifiedName);
            } else fail();
        } else fail();
    }

    @Test
    public void test_0() throws IOException {
        inspectAndResolve(Basics_0.class, "java.io.PrintStream.println(java.lang.String)");
    }

    @Test
    public void test_1() throws IOException {
        inspectAndResolve(Basics_1.class, "java.io.PrintStream.println(int)");
    }

    @Test
    public void test_2() throws IOException {
        inspectAndResolve(Basics_2.class);
    }

    @Test
    public void test_3() throws IOException {
        TypeMap typeMap = inspectAndResolve(Basics_3.class);
        TypeInfo typeInfo = typeMap.get(Basics_3.class);
        TypeInspection typeInspection = typeMap.getTypeInspection(typeInfo);
        ensureNoUnevaluatedAnnotationParameterValues(typeInspection.getAnnotations());

        MethodInfo method = typeInfo.findUniqueMethod("method", 0);
        MethodInspection methodInspection = typeMap.getMethodInspection(method);
        ensureNoUnevaluatedAnnotationParameterValues(methodInspection.getAnnotations());

        FieldInfo fieldInfo = typeInfo.getFieldByName("field", true);
        FieldInspection fieldInspection = typeMap.getFieldInspection(fieldInfo);
        ensureNoUnevaluatedAnnotationParameterValues(fieldInspection.getAnnotations());
    }

    private void ensureNoUnevaluatedAnnotationParameterValues(List<AnnotationExpression> expressions) {
        List<Expression> memberValuePairs = expressions.stream().flatMap(ae -> ae.expressions().stream()).toList();
        assertFalse(memberValuePairs.isEmpty());
        for (Expression expression : memberValuePairs) {
            if (expression instanceof MemberValuePair mvp) {
                assertFalse(mvp.value().get() instanceof UnevaluatedAnnotationParameterValue);
                assertFalse(mvp.value().isVariable());
            } else fail("Expected member value pair but got " + expression.getClass());
        };
    }
}
