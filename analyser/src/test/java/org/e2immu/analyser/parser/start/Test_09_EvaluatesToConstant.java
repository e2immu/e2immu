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

package org.e2immu.analyser.parser.start;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.expression.UnknownExpression;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.Message;
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
            if ("1".equals(d.statementAnalysis().index()) && d.iteration() >= 1) {
                assertNotNull(d.haveError(Message.Label.CONDITION_EVALUATES_TO_CONSTANT));
            }
            if ("1.0.0".equals(d.statementAnalysis().index())) {
                assertNull(d.haveError(Message.Label.CONDITION_EVALUATES_TO_CONSTANT));
            }
        }

        if ("method3".equals(d.methodInfo().name)) {
            if ("1.0.0".equals(d.statementId())) {
                String expected = d.iteration() == 0 ? "<m:contains>" : "param.contains(\"a\")";
                assertEquals(expected, d.absoluteState().toString());
                Expression value = d.statementAnalysis().stateData().valueOfExpressionGet();
                assertEquals("EvaluatesToConstant.someMethod(\"xzy\")", value.toString());
            }
            if ("1.0.1".equals(d.statementAnalysis().index())) {
                String expected = d.iteration() == 0 ? "<m:contains>&&!<c:boolean>" : "param.contains(\"a\")";
                assertEquals(expected, d.absoluteState().toString());
                assertEquals(d.iteration() > 0,
                        null != d.haveError(Message.Label.CONDITION_EVALUATES_TO_CONSTANT));
            }
            if ("1.0.1.0.0".equals(d.statementAnalysis().index())) {
                String expected = d.iteration() == 0 ? "<m:contains>&&<c:boolean>" : "false";
                assertEquals(expected, d.absoluteState().toString());
                assertNull(d.haveError(Message.Label.CONDITION_EVALUATES_TO_CONSTANT));
            }
        }

        if ("someMethod".equals(d.methodInfo().name)) {
            VariableInfo variableInfo = d.getReturnAsVariable();
            assertEquals("null==a?\"x\":a", variableInfo.getValue().toString());
            assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV,
                    variableInfo.getProperty(Property.NOT_NULL_EXPRESSION));
        }
    };

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if (d.variable() instanceof ReturnVariable) {
            if (d.currentValue() instanceof UnknownExpression ue) {
                assertEquals(UnknownExpression.RETURN_VALUE, ue.msg());
                for (Property property : EvaluationContext.VALUE_PROPERTIES) {
                    DV dv = d.getProperty(property);
                    assertTrue(dv == null || dv.isDone(),
                            "Problem in it " + d.iteration() + ", " + d.statementId() + ", " + property + "=" + dv);
                }
            }
        }

        if ("method2".equals(d.methodInfo().name)) {
            if ("b".equals(d.variableName()) && "0".equals(d.statementId())) {
                assertCurrentValue(d, 0, "EvaluatesToConstant.someMethod(param)");
                //  if (d.iteration() > 0) {
                DV nne = d.currentValue().getProperty(d.context(), Property.NOT_NULL_EXPRESSION, true);
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, nne);
                //    }
                assertEquals("", d.variableInfo().getLinkedVariables().toString());
            }
            if (d.variable() instanceof ParameterInfo p && "param".equals(p.name)) {
                if ("1".equals(d.statementId())) {
                    assertDv(d, 1, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                }
            }
        }

        if ("method3".equals(d.methodInfo().name)) {
            if (d.variable() instanceof ParameterInfo p && "param".equals(p.name)) {
                if ("0".equals(d.statementId())) {
                    assertDv(d, 1, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                    assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    String expected = d.iteration() == 0 ? "<mod:String>" : "nullable instance type String/*@Identity*/";
                    assertEquals(expected, d.currentValue().toString());
                    assertEquals(DV.TRUE_DV, d.getProperty(Property.IDENTITY));
                }
                if ("1.0.0".equals(d.statementId())) {
                    assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                    assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    String expected = d.iteration() == 0 ? "<mod:String>" : "nullable instance type String/*@Identity*/";
                    assertEquals(expected, d.currentValue().toString());
                    assertDv(d, 1, DV.TRUE_DV, Property.IDENTITY);
                }
                // this is the if(a==null) { ..} statement, where the expression evaluates to false
                if ("1.0.1".equals(d.statementId())) {
                    if (d.iteration() > 0) {
                        assertFalse(d.variableInfoContainer().hasEvaluation());
                        VariableInfo prev = d.variableInfoContainer().getPreviousOrInitial();

                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, prev.getProperty(Property.CONTEXT_NOT_NULL));
                        assertEquals(DV.FALSE_DV, prev.getProperty(Property.CONTEXT_MODIFIED));
                    }
                }
                if ("1".equals(d.statementId())) {
                    assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                    assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                }
            }
            if ("b".equals(d.variableName()) && "0".equals(d.statementId())) {
                assertCurrentValue(d, 0, "EvaluatesToConstant.someMethod(param)");
                assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
            }
            if ("a".equals(d.variableName())) {
                assertEquals("EvaluatesToConstant.someMethod(\"xzy\")", d.currentValue().toString());
                if ("1.0.0".equals(d.statementId())) {
                    assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                }
                if ("1.0.1".equals(d.statementId())) {
                    assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    assertDv(d, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                }
                assertNotEquals("1", d.statementId());
            }
            if (d.variable() instanceof ReturnVariable) {
                if ("0".equals(d.statementId())) {
                    assertEquals("", d.variableInfo().getLinkedVariables().toString());
                    assertFalse(d.variableInfo().getLinkedVariables().isDelayed());

                    assertEquals(DV.FALSE_DV, d.getProperty(Property.CONTEXT_MODIFIED));
                }
                if ("1.0.0".equals(d.statementId())) {
                    String expect = d.iteration() <= 1 ? "<return value>" : "no iteration 2";
                    assertEquals(expect, d.currentValue().toString());
                }
                if ("1.0.1.0.0".equals(d.statementId())) {
                    String expected = d.iteration() == 0 ? "<v:b>+\"c\"" : "(null==param?\"x\":param)+\"c\"";
                    assertEquals(expected, d.currentValue().toString());
                    if (d.iteration() == 0) {
                        assertEquals("b:-1", d.variableInfo().getLinkedVariables().toString());
                        assertNull(d.getProperty(Property.CONTEXT_MODIFIED));
                    } else {
                        fail(); // unreachable, now that the condition is stable
                    }
                }
                if ("1.0.1".equals(d.statementId())) {
                    String expected = d.iteration() == 0
                            ? "<c:boolean>?<v:b>+\"c\":<return value>"
                            : "<return value>";
                    assertEquals(expected, d.currentValue().toString());
                    String linked = d.iteration() == 0 ? "b:-1" : "";
                    assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                }
                if ("1".equals(d.statementId())) {
                    String expected = d.iteration() == 0
                            ? "<m:contains>&&<c:boolean>?<v:b>+\"c\":<return value>"
                            : "<return value>";
                    assertEquals(expected, d.currentValue().toString());
                    CausesOfDelay causesOfDelayOfLinkedVars = d.variableInfo().getLinkedVariables().causesOfDelay();
                    assertEquals(d.iteration() > 0, causesOfDelayOfLinkedVars.isDone());
                    String linked = d.iteration() == 0 ? "b:-1" : "";
                    assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    assertNull(d.getProperty(Property.CONTEXT_MODIFIED));
                }
            }
        }
    };

    EvaluationResultVisitor evaluationResultVisitor = d -> {
        if ("method2".equals(d.methodInfo().name)) {
            if ("0".equals(d.statementId())) {
                String expected = "EvaluatesToConstant.someMethod(param)";
                assertEquals(expected, d.evaluationResult().value().toString());
            }
            if ("1".equals(d.statementId())) {
                assertEquals("false", d.evaluationResult().value().toString());
            }
        }
        if ("method3".equals(d.methodInfo().name)) {
            if ("0".equals(d.statementId())) {
                // read: someMethod's parameter a may cause modification or not-null, but not known yet
                String expected = d.iteration() == 0 ? "cm@Parameter_a;constructor-to-instance@Method_method3_0-E" : "";
                assertEquals(expected, d.evaluationResult().causesOfDelay().toString());
            }
            if ("1".equals(d.statementId())) {
                String expected = d.iteration() == 0 ? "<m:contains>" : "param.contains(\"a\")";
                assertEquals(expected, d.evaluationResult().value().toString());
            }
            if ("1.0.0".equals(d.statementId())) {
                String expected = "EvaluatesToConstant.someMethod(\"xzy\")";
                assertEquals(expected, d.evaluationResult().value().toString());
                assertTrue(d.evaluationResult().causesOfDelay().isDone());
            }
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        if ("someMethod".equals(d.methodInfo().name)) {
            assertEquals(DV.FALSE_DV, d.methodAnalysis().getProperty(Property.MODIFIED_METHOD));
            String expected = d.iteration() == 0 ? "<m:someMethod>" : "/*inline someMethod*/null==a?\"x\":a";
            assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());

            assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
            assertDv(d.p(0), 1, MultiLevel.NULLABLE_DV, Property.NOT_NULL_PARAMETER);
            assertDv(d.p(0), 1, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
        }
    };

    /*
    b is used in the current implementation (only in iteration 1 do we set the execution of the block to NEVER,
    by that time the variable has been READ)
     */
    @Test
    public void test() throws IOException {
        testClass("EvaluatesToConstant", 4, 0, new DebugConfiguration.Builder()
                        //  .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        //  .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        //  .addEvaluationResultVisitor(evaluationResultVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().setSkipTransformations(true).build());
    }

}
