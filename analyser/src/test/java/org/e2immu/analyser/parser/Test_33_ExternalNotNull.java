
/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterAnalysis;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

import static org.e2immu.analyser.analyser.VariableProperty.EXTERNAL_NOT_NULL;

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
                    Assert.assertEquals(expectValue, d.currentValue().toString());
                    int expectNne = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                    Assert.assertEquals(expectNne, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                    // because NNE > 0, there cannot be a warning!
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            int n = d.methodInfo().methodInspection.get().getParameters().size();
            if ("ExternalNotNull_0".equals(d.methodInfo().name) && n == 4) {
                if ("6".equals(d.statementId())) {
             //       Assert.assertEquals(d.iteration() == 0,
              //              null == d.haveError(Message.CONDITION_EVALUATES_TO_CONSTANT_ENN));
                }
                if ("7".equals(d.statementId())) {
             //       Assert.assertEquals(d.iteration() == 0,
              //              null == d.haveError(Message.CONDITION_EVALUATES_TO_CONSTANT_ENN));
                }
            }
            if ("upperCaseO".equals(d.methodInfo().name)) {
                Assert.assertEquals(d.iteration() == 0,
                        null == d.haveError(Message.POTENTIAL_NULL_POINTER_EXCEPTION));
            }
            if ("upperCaseP".equals(d.methodInfo().name)) {
                Assert.assertNull(d.haveError(Message.POTENTIAL_NULL_POINTER_EXCEPTION));
            }
            if ("upperCaseQ".equals(d.methodInfo().name)) {
                Assert.assertEquals(d.iteration() == 0,
                        null == d.haveError(Message.POTENTIAL_NULL_POINTER_EXCEPTION));
            }
            if ("upperCaseR".equals(d.methodInfo().name)) {
                Assert.assertNull(d.haveError(Message.POTENTIAL_NULL_POINTER_EXCEPTION));
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
                Assert.assertEquals(expectEnnP1, p1.getProperty(EXTERNAL_NOT_NULL));

                ParameterAnalysis r1 = d.parameterAnalyses().get(1);
                int expectEnnR1 = d.iteration() == 0 ? Level.DELAY : MultiLevel.NOT_INVOLVED;
                Assert.assertEquals(expectEnnR1, r1.getProperty(EXTERNAL_NOT_NULL));
            }
            if ("ExternalNotNull_0".equals(d.methodInfo().name) && n == 4) {
                ParameterAnalysis p2 = d.parameterAnalyses().get(0);
                int expectEnnP2 = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                Assert.assertEquals(expectEnnP2, p2.getProperty(EXTERNAL_NOT_NULL));

                ParameterAnalysis q2 = d.parameterAnalyses().get(1);
                int expectEnnQ2 = d.iteration() == 0 ? Level.DELAY : MultiLevel.NOT_INVOLVED;
                Assert.assertEquals(expectEnnQ2, q2.getProperty(EXTERNAL_NOT_NULL));

                ParameterAnalysis r2 = d.parameterAnalyses().get(2);
                int expectEnnR2 = d.iteration() == 0 ? Level.DELAY : MultiLevel.NOT_INVOLVED;
                Assert.assertEquals(expectEnnR2, r2.getProperty(EXTERNAL_NOT_NULL));

                ParameterAnalysis s2 = d.parameterAnalyses().get(3);
                int expectEnnS2 = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
                Assert.assertEquals(expectEnnS2, s2.getProperty(EXTERNAL_NOT_NULL));
            }
            // not involved, because q is @Variable
            if ("setQ".equals(d.methodInfo().name)) {
                ParameterAnalysis qs = d.parameterAnalyses().get(0);
                int expectEnnQs = d.iteration() == 0 ? Level.DELAY : MultiLevel.NOT_INVOLVED;
                Assert.assertEquals(expectEnnQs, qs.getProperty(EXTERNAL_NOT_NULL));
            }
            // not involved, because r is not assigned, @Variable
            if ("setR".equals(d.methodInfo().name)) {
                ParameterAnalysis rs = d.parameterAnalyses().get(0);
                int expectEnnRs = d.iteration() == 0 ? Level.DELAY : MultiLevel.NOT_INVOLVED;
                Assert.assertEquals(expectEnnRs, rs.getProperty(EXTERNAL_NOT_NULL));
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            int enn = d.fieldAnalysis().getProperty(EXTERNAL_NOT_NULL);
            int effFinal = d.fieldAnalysis().getProperty(VariableProperty.FINAL);

            if ("o".equals(d.fieldInfo().name)) {
                Assert.assertEquals(MultiLevel.NULLABLE, enn);
                Assert.assertEquals(Level.TRUE, effFinal);
                Assert.assertEquals("[null,\"hello\"]", d.fieldAnalysis().getEffectivelyFinalValue().toString());
                Assert.assertEquals("", d.fieldAnalysis().getLinkedVariables().toString());
            }
            if ("p".equals(d.fieldInfo().name)) {
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, enn);
                Assert.assertEquals(Level.TRUE, effFinal);
                Assert.assertEquals("[p1,p2]", d.fieldAnalysis().getEffectivelyFinalValue().toString());

            }
            if ("q".equals(d.fieldInfo().name)) {
                Assert.assertEquals(MultiLevel.NULLABLE, enn);
                Assert.assertEquals(Level.FALSE, effFinal);
                Assert.assertEquals("", d.fieldAnalysis().getLinkedVariables().toString());
            }
            if ("r".equals(d.fieldInfo().name)) {
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, enn);
                Assert.assertEquals(Level.FALSE, effFinal);
                Assert.assertEquals("", d.fieldAnalysis().getLinkedVariables().toString());
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
                Assert.assertEquals("<no return value>", d.evaluationResult().value().toString());
                Assert.assertEquals(d.iteration() == 0, d.evaluationResult().someValueWasDelayed());
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            int enn = d.fieldAnalysis().getProperty(EXTERNAL_NOT_NULL);
            int effFinal = d.fieldAnalysis().getProperty(VariableProperty.FINAL);

            if ("p".equals(d.fieldInfo().name)) {
                Assert.assertEquals(Level.TRUE, effFinal);
                int expectEnn = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                Assert.assertEquals(expectEnn, enn);
                if (d.iteration() == 0) {
                    Assert.assertNull(d.fieldAnalysis().getEffectivelyFinalValue());
                } else {
                    Assert.assertEquals("[p1,p2]", d.fieldAnalysis().getEffectivelyFinalValue().toString());
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            int n = d.methodInfo().methodInspection.get().getParameters().size();
            if ("ExternalNotNull_1".equals(d.methodInfo().name) && n == 2) {
                ParameterAnalysis p1 = d.parameterAnalyses().get(0);
                int expectEnnP1 = d.iteration() <= 1 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                Assert.assertEquals(expectEnnP1, p1.getProperty(EXTERNAL_NOT_NULL));
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            int n = d.methodInfo().methodInspection.get().getParameters().size();
            if ("ExternalNotNull_1".equals(d.methodInfo().name) && n == 2) {
                if (d.variable() instanceof ParameterInfo p1 && "p1".equals(p1.name) && "5".equals(d.statementId())) {
                    int expectEnn = d.iteration() <= 1 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                    Assert.assertEquals(expectEnn, d.getProperty(EXTERNAL_NOT_NULL));
                    String expectValue = d.iteration() == 0 ? "<p:p1>" : "nullable instance type String";
                    Assert.assertEquals(expectValue, d.currentValue().toString());
                }
            }
        };

        testClass("ExternalNotNull_1", 0, 4, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }
}
