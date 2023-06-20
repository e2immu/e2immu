
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

package org.e2immu.analyser.parser.minor;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.LinkedVariables;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analysis.impl.FieldAnalysisImpl;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.e2immu.analyser.analyser.Property.*;
import static org.junit.jupiter.api.Assertions.*;

public class Test_33_ExternalNotNull extends CommonTestRunner {

    public Test_33_ExternalNotNull() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("upperCaseO".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "o".equals(fr.fieldInfo.name)) {
                    assertDv(d, DV.TRUE_DV, CNN_TRAVELS_TO_PRECONDITION);
                    assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, CONTEXT_NOT_NULL);
                }
            }
            if ("upperCaseR".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "r".equals(fr.fieldInfo.name)) {
                    String expectValue = d.iteration() <= 1 ? "<f:r>" : "instance type String";
                    assertEquals(expectValue, d.currentValue().toString());
                    assertDv(d, 2, MultiLevel.EFFECTIVELY_NOT_NULL_DV, NOT_NULL_EXPRESSION);
                    // because NNE > 0, there cannot be a warning!
                }
            }
            if ("upperCaseP".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "p".equals(fr.fieldInfo.name)) {
                    String expectValue = d.iteration() <= 1 ? "<f:p>" : "instance type String";
                    assertEquals(expectValue, d.currentValue().toString());
                    assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, CONTEXT_NOT_NULL);
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            int n = d.methodInfo().methodInspection.get().getParameters().size();
            if ("ExternalNotNull_0".equals(d.methodInfo().name) && n == 4) {
                if ("6".equals(d.statementId())) {
                    assertEquals(d.iteration() <= 1,
                            null == d.haveError(Message.Label.CONDITION_EVALUATES_TO_CONSTANT_ENN));
                }
                if ("7".equals(d.statementId())) {
                    assertEquals(d.iteration() <= 1,
                            null == d.haveError(Message.Label.CONDITION_EVALUATES_TO_CONSTANT_ENN));
                }
            }
            if ("upperCaseO".equals(d.methodInfo().name)) {
                assertNull(d.haveError(Message.Label.POTENTIAL_NULL_POINTER_EXCEPTION));
                // because CNN travels to precondition, causes error on the field
            }
            if ("upperCaseP".equals(d.methodInfo().name)) {
                assertNull(d.haveError(Message.Label.POTENTIAL_NULL_POINTER_EXCEPTION));
            }
            if ("upperCaseQ".equals(d.methodInfo().name)) {
                assertNull(d.haveError(Message.Label.POTENTIAL_NULL_POINTER_EXCEPTION));
                // because CNN travels to precondition, causes error on the field
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
                assertDv(d.p(0), 2, MultiLevel.EFFECTIVELY_NOT_NULL_DV, EXTERNAL_NOT_NULL);
                assertDv(d.p(1), 2, MultiLevel.EFFECTIVELY_NOT_NULL_DV, EXTERNAL_NOT_NULL);
            }
            if ("ExternalNotNull_0".equals(d.methodInfo().name) && n == 4) {
                assertDv(d.p(0), 2, MultiLevel.EFFECTIVELY_NOT_NULL_DV, EXTERNAL_NOT_NULL);
                assertDv(d.p(1), 2, MultiLevel.NULLABLE_DV, EXTERNAL_NOT_NULL);
                assertDv(d.p(2), 2, MultiLevel.EFFECTIVELY_NOT_NULL_DV, EXTERNAL_NOT_NULL);
                assertDv(d.p(3), 2, MultiLevel.NULLABLE_DV, EXTERNAL_NOT_NULL);
            }
            if ("setQ".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 2, MultiLevel.NULLABLE_DV, EXTERNAL_NOT_NULL);
            }
            if ("setR".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 2, MultiLevel.EFFECTIVELY_NOT_NULL_DV, EXTERNAL_NOT_NULL);
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            DV effFinal = d.fieldAnalysis().getProperty(Property.FINAL);
            FieldAnalysisImpl.Builder fai = (FieldAnalysisImpl.Builder) d.fieldAnalysis();

            if ("o".equals(d.fieldInfo().name)) {
                assertDv(d, 1, MultiLevel.NULLABLE_DV, EXTERNAL_NOT_NULL);
                assertEquals(DV.TRUE_DV, effFinal);
                String expected = d.iteration() == 0 ? "<f:o>" : "[null,\"hello\"]";
                assertEquals(expected, d.fieldAnalysis().getValue().toString());
                String linked = d.iteration() == 0
                        ? "System.out:-1,p2:-1,q2:-1,r2:-1,s2:-1,this.p:-1,this.q:-1,this.r:-1,this.s:-1"
                        : "";
                assertEquals(linked, d.fieldAnalysis().getLinkedVariables().toString());
                if (d.iteration() > 0) {
                    assertFalse(fai.valuesAreLinkedToParameters(LinkedVariables.LINK_ASSIGNED));
                }
            }
            if ("p".equals(d.fieldInfo().name)) {
                assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, EXTERNAL_NOT_NULL);
                assertEquals(DV.TRUE_DV, effFinal);
                String expected = d.iteration() == 0 ? "<f:p>" : "[p1,p2]";
                assertEquals(expected, d.fieldAnalysis().getValue().toString());
                String linked = d.iteration() == 0
                        ? "System.out:-1,p1:-1,p2:-1,q2:-1,r2:-1,s2:-1,this.o:-1,this.q:-1,this.r:-1,this.s:-1"
                        : "p1:0,p2:0";
                assertEquals(linked, d.fieldAnalysis().getLinkedVariables().toString());
                if (d.iteration() > 0) {
                    assertTrue(fai.valuesAreLinkedToParameters(LinkedVariables.LINK_ASSIGNED));
                }
            }
            if ("q".equals(d.fieldInfo().name)) {
                assertDv(d, 1, MultiLevel.NULLABLE_DV, EXTERNAL_NOT_NULL);
                assertEquals(DV.FALSE_DV, effFinal);
                String linked = d.iteration() == 0
                        ? "System.out:-1,p2:-1,q2:-1,qs:-1,r2:-1,s2:-1,this.o:-1,this.p:-1,this.r:-1,this.s:-1"
                        : "q2:0,qs:0";
                assertEquals(linked, d.fieldAnalysis().getLinkedVariables().toString());
                if (d.iteration() > 0) {
                    // also value null
                    assertFalse(fai.valuesAreLinkedToParameters(LinkedVariables.LINK_ASSIGNED));
                }
            }
            if ("r".equals(d.fieldInfo().name)) {
                assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, EXTERNAL_NOT_NULL);
                assertEquals(DV.FALSE_DV, effFinal);
                String linked = d.iteration() == 0
                        ? "System.out:-1,p2:-1,q2:-1,r1:-1,r2:-1,rs:-1,s2:-1,this.o:-1,this.p:-1,this.q:-1,this.s:-1"
                        : "r1:1,r2:1,rs:1";
                assertEquals(linked, d.fieldAnalysis().getLinkedVariables().toString());
            }
        };
        testClass("ExternalNotNull_0", 0, 4, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build(), new AnalyserConfiguration.Builder()
                .setComputeContextPropertiesOverAllMethods(true)// for NN to travel from method to constructor
                .build());
    }

    @Test
    public void test_1() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            int n = d.methodInfo().methodInspection.get().getParameters().size();
            if ("5".equals(d.statementId()) && "ExternalNotNull_1".equals(d.methodInfo().name) && n == 2) {
                String expected = d.iteration() == 0 ? "<m:println>" : "<no return value>";
                assertEquals(expected, d.evaluationResult().value().toString());
                assertEquals(d.iteration() == 0, d.evaluationResult().causesOfDelay().isDelayed());
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("p".equals(d.fieldInfo().name)) {
                assertEquals(DV.TRUE_DV, d.fieldAnalysis().getProperty(Property.FINAL));
                assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, EXTERNAL_NOT_NULL);
                String expect = d.iteration() == 0 ? "<f:p>" : "[p1,p2]";
                assertEquals(expect, d.fieldAnalysis().getValue().toString());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            int n = d.methodInfo().methodInspection.get().getParameters().size();
            if ("ExternalNotNull_1".equals(d.methodInfo().name) && n == 2) {
                assertDv(d.p(0), 2, MultiLevel.EFFECTIVELY_NOT_NULL_DV, EXTERNAL_NOT_NULL);
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            int n = d.methodInfo().methodInspection.get().getParameters().size();
            if ("ExternalNotNull_1".equals(d.methodInfo().name) && n == 2) {
                if (d.variable() instanceof ParameterInfo p1 && "p1".equals(p1.name) && "5".equals(d.statementId())) {
                    assertDv(d, 2, MultiLevel.EFFECTIVELY_NOT_NULL_DV, EXTERNAL_NOT_NULL);

                    String expectValue = d.iteration() == 0 ? "<p:p1>" : "nullable instance type String/*@Identity*/";
                    assertEquals(expectValue, d.currentValue().toString());
                }
            }
            if ("ExternalNotNull_1".equals(d.methodInfo().name) && n == 4) {
                if (d.variable() instanceof ParameterInfo p2 && "p2".equals(p2.name)) {
                    if ("1".equals(d.statementId())) {
                        assertDv(d, 2, MultiLevel.EFFECTIVELY_NOT_NULL_DV, EXTERNAL_NOT_NULL);
                        assertDv(d, 0, MultiLevel.NULLABLE_DV, CONTEXT_NOT_NULL);
                    }
                }
                if (d.variable() instanceof FieldReference fr && "p".equals(fr.fieldInfo.name) && "5".equals(d.statementId())) {
                    assertDv(d, 2, MultiLevel.EFFECTIVELY_NOT_NULL_DV, EXTERNAL_NOT_NULL);
                    assertDv(d, 0, MultiLevel.NULLABLE_DV, CONTEXT_NOT_NULL);
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
                .build(), new AnalyserConfiguration.Builder()
                .setComputeContextPropertiesOverAllMethods(true)// for NN to travel from method to constructor
                .build());
    }
}
