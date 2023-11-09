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
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.statement.ExpressionAsStatement;
import org.e2immu.analyser.model.statement.IfElseStatement;
import org.e2immu.analyser.model.variable.VariableNature;
import org.e2immu.analyser.output.Formatter;
import org.e2immu.analyser.output.FormattingOptions;
import org.e2immu.analyser.output.OutputBuilder;
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
        TypeMap typeMap = inspectAndResolve(Basics_0.class, "java.io.PrintStream.println(String)",
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
                assertEquals(1, lvc.moreDeclarations.size());

                assertEquals("i", lvc.localVariableReference.simpleName());
                assertEquals("4", lvc.localVariableReference.assignmentExpression.toString());
                assertEquals("Type int", lvc.localVariableReference.parameterizedType().toString());
                assertTrue(lvc.localVariableReference.variable.modifiers().contains(LocalVariableModifier.FINAL));

                LocalVariableCreation.Declaration d1 = lvc.moreDeclarations.get(0);
                assertEquals("j", d1.localVariableReference().variable.name());
                assertEquals("<empty>", d1.localVariableReference().assignmentExpression.toString());
                assertEquals(VariableNature.METHOD_WIDE, d1.localVariableReference().variable.nature());
                assertEquals("Type int", d1.localVariableReference().parameterizedType().toString());
                assertTrue(d1.localVariableReference().variable.modifiers().contains(LocalVariableModifier.FINAL));

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
        assertTrue(name.isAbstract());
        assertTrue(nameInspection.isPublic()); // computed, on type
        assertFalse(name.isDefault()); // modifier
    }

    @Test
    public void test_7() throws IOException {
        inspectAndResolve(Basics_7.class);
    }

    @Test
    public void test_8() throws IOException {
        Formatter formatter = new Formatter(FormattingOptions.DEFAULT);

        TypeMap typeMap = inspectAndResolve(Basics_8.class);
        TypeInfo typeInfo = typeMap.get(Basics_8.class);
        TypeInspection typeInspection = typeInfo.typeInspection.get();
        assertNotNull(typeInspection.getComment());
        assertEquals("orphan to type\ncomment on type", typeInspection.getComment().text());
        OutputBuilder ob = typeInspection.getComment().output(Qualification.EMPTY);
        assertEquals("/*orphan to type comment on type*/\n", formatter.write(ob));

        MethodInfo methodInfo = typeInfo.findUniqueMethod("method", 1);
        MethodInspection methodInspection = methodInfo.methodInspection.get();
        assertNotNull(methodInspection.getComment());
        assertEquals("orphan to method\ncomment on method", methodInspection.getComment().text());

        Block block = methodInspection.getMethodBody();
        if (block.structure.statements().get(0) instanceof IfElseStatement ifElseStatement) {
            Comment comment = ifElseStatement.structure.comment();
            assertNotNull(comment);
            assertEquals("orphan on if\ncomment on 'if'", comment.text());

            OutputBuilder output = block.output(Qualification.EMPTY, null);
            assertEquals("{ /*orphan on if comment on 'if'*/ if(in > 9) { return 1; } System.out.println(\"in = \" + in); return in; }\n", formatter.write(output));
        } else fail();

        {
            FieldInfo fieldInfo = typeInfo.getFieldByName("CONSTANT_1", true);
            FieldInspection fieldInspection = fieldInfo.fieldInspection.get();
            Comment comment = fieldInspection.getComment();
            assertNotNull(comment);
            assertEquals("orphan to field 1\ncomment on field 1", comment.text());
        }
        {
            FieldInfo fieldInfo2 = typeInfo.getFieldByName("CONSTANT_2", true);
            FieldInspection fieldInspection2 = fieldInfo2.fieldInspection.get();
            Comment comment2 = fieldInspection2.getComment();
            assertNotNull(comment2);
            assertEquals("orphan to field 2\ncomment on field 2", comment2.text());
        }
    }

    @Test
    public void test_9() throws IOException {
        TypeMap typeMap = inspectAndResolve(Basics_9.class);
        TypeInfo typeInfo = typeMap.get(Basics_9.class.getCanonicalName());
        MethodInfo methodInfo = typeInfo.findUniqueMethod("method1", 1);
        MethodInspection methodInspection = methodInfo.methodInspection.get();
        Block block = methodInspection.getMethodBody();
        if (block.structure.statements().get(2) instanceof IfElseStatement ifElseStatement) {
            assertEquals("v==null", ifElseStatement.expression.toString());
            if (ifElseStatement.expression instanceof BinaryOperator op) {
                assertSame(typeMap.getPrimitives().equalsOperatorObject(), op.operator);
            } else fail();
        } else fail();
    }


    // variable always takes priority over type!
    @Test
    public void test_10() throws IOException {
        inspectAndResolve(Basics_10.class);
    }

    // weird but legal
    @Test
    public void test_11() throws IOException {
        inspectAndResolve(Basics_11.class);
    }
}