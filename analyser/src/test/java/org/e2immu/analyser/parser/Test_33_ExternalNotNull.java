
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

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterAnalysis;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.e2immu.analyser.analyser.VariableProperty.CONTEXT_NOT_NULL;
import static org.e2immu.analyser.analyser.VariableProperty.EXTERNAL_NOT_NULL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class Test_33_ExternalNotNull extends CommonTestRunner {

    public Test_33_ExternalNotNull() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("upperCaseR".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "r".equals(fr.fieldInfo.name)) {
                    String expectValue = d.iteration() == 0 ? "<f:r>" : "instance type String";
                    assertEquals(expectValue, d.currentValue().toString());
                    int expectNne = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                    assertEquals(expectNne, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                    // because NNE > 0, there cannot be a warning!
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            int n = d.methodInfo().methodInspection.get().getParameters().size();
            if ("ExternalNotNull_0".equals(d.methodInfo().name) && n == 4) {
                if ("6".equals(d.statementId())) {
                    assertEquals(d.iteration() == 0,
                            null == d.haveError(Message.Label.CONDITION_EVALUATES_TO_CONSTANT_ENN));
                }
                if ("7".equals(d.statementId())) {
                    assertEquals(d.iteration() == 0,
                            null == d.haveError(Message.Label.CONDITION_EVALUATES_TO_CONSTANT_ENN));
                }
            }
            if ("upperCaseO".equals(d.methodInfo().name)) {
                assertEquals(d.iteration() == 0,
                        null == d.haveError(Message.Label.POTENTIAL_NULL_POINTER_EXCEPTION));
            }
            if ("upperCaseP".equals(d.methodInfo().name)) {
                assertNull(d.haveError(Message.Label.POTENTIAL_NULL_POINTER_EXCEPTION));
            }
            if ("upperCaseQ".equals(d.methodInfo().name)) {
                assertEquals(d.iteration() == 0,
                        null == d.haveError(Message.Label.POTENTIAL_NULL_POINTER_EXCEPTION));
            }
            if ("upperCaseR".equals(d.methodInfo().name)) {
                assertNull(d.haveError(Message.Label.POTENTIAL_NULL_POINTER_EXCEPTION));
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {

            /*
            only in iteration 2 is the Linked+EffFinalValue status of all fields known
            (iteration 0 is the very first of anything; only at the end of it 1 the field p has values)
             */
            int n = d.methodInfo().methodInspection.get().getParameters().size();
            if ("ExternalNotNull_0".equals(d.methodInfo().name) && n == 2) {
                ParameterAnalysis p1 = d.parameterAnalyses().get(0);
                int expectEnnP1 = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                assertEquals(expectEnnP1, p1.getProperty(EXTERNAL_NOT_NULL));

                ParameterAnalysis r1 = d.parameterAnalyses().get(1);
                int expectEnnR1 = d.iteration() == 0 ? Level.DELAY : MultiLevel.NOT_INVOLVED;
                assertEquals(expectEnnR1, r1.getProperty(EXTERNAL_NOT_NULL));
            }
            if ("ExternalNotNull_0".equals(d.methodInfo().name) && n == 4) {
                ParameterAnalysis p2 = d.parameterAnalyses().get(0);
                int expectEnnP2 = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                assertEquals(expectEnnP2, p2.getProperty(EXTERNAL_NOT_NULL));

                ParameterAnalysis q2 = d.parameterAnalyses().get(1);
                int expectEnnQ2 = d.iteration() == 0 ? Level.DELAY : MultiLevel.NOT_INVOLVED;
                assertEquals(expectEnnQ2, q2.getProperty(EXTERNAL_NOT_NULL));

                ParameterAnalysis r2 = d.parameterAnalyses().get(2);
                int expectEnnR2 = d.iteration() == 0 ? Level.DELAY : MultiLevel.NOT_INVOLVED;
                assertEquals(expectEnnR2, r2.getProperty(EXTERNAL_NOT_NULL));

                ParameterAnalysis s2 = d.parameterAnalyses().get(3);
                int expectEnnS2 = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
                assertEquals(expectEnnS2, s2.getProperty(EXTERNAL_NOT_NULL));
            }
            // not involved, because q is @Variable
            if ("setQ".equals(d.methodInfo().name)) {
                ParameterAnalysis qs = d.parameterAnalyses().get(0);
                int expectEnnQs = d.iteration() == 0 ? Level.DELAY : MultiLevel.NOT_INVOLVED;
                assertEquals(expectEnnQs, qs.getProperty(EXTERNAL_NOT_NULL));
            }
            // not involved, because r is not assigned, @Variable
            if ("setR".equals(d.methodInfo().name)) {
                ParameterAnalysis rs = d.parameterAnalyses().get(0);
                int expectEnnRs = d.iteration() == 0 ? Level.DELAY : MultiLevel.NOT_INVOLVED;
                assertEquals(expectEnnRs, rs.getProperty(EXTERNAL_NOT_NULL));
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            int enn = d.fieldAnalysis().getProperty(EXTERNAL_NOT_NULL);
            int effFinal = d.fieldAnalysis().getProperty(VariableProperty.FINAL);

            if ("o".equals(d.fieldInfo().name)) {
                assertEquals(MultiLevel.NULLABLE, enn);
                assertEquals(Level.TRUE, effFinal);
                assertEquals("[null,\"hello\"]", d.fieldAnalysis().getEffectivelyFinalValue().toString());
                assertEquals("", d.fieldAnalysis().getLinkedVariables().toString());
            }
            if ("p".equals(d.fieldInfo().name)) {
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, enn);
                assertEquals(Level.TRUE, effFinal);
                assertEquals("[p1,p2]", d.fieldAnalysis().getEffectivelyFinalValue().toString());

            }
            if ("q".equals(d.fieldInfo().name)) {
                assertEquals(MultiLevel.NULLABLE, enn);
                assertEquals(Level.FALSE, effFinal);
                assertEquals("q2:0,qs:0", d.fieldAnalysis().getLinkedVariables().toString());
            }
            if ("r".equals(d.fieldInfo().name)) {
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, enn);
                assertEquals(Level.FALSE, effFinal);
                assertEquals("r1:1,r2:1,rs:1", d.fieldAnalysis().getLinkedVariables().toString());
            }
        };
        testClass("ExternalNotNull_0", 0, 4, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_1() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            int n = d.methodInfo().methodInspection.get().getParameters().size();
            if ("5".equals(d.statementId()) && "ExternalNotNull_1".equals(d.methodInfo().name) && n == 2) {
                assertEquals("<no return value>", d.evaluationResult().value().toString());
                assertEquals(d.iteration() == 0, d.evaluationResult().someValueWasDelayed());
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            int enn = d.fieldAnalysis().getProperty(EXTERNAL_NOT_NULL);
            int effFinal = d.fieldAnalysis().getProperty(VariableProperty.FINAL);

            if ("p".equals(d.fieldInfo().name)) {
                assertEquals(Level.TRUE, effFinal);
                int expectEnn = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                assertEquals(expectEnn, enn);
                if (d.iteration() == 0) {
                    assertNull(d.fieldAnalysis().getEffectivelyFinalValue());
                } else {
                    assertEquals("[p1,p2]", d.fieldAnalysis().getEffectivelyFinalValue().toString());
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            int n = d.methodInfo().methodInspection.get().getParameters().size();
            if ("ExternalNotNull_1".equals(d.methodInfo().name) && n == 2) {
                ParameterAnalysis p1 = d.parameterAnalyses().get(0);
                int expectEnnP1 = d.iteration() <= 1 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                assertEquals(expectEnnP1, p1.getProperty(EXTERNAL_NOT_NULL));
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            int n = d.methodInfo().methodInspection.get().getParameters().size();
            if ("ExternalNotNull_1".equals(d.methodInfo().name) && n == 2) {
                if (d.variable() instanceof ParameterInfo p1 && "p1".equals(p1.name) && "5".equals(d.statementId())) {
                    int expectEnn = d.iteration() <= 1 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                    assertEquals(expectEnn, d.getProperty(EXTERNAL_NOT_NULL));
                    String expectValue = d.iteration() == 0 ? "<p:p1>" : "nullable instance type String/*@Identity*/";
                    assertEquals(expectValue, d.currentValue().toString());
                }
            }
            if ("ExternalNotNull_1".equals(d.methodInfo().name) && n == 4) {
                if (d.variable() instanceof ParameterInfo p2 && "p2".equals(p2.name)) {
                    if ("1".equals(d.statementId())) {
                        int expectEnn = d.iteration() <= 1 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                        assertEquals(expectEnn, d.getProperty(EXTERNAL_NOT_NULL));
                        int expectCnn = MultiLevel.NULLABLE;
                        assertEquals(expectCnn, d.getProperty(CONTEXT_NOT_NULL));
                    }
                }
                if (d.variable() instanceof FieldReference fr && "p".equals(fr.fieldInfo.name) && "5".equals(d.statementId())) {
                    int expectEnn = d.iteration() <= 1 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                    assertEquals(expectEnn, d.getProperty(EXTERNAL_NOT_NULL));
                    int expectCnn = MultiLevel.NULLABLE;
                    assertEquals(expectCnn, d.getProperty(CONTEXT_NOT_NULL));
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            int n = d.methodInfo().methodInspection.get().getParameters().size();
            if ("ExternalNotNull_1".equals(d.methodInfo().name) && n == 4) {
                if ("6".equals(d.statementId())) {
                    assertEquals(d.iteration() <= 1,
                            null == d.haveError(Message.Label.CONDITION_EVALUATES_TO_CONSTANT_ENN));
                }
            }
        };

        testClass("ExternalNotNull_1", 0, 3, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }
}
