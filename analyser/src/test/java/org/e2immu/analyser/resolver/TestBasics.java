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
import org.e2immu.analyser.model.expression.LocalVariableCreation;
import org.e2immu.analyser.model.expression.MemberValuePair;
import org.e2immu.analyser.model.expression.MethodCall;
import org.e2immu.analyser.model.expression.UnevaluatedAnnotationParameterValue;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.statement.ExpressionAsStatement;
import org.e2immu.analyser.model.statement.IfElseStatement;
import org.e2immu.analyser.model.variable.VariableNature;
import org.e2immu.analyser.parser.TypeMap;
import org.e2immu.analyser.resolver.testexample.*;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TestBasics extends CommonTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestBasics.class);

    private TypeMap inspectAndResolve(Class<?> clazz, String methodFqn, String comment) throws IOException {
        TypeMap typeMap = inspectAndResolve(clazz);
        TypeInfo typeInfo = typeMap.get(clazz);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("test", 0);
        Block block = methodInfo.methodInspection.get().getMethodBody();
        if (block.structure.statements().get(0) instanceof ExpressionAsStatement eas) {
            if (eas.expression instanceof MethodCall methodCall) {
                LOGGER.info("Accept: {}", methodCall);
                assertEquals(methodFqn, methodCall.methodInfo.fullyQualifiedName);
            } else fail();
            if (comment == null) {
                assertNull(eas.structure.comment());
            } else {
                assertEquals(comment, eas.structure.comment().text());
            }
        } else fail();
        return typeMap;
    }

    @Test
    public void test_0() throws IOException {
        TypeMap typeMap = inspectAndResolve(Basics_0.class, "java.io.PrintStream.println(java.lang.String)",
                "a comment");
        TypeInfo typeInfo = typeMap.get(Basics_0.class);
        TypeInspection typeInspection = typeMap.getTypeInspection(typeInfo);

        FieldInfo b = typeInspection.fields().stream().filter(f -> "b".equals(f.name)).findFirst().orElseThrow();
        assertFalse(typeMap.getFieldInspection(b).hasFieldInitializer());
        FieldInfo i = typeInspection.fields().stream().filter(f -> "i".equals(f.name)).findFirst().orElseThrow();
        assertTrue(typeMap.getFieldInspection(i).hasFieldInitializer());
    }

    @Test
    public void test_1() throws IOException {
        inspectAndResolve(Basics_1.class, "java.io.PrintStream.println(int)", "another comment");
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

        assertTrue(methodInspection.getMethodBody().identifier instanceof Identifier.PositionalIdentifier);

        FieldInfo fieldInfo = typeInfo.getFieldByName("field", true);
        FieldInspection fieldInspection = typeMap.getFieldInspection(fieldInfo);
        ensureNoUnevaluatedAnnotationParameterValues(fieldInspection.getAnnotations());
    }

    private void ensureNoUnevaluatedAnnotationParameterValues(List<AnnotationExpression> expressions) {
        List<MemberValuePair> memberValuePairs = expressions.stream().flatMap(ae -> ae.expressions().stream()).toList();
        assertFalse(memberValuePairs.isEmpty());
        for (MemberValuePair mvp : memberValuePairs) {
            assertFalse(mvp.value().get() instanceof UnevaluatedAnnotationParameterValue);
            assertFalse(mvp.value().isVariable());
        }
    }

    @Test
    public void test_4() throws IOException {
        inspectAndResolve(Basics_4.class);
    }


    @Test
    public void test_5() throws IOException {
        TypeMap typeMap = inspectAndResolve(Basics_5.class);
        TypeInfo typeInfo = typeMap.get(Basics_5.class);
        MethodInfo method2 = typeInfo.findUniqueMethod("method2", 0);
        Block block = method2.methodInspection.get().getMethodBody();
        Statement s0 = block.structure.statements().get(0);
        if (s0 instanceof ExpressionAsStatement eas) {
            if (eas.expression instanceof LocalVariableCreation lvc) {
                assertEquals(2, lvc.declarations.size());

                LocalVariableCreation.Declaration d0 = lvc.declarations.get(0);
                assertEquals("i", d0.localVariable().name());
                assertEquals("4", d0.expression().toString());
                assertEquals("Type int", d0.localVariable().parameterizedType().toString());
                assertTrue(d0.localVariable().modifiers().contains(LocalVariableModifier.FINAL));

                LocalVariableCreation.Declaration d1 = lvc.declarations.get(1);
                assertEquals("j", d1.localVariable().name());
                assertEquals("<empty>", d1.expression().toString());
                assertEquals(VariableNature.METHOD_WIDE, d1.localVariable().nature());
                assertEquals("Type int", d1.localVariable().parameterizedType().toString());
                assertTrue(d1.localVariable().modifiers().contains(LocalVariableModifier.FINAL));

                assertEquals("final int i=4,j", lvc.minimalOutput());
            } else fail();
        } else fail();
    }

    @Test
    public void test_6() throws IOException {
        TypeMap typeMap = inspectAndResolve(Basics_6.class);
        TypeInfo typeInfo = typeMap.get(Basics_6.class.getCanonicalName());
        assertTrue(typeInfo.isInterface());
        MethodInfo name = typeInfo.findUniqueMethod("name", 0);
        assertTrue(typeInfo.typeInspection.get().isPublic());

        MethodInspection nameInspection = name.methodInspection.get();
        assertTrue(nameInspection.isAbstract());
        assertTrue(nameInspection.isPublic()); // computed, on type
        assertFalse(nameInspection.isDefault()); // modifier
    }

    @Test
    public void test_7() throws IOException {
        inspectAndResolve(Basics_7.class);
    }

    @Test
    public void test_8() throws IOException {
        TypeMap typeMap = inspectAndResolve(Basics_8.class);
        TypeInfo typeInfo = typeMap.get(Basics_8.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("method", 1);
        Block block = methodInfo.methodInspection.get().getMethodBody();
        if (block.structure.statements().get(0) instanceof IfElseStatement ifElseStatement) {
            Comment comment = ifElseStatement.structure.comment();
            assertNotNull(comment);
            assertEquals("comment on 'if'", comment.text());
        } else fail();
    }
}
