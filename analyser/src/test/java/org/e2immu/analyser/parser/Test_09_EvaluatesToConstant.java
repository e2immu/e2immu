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

package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.StringConstant;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.visitor.EvaluationResultVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class Test_09_EvaluatesToConstant extends CommonTestRunner {

    public Test_09_EvaluatesToConstant() {
        super(true);
    }

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if ("method2".equals(d.methodInfo().name)) {
            if ("1".equals(d.statementAnalysis().index) && d.iteration() >= 1) {
                assertNotNull(d.haveError(Message.Label.CONDITION_EVALUATES_TO_CONSTANT));
            }
            if ("1.0.0".equals(d.statementAnalysis().index)) {
                assertNull(d.haveError(Message.Label.CONDITION_EVALUATES_TO_CONSTANT));
            }
        }

        if ("method3".equals(d.methodInfo().name)) {
            if ("1.0.0".equals(d.statementId())) {
                assertEquals("param.contains(\"a\")", d.absoluteState().toString());

                if (d.iteration() >= 1) {
                    Expression value = d.statementAnalysis().stateData.valueOfExpression.get();
                    assertEquals("\"xzy\"", value.toString());
                    assertTrue(value instanceof StringConstant);
                }
            }
            if ("1.0.1".equals(d.statementAnalysis().index)) {
                String expectState = d.iteration() == 0 ? "param.contains(\"a\")&&null!=<s:String>" : "param.contains(\"a\")";
                assertEquals(expectState, d.absoluteState().toString());
                if (d.iteration() >= 1) {
                    assertNotNull(d.haveError(Message.Label.CONDITION_EVALUATES_TO_CONSTANT));
                }
            }
            if ("1.0.1.0.0".equals(d.statementAnalysis().index)) {
                assertNull(d.haveError(Message.Label.CONDITION_EVALUATES_TO_CONSTANT));
            }
        }

        if ("someMethod".equals(d.methodInfo().name)) {
            VariableInfo variableInfo = d.getReturnAsVariable();
            assertEquals("null==a?\"x\":a", variableInfo.getValue().toString());
            assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL,
                    variableInfo.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
        }
    };

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {

        if ("method2".equals(d.methodInfo().name)) {
            if ("b".equals(d.variableName()) && "0".equals(d.statementId())) {
                assertEquals("null==param?\"x\":param", d.currentValue().toString());
                int nne = d.currentValue().getProperty(d.evaluationContext(), VariableProperty.NOT_NULL_EXPRESSION, true);
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, nne);
                assertEquals("", d.variableInfo().getLinkedVariables().toString());
            }
            if (d.variable() instanceof ParameterInfo p && "param".equals(p.name)) {
                int expectCnn = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
                assertEquals(expectCnn, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
            }
        }

        if ("method3".equals(d.methodInfo().name)) {
            if (d.variable() instanceof ParameterInfo p && "param".equals(p.name)) {
                assertEquals("nullable instance type String", d.currentValue().toString());
                if ("0".equals(d.statementId())) {
                    int expectCnn = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
                    assertEquals(expectCnn, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                }
            }
            if ("b".equals(d.variableName()) && "0".equals(d.statementId())) {
                assertEquals("null==param?\"x\":param", d.currentValue().toString());
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
            }
            if ("a".equals(d.variableName()) && "1.0.0".equals(d.statementId())) {
                // the delay comes from the CNN == -1 value of PARAM, delayed condition in 1
                String expectValue = d.iteration() == 0 ? "<s:String>" : "\"xzy\"";
                assertEquals(expectValue, d.currentValue().toString());
                int expectNne = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                assertEquals(expectNne, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
            }
            if (d.variable() instanceof ReturnVariable) {
                if ("0".equals(d.statementId())) {
                    assertEquals("", d.variableInfo().getLinkedVariables().toString());
                    assertEquals(Level.FALSE, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                    assertEquals(MultiLevel.NOT_INVOLVED, d.getProperty(VariableProperty.EXTERNAL_NOT_NULL));
                }
                if ("1.0.0".equals(d.statementId())) {
                    assertEquals(MultiLevel.NOT_INVOLVED, d.getProperty(VariableProperty.EXTERNAL_NOT_NULL));
                }
                if ("1.0.1.0.0".equals(d.statementId())) {
                    if (d.iteration() == 0) {
                        assertEquals("", d.variableInfo().getLinkedVariables().toString());
                        assertEquals(Level.FALSE, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                        assertEquals(Level.DELAY, d.getProperty(VariableProperty.EXTERNAL_NOT_NULL));
                    } else {
                        fail(); // unreachable, now that the condition is stable
                    }
                }
                if ("1.0.1".equals(d.statementId())) {
                    assertEquals("", d.variableInfo().getLinkedVariables().toString());
                    assertEquals(Level.FALSE, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                    int expectEnn = d.iteration() == 0 ? Level.DELAY : MultiLevel.NOT_INVOLVED;
                    assertEquals(expectEnn, d.getProperty(VariableProperty.EXTERNAL_NOT_NULL));
                }
                if ("1".equals(d.statementId())) {
                    assertEquals("", d.variableInfo().getLinkedVariables().toString());
                    assertEquals(Level.FALSE, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                    int expectEnn = d.iteration() == 0 ? Level.DELAY : MultiLevel.NOT_INVOLVED;
                    assertEquals(expectEnn, d.getProperty(VariableProperty.EXTERNAL_NOT_NULL));
                }
            }
        }
    };

    EvaluationResultVisitor evaluationResultVisitor = d -> {
        if ("method2".equals(d.methodInfo().name)) {
            if ("0".equals(d.statementId())) {
                assertEquals("null==param?\"x\":param", d.evaluationResult().value().toString());
            }
            if ("1".equals(d.statementId())) {
                assertEquals("false", d.evaluationResult().value().toString());
            }
        }
        if ("method3".equals(d.methodInfo().name)) {
            if ("1".equals(d.statementId())) {
                assertEquals("param.contains(\"a\")", d.evaluationResult().value().toString());
                assertFalse(d.evaluationResult().someValueWasDelayed());
            }
            if ("1.0.0".equals(d.statementId())) {
                assertEquals("\"xzy\"", d.evaluationResult().value().toString());
            }
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        if ("someMethod".equals(d.methodInfo().name)) {
            assertEquals(Level.FALSE, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
            assertEquals("null==a?\"x\":a", d.methodAnalysis().getSingleReturnValue().toString());
            assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL,
                    d.methodAnalysis().getProperty(VariableProperty.NOT_NULL_EXPRESSION));

            ParameterAnalysis param = d.parameterAnalyses().get(0);
            int expectNnp = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
            assertEquals(expectNnp, param.getProperty(VariableProperty.NOT_NULL_PARAMETER));
        }
    };

    /*
    whether b is used or not: 6 or 4 warnings; not too important at the moment
     */
    @Test
    public void test() throws IOException {
        testClass("EvaluatesToConstant", 4, 0, new DebugConfiguration.Builder()
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addEvaluationResultVisitor(evaluationResultVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().setSkipTransformations(true).build());
    }

}
