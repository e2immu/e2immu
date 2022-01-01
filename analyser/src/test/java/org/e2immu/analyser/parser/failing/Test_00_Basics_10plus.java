
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
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analyser.VariableInfoContainer;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.expression.InlinedMethod;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.EvaluationResultVisitor;
import org.e2immu.analyser.visitor.FieldAnalyserVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.e2immu.analyser.analyser.Property.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Test_00_Basics_10plus extends CommonTestRunner {

    public Test_00_Basics_10plus() {
        super(false);
    }

    @Test
    public void test_9() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("setContainsValueHelper".equals(d.methodInfo().name)) {
                assertEquals("Basics_9.isFact(containsE)?containsE:!Basics_9.isKnown(true)&&retVal&&size>=1",
                        d.evaluationResult().value().toString());
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV,
                        d.evaluationResult().evaluationContext().getProperty(d.evaluationResult().value(),
                                NOT_NULL_EXPRESSION, false, false));
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("isFact".equals(d.methodInfo().name) || "isKnown".equals(d.methodInfo().name)) {
                assertEquals(DV.FALSE_DV, d.methodAnalysis().getProperty(MODIFIED_METHOD));
                assertEquals(1, d.methodAnalysis().getLastStatement().statementTime(VariableInfoContainer.Level.MERGE));

                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV,
                        d.methodAnalysis().getProperty(NOT_NULL_EXPRESSION));
                ParameterAnalysis p0 = d.parameterAnalyses().get(0);
                assertEquals(MultiLevel.NOT_INVOLVED_DV, p0.getProperty(EXTERNAL_NOT_NULL));
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, p0.getProperty(NOT_NULL_PARAMETER),
                        "Method: " + d.methodInfo().name);
            }
            if ("setContainsValueHelper".equals(d.methodInfo().name)) {
                assertEquals(DV.FALSE_DV, d.methodAnalysis().getProperty(MODIFIED_METHOD));
                assertEquals(1, d.methodAnalysis().getLastStatement().statementTime(VariableInfoContainer.Level.MERGE));

                assertEquals("Basics_9.isFact(containsE)?containsE:!Basics_9.isKnown(true)&&retVal&&size>=1",
                        d.methodAnalysis().getSingleReturnValue().toString());
                assertTrue(d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod,
                        "class is " + d.methodAnalysis().getSingleReturnValue().getClass());
            }
            if ("test1".equals(d.methodInfo().name)) {
                assertEquals(DV.FALSE_DV, d.methodAnalysis().getProperty(MODIFIED_METHOD));
                assertEquals(1, d.methodAnalysis().getLastStatement().statementTime(VariableInfoContainer.Level.MERGE));


                assertEquals("Basics_9.isFact(contains)?contains:!Basics_9.isKnown(true)&&isEmpty",
                        d.methodAnalysis().getSingleReturnValue().toString());
                assertTrue(d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod,
                        "class is " + d.methodAnalysis().getSingleReturnValue().getClass());

                ParameterAnalysis contains = d.parameterAnalyses().get(0);
                assertEquals(MultiLevel.NOT_INVOLVED_DV, contains.getProperty(EXTERNAL_IMMUTABLE));
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("test1".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo contains && "contains".equals(contains.name)) {
                    assertEquals(MultiLevel.NOT_INVOLVED_DV, d.getProperty(EXTERNAL_IMMUTABLE));
                    assertEquals("contains:0,return test1:1", d.variableInfo().getLinkedVariables().toString());
                }
            }
        };

        testClass("Basics_9", 0, 2, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_10() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("Basics_10".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "string".equals(fr.fieldInfo.name)) {
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(NOT_NULL_EXPRESSION));
                }
            }
            if ("getString".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "string".equals(fr.fieldInfo.name)) {
                    assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, EXTERNAL_NOT_NULL);
                    assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, NOT_NULL_EXPRESSION);

                    String expectValue = d.iteration() == 0 ? "<f:string>" : "instance type String";
                    assertEquals(expectValue, d.currentValue().toString());
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("Basics_10".equals(d.methodInfo().name)) {
                ParameterAnalysis in = d.parameterAnalyses().get(0);
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, in.getProperty(NOT_NULL_PARAMETER));
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("string".equals(d.fieldInfo().name)) {
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV,
                        d.fieldAnalysis().getProperty(EXTERNAL_NOT_NULL));
                assertEquals("in", d.fieldAnalysis().getValue().toString());
            }
        };
        testClass("Basics_10", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_11() throws IOException {
        final String NULLABLE_INSTANCE = "nullable instance type String/*@Identity*/";
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("test".equals(d.methodInfo().name)) {
                String value = d.currentValue().toString();
                DV cnn = d.getProperty(CONTEXT_NOT_NULL);

                if (d.variable() instanceof ParameterInfo in1 && "in".equals(in1.name)) {
                    if ("0".equals(d.statementId()) || "1".equals(d.statementId())) {
                        assertEquals(NULLABLE_INSTANCE, value);
                    }
                    if ("2".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<p:in>" : NULLABLE_INSTANCE;
                        assertEquals(expectValue, value);
                        assertEquals(MultiLevel.NULLABLE_DV, cnn);
                    }
                    if ("3".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<p:in>" : NULLABLE_INSTANCE;
                        assertEquals(expectValue, value);
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, cnn);
                    }
                    if ("4".equals(d.statementId())) {
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, cnn);
                    }
                }
                if ("s1".equals(d.variableName())) {
                    if ("0".equals(d.statementId()) || "1".equals(d.statementId())) {
                        assertEquals("in", value);
                    }
                    if ("2".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<v:s1>" : "in";
                        assertEquals(expectValue, value);
                    }
                }
                if ("s2".equals(d.variableName())) {
                    if ("1".equals(d.statementId()) || "2".equals(d.statementId())) {
                        assertEquals("in", value);
                    }
                    if ("3".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<v:s2>" : "in";
                        assertEquals(expectValue, value);
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, cnn);
                    }
                }
            }
        };
        // warning: out potential null pointer (x1) and assert always true (x1)
        testClass("Basics_11", 0, 2, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_12() throws IOException {
        testClass("Basics_12", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    /*
    linked variables is empty all around because String is @E2Immutable
     */
    @Test
    public void test_13() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("test".equals(d.methodInfo().name)) {
                DV enn = d.getProperty(EXTERNAL_NOT_NULL);
                DV nne = d.getProperty(NOT_NULL_EXPRESSION);
                DV cnn = d.getProperty(CONTEXT_NOT_NULL);
                DV cm = d.getProperty(CONTEXT_MODIFIED);
                String value = d.currentValue().toString();
                String linkedVariables = d.variableInfo().getLinkedVariables().toString();

                if (d.variable() instanceof ParameterInfo in1 && "in1".equals(in1.name)) {
                    if ("0".equals(d.statementId())) {
                        // means: there are no fields, we have no opinion, right from the start ->
                        assertEquals(MultiLevel.NOT_INVOLVED_DV, enn);
                        assertEquals(MultiLevel.NULLABLE_DV, nne);
                        assertEquals(MultiLevel.NULLABLE_DV, cnn);
                        assertEquals(DV.FALSE_DV, cm);
                        assertEquals("nullable instance type String/*@Identity*/", value);
                        assertEquals("a:0,in1:0", linkedVariables); // symmetrical!
                    }
                    if ("4".equals(d.statementId())) {
                        assertEquals(MultiLevel.NULLABLE_DV, cnn);
                        assertEquals(DV.FALSE_DV, cm);
                        assertEquals("b:1,in1:0,return test:1", linkedVariables);
                    }
                }
                if (d.variable() instanceof ParameterInfo in2 && "in2".equals(in2.name)) {
                    if ("0".equals(d.statementId())) {
                        assertEquals(MultiLevel.NOT_INVOLVED_DV, enn);
                        assertEquals(MultiLevel.NULLABLE_DV, nne);
                        assertEquals(MultiLevel.NULLABLE_DV, cnn);
                        assertEquals(DV.FALSE_DV, cm);
                        assertEquals("in2:0", linkedVariables);
                        assertEquals("nullable instance type String", value);
                    }
                    if ("4".equals(d.statementId())) {
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, cnn);
                        assertEquals(DV.FALSE_DV, cm);
                        assertEquals("a:0,in2:0", linkedVariables);
                    }
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("<return value>", value);
                        assertEquals("return test:0", linkedVariables);
                        assertEquals(MultiLevel.NULLABLE_DV, cnn);
                        assertEquals(MultiLevel.NULLABLE_DV, nne);
                        assertEquals(MultiLevel.NOT_INVOLVED_DV, enn);
                        assertEquals(DV.FALSE_DV, cm);
                    }
                    if ("4".equals(d.statementId())) {
                        assertEquals("in1", value);
                        assertEquals("b:0,in1:1,return test:0", linkedVariables);
                        assertEquals(MultiLevel.NULLABLE_DV, cnn);
                        assertEquals(MultiLevel.NULLABLE_DV, nne);
                        assertEquals(MultiLevel.NOT_INVOLVED_DV, enn);
                        assertEquals(DV.FALSE_DV, cm);
                    }
                }
                if ("a".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("in1", value);
                        assertTrue(d.variableInfo().valueIsSet());
                        assertEquals("a:0,in1:0", linkedVariables);
                        assertEquals(MultiLevel.NOT_INVOLVED_DV, enn);
                        assertEquals(MultiLevel.NULLABLE_DV, cnn);
                        assertEquals(MultiLevel.NULLABLE_DV, nne);
                        assertEquals(DV.FALSE_DV, cm);
                    }
                    if ("2".equals(d.statementId())) {
                        assertEquals("a:0,in2:0", linkedVariables);
                        assertTrue(d.variableInfo().valueIsSet());
                        assertEquals(MultiLevel.NULLABLE_DV, cnn);
                        assertEquals(MultiLevel.NOT_INVOLVED_DV, enn);
                    }
                    if ("3".equals(d.statementId())) {
                        assertEquals("a:0,in2:0", linkedVariables);
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, cnn);
                        assertEquals(MultiLevel.NOT_INVOLVED_DV, enn);
                        assertEquals(DV.FALSE_DV, cm);
                    }
                    if ("4".equals(d.statementId())) {
                        assertEquals("a:0,in2:0", linkedVariables);
                    }
                }
                if ("b".equals(d.variableName())) {
                    if ("1".equals(d.statementId())) {
                        assertEquals("a:0,b:0,in1:1", linkedVariables);
                        assertEquals(MultiLevel.NULLABLE_DV, cnn);
                        assertEquals(MultiLevel.NULLABLE_DV, nne);
                        assertEquals(MultiLevel.NOT_INVOLVED_DV, enn);
                        assertEquals(DV.FALSE_DV, cm);
                        assertTrue(d.variableInfo().valueIsSet());
                    }
                    if ("2".equals(d.statementId())) {
                        assertEquals("b:0,in1:1", linkedVariables);
                        assertEquals(MultiLevel.NULLABLE_DV, cnn);
                        assertEquals(MultiLevel.NOT_INVOLVED_DV, enn);
                        assertEquals(DV.FALSE_DV, cm);
                        assertTrue(d.variableInfo().valueIsSet());
                    }
                    if ("3".equals(d.statementId())) {
                        assertEquals("b:0,in1:1", linkedVariables);
                        assertEquals(MultiLevel.NULLABLE_DV, cnn);
                        assertEquals(DV.FALSE_DV, cm);
                    }
                    if ("4".equals(d.statementId())) {
                        assertEquals(MultiLevel.NULLABLE_DV, cnn);
                        assertEquals(DV.FALSE_DV, cm);
                        assertEquals("in1", value);
                        assertEquals("b:0,in1:1,return test:0", linkedVariables);
                        assertEquals(MultiLevel.NOT_INVOLVED_DV, enn);
                    }
                }
            }
        };
        testClass("Basics_13", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    /*
    copy of the SetOnce delay problems (20210304)
     */
    @Test
    public void test_14() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            DV cnn = d.getProperty(CONTEXT_NOT_NULL);
            DV enn = d.getProperty(EXTERNAL_NOT_NULL);
            if ("setT".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo p && "t".equals(p.name)) {
                    assertEquals(DV.TRUE_DV, d.getProperty(IDENTITY));
                    // not contracted
                    assertEquals(DV.FALSE_DV, d.getProperty(CONTAINER));
                }
            }
            if ("getT".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference t && t.fieldInfo.name.equals("t")) {
                    if ("0".equals(d.statementId())) {
                        VariableInfo initial = d.variableInfoContainer().getPreviousOrInitial();
                        assertEquals(MultiLevel.NULLABLE_DV, initial.getProperty(CONTEXT_NOT_NULL));

                        VariableInfo eval = d.variableInfoContainer().best(VariableInfoContainer.Level.EVALUATION);
                        assertEquals(MultiLevel.NULLABLE_DV, eval.getProperty(CONTEXT_NOT_NULL));
                    }

                    String expectValue = d.iteration() == 0 ? "<f:t>" : "nullable instance type T";
                    assertEquals(expectValue, d.currentValue().toString());

                    assertDv(d, 1, MultiLevel.NULLABLE_DV, EXTERNAL_NOT_NULL);

                    if ("0.0.0".equals(d.statementId())) {
                        assertEquals(MultiLevel.NULLABLE_DV, d.getProperty(CONTEXT_NOT_NULL));
                    } else {
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, CONTEXT_NOT_NULL);
                    }
                }
                if ("t$0".equals(d.variableInfo().variable().simpleName())) {
                    if ("1".equals(d.statementId())) {
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, cnn);
                    }
                    assertEquals(MultiLevel.NULLABLE_DV, enn);
                }
            }
            if (d.iteration() > 1) {
                assertTrue(enn.isDone());
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("t".equals(d.fieldInfo().name)) {
                assertEquals(MultiLevel.NULLABLE_DV, d.fieldAnalysis().getProperty(EXTERNAL_NOT_NULL));
                assertEquals(DV.FALSE_DV, d.fieldAnalysis().getProperty(FINAL));
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("setT".equals(d.methodInfo().name)) {
                ParameterAnalysis p0 = d.parameterAnalyses().get(0);
                // not contracted
                assertEquals(DV.FALSE_DV, p0.getProperty(CONTAINER));
            }
        };

        testClass("Basics_14", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }
}
