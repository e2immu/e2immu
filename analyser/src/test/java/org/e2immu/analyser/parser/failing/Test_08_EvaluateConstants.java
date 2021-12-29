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

package org.e2immu.analyser.parser.failing;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.MethodLevelData;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.expression.ConstantExpression;
import org.e2immu.analyser.model.expression.StringConstant;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.e2immu.analyser.analyser.AnalysisStatus.DONE;
import static org.junit.jupiter.api.Assertions.*;

public class Test_08_EvaluateConstants extends CommonTestRunner {

    public Test_08_EvaluateConstants() {
        super(false);
    }


    @Test
    public void test_0() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("EvaluateConstants_0".equals(d.methodInfo().name)) {
                if ("0.0.0".equals(d.statementId()) && d.variable() instanceof ParameterInfo in) {
                    assertEquals("in", in.name);
                    assertEquals("in:0", d.variableInfo().getLinkedVariables().toString());
                }
                if ("0".equals(d.statementId()) && d.variable() instanceof ParameterInfo in) {
                    assertEquals("in", in.name);
                    assertEquals("in:0", d.variableInfo().getLinkedVariables().toString());
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("getEffectivelyFinal".equals(d.methodInfo().name)) {
                VariableInfo vi = d.getReturnAsVariable();
                assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);

                if (d.iteration() == 0) {
                    assertTrue(vi.isDelayed());
                } else if (d.iteration() == 1) {
                    assertEquals("effectivelyFinal", vi.getValue().toString());
                    assertSame(DONE, d.result().analysisStatus());
                } else fail();
            }
            if ("EvaluateConstants_0".equals(d.methodInfo().name)) {
                if ("2".equals(d.statementId())) {
                    assertTrue(d.statementAnalysis().methodLevelData.linksHaveBeenEstablished());
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("getEffectivelyFinal".equals(d.methodInfo().name)) {
                Expression srv = d.methodAnalysis().getSingleReturnValue();
                if (d.iteration() == 0) {
                    assertNull(srv);
                } else {
                    assertEquals("/* inline getEffectivelyFinal */this.effectivelyFinal",
                            srv.debugOutput());
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV,
                            d.methodAnalysis().getProperty(Property.NOT_NULL_EXPRESSION));
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("effectivelyFinal".equals(d.fieldInfo().name)) {
                assertEquals(Level.TRUE_DV, d.fieldAnalysis().getProperty(Property.FINAL));
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.fieldAnalysis()
                        .getProperty(Property.EXTERNAL_NOT_NULL));
                assertEquals("in", d.fieldAnalysis().getValue().toString());
            }
        };

        testClass("EvaluateConstants_0", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build(), new AnalyserConfiguration.Builder().setSkipTransformations(true).build());
    }


    @Test
    public void test_1() throws IOException {

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("print".equals(d.methodInfo().name) && "0".equals(d.statementId())) {
                String expect = d.iteration() == 0 ? "<m:ee>" : "false";
                assertEquals(expect, d.evaluationResult().value().toString());
            }
            if ("print2".equals(d.methodInfo().name)) {
                if (d.iteration() > 0) {
                    assertTrue(d.evaluationResult().value().isInstanceOf(ConstantExpression.class));
                    assertEquals("\"b\"", d.evaluationResult().value().toString());
                }
            }
        };

    /*
    Method ee() becomes @NotModified in iteration 1
    Only then, the internal object flows of print2 can be frozen; this happens during evaluation.

     */

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            MethodLevelData methodLevelData = d.statementAnalysis().methodLevelData;
            if ("print".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId()) && d.iteration() >= 1) {
                    assertNotNull(d.haveError(Message.Label.CONDITION_EVALUATES_TO_CONSTANT));
                }
                if ("0.0.0".equals(d.statementId())) {
                    assertNull(d.haveError(Message.Label.CONDITION_EVALUATES_TO_CONSTANT));
                    if (d.iteration() >= 1) {
                        assertTrue(d.statementAnalysis().flowData.isUnreachable());
                    }
                }
            }
            if ("ee".equals(d.methodInfo().name)) {
                // just says: return e; (e is a field, constant false (rather than linked to c and c, I'd say)
                if (d.iteration() > 0) {
                    assertTrue(d.statementAnalysis().variableStream().filter(vi -> vi.variable() instanceof FieldReference)
                            .allMatch(VariableInfo::linkedVariablesIsSet));
                    assertTrue(methodLevelData.linksHaveBeenEstablished());
                }
            }
            if ("print2".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId()) && d.iteration() > 0) {
                    assertNotNull(d.haveError(Message.Label.INLINE_CONDITION_EVALUATES_TO_CONSTANT));
                    assertSame(DONE, d.result().analysisStatus());
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("ee".equals(d.methodInfo().name)) {
                // we prove that ee() returns false
                Expression srv = d.methodAnalysis().getSingleReturnValue();
                if (d.iteration() > 0) {
                    assertEquals("false", srv.toString());
                    DV modified = d.methodAnalysis().getProperty(Property.MODIFIED_METHOD);
                    assertEquals(Level.FALSE_DV, modified);
                }
            }
            if ("print".equals(d.methodInfo().name)) {
                if (d.iteration() > 0) {
                    Expression srv = d.methodAnalysis().getSingleReturnValue();
                    assertEquals("\"b\"", srv.toString());
                }
            }
            if ("print2".equals(d.methodInfo().name) && d.iteration() > 2) {
                Expression srv = d.methodAnalysis().getSingleReturnValue();
                assertTrue(srv instanceof StringConstant); // inline conditional works as advertised
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("e".equals(d.fieldInfo().name)) {
                assertEquals("e:0", d.fieldAnalysis().getLinkedVariables().toString());
            }
        };

        testClass("EvaluateConstants_1", 3, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build(), new AnalyserConfiguration.Builder().setSkipTransformations(true).build());
    }

}
