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

package org.e2immu.analyser.parser.functional;

import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.StatementAnalysis;
import org.e2immu.analyser.analysis.impl.MethodAnalysisImpl;
import org.e2immu.analyser.analysis.impl.StatementAnalysisImpl;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.statement.ExpressionAsStatement;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.functional.testexample.Lambda_18;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class Test_57_Lambda_AAPI extends CommonTestRunner {

    public Test_57_Lambda_AAPI() {
        super(true);
    }

    @Test
    public void test_1() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                if ("list".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<m:computeIfAbsent>" : "instance type LinkedList<V>";
                        assertEquals(expected, d.currentValue().toString());
                        // TODO this should be CONTENT_NOT_NULL
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                    }
                }
            }
        };
        testClass("Lambda_1", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }


    @Test
    public void test_14() throws IOException {
        testClass("Lambda_14", 0, 0, new DebugConfiguration.Builder()
                .build());
    }


    @Test
    public void test_15() throws IOException {
        testClass("Lambda_15", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_16() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("same1".equals(d.methodInfo().name)) {
                String expected = d.iteration() == 0
                        ? "<m:toArray>"
                        : "list.stream().filter(/*inline test*/mask.startsWith(s)).toArray(/*inline apply*/new String[n])";
                Expression expression = d.evaluationResult().getExpression();
                assertEquals(expected, expression.toString());
                if (d.iteration() > 0) {
                    if (expression instanceof MethodCall methodCall
                            && methodCall.parameterExpressions.get(0) instanceof InlinedMethod inlinedMethod
                            && inlinedMethod.expression() instanceof ConstructorCall constructorCall) {
                        assertEquals(1, constructorCall.parameterizedType().arrays);
                    } else fail();
                }
            }
        };
        testClass("Lambda_16", 0, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build());
    }


    @Test
    public void test_17() throws IOException {
        testClass("Lambda_17", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_18() throws IOException {
        TypeContext typeContext = testClass("Lambda_18", 0, 0,
                new DebugConfiguration.Builder().build());
        TypeInfo lambda18 = typeContext.getFullyQualified(Lambda_18.class);

        MethodInfo method1 = lambda18.findUniqueMethod("method1", 1);
        MethodAnalysis methodAnalysis1 = method1.methodAnalysis.get();
        StatementAnalysis s0 = methodAnalysis1.getFirstStatement();
        StatementAnalysis s1 = s0.navigationData().next.get().orElseThrow();
        Lambda l0 = s1.statement().asInstanceOf(ExpressionAsStatement.class).expression
                .asInstanceOf(LocalVariableCreation.class)
                .localVariableReference.assignmentExpression.asInstanceOf(MethodCall.class)
                .parameterExpressions.get(0).asInstanceOf(Lambda.class);
        assertEquals("Type org.e2immu.analyser.parser.functional.testexample.Lambda_18.$1",
                l0.implementation.toString());
        TypeInfo dollar1 = l0.implementation.typeInfo;
        assert dollar1 != null;
        assertTrue(dollar1.typeAnalysis.isSet());
    }
}
