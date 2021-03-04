
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

import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analyser.VariableInfoContainer;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterAnalysis;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class Test_00_Basics_9plus extends CommonTestRunner {

    public Test_00_Basics_9plus() {
        super(false);
    }

    @Test
    public void test_9() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("setContainsValueHelper".equals(d.methodInfo().name)) {
                Assert.assertEquals("Basics_9.isFact(containsE)?containsE:!Basics_9.isKnown(true)&&retVal&&size>=1",
                        d.evaluationResult().value().toString());
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL,
                        d.evaluationResult().evaluationContext().getProperty(d.evaluationResult().value(),
                                VariableProperty.NOT_NULL_EXPRESSION, false));
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("isFact".equals(d.methodInfo().name) || "isKnown".equals(d.methodInfo().name)) {
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL,
                        d.methodAnalysis().getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                ParameterAnalysis p0 = d.parameterAnalyses().get(0);
                Assert.assertEquals(MultiLevel.NOT_INVOLVED, p0.getProperty(VariableProperty.EXTERNAL_NOT_NULL));
                Assert.assertEquals("Method: " + d.methodInfo().name,
                        MultiLevel.EFFECTIVELY_NOT_NULL, p0.getProperty(VariableProperty.NOT_NULL_PARAMETER));
            }
        };
        testClass("Basics_9", 0, 2, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build());
    }

    @Test
    public void test_10() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("Basics_10".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "string".equals(fr.fieldInfo.name)) {
                    Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL,
                            d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("Basics_10".equals(d.methodInfo().name)) {
                ParameterAnalysis in = d.parameterAnalyses().get(0);
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, in.getProperty(VariableProperty.NOT_NULL_PARAMETER));
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("string".equals(d.fieldInfo().name)) {
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL,
                        d.fieldAnalysis().getProperty(VariableProperty.EXTERNAL_NOT_NULL));
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
        final String NULLABLE_INSTANCE = "nullable instance type String";
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("test".equals(d.methodInfo().name)) {
                String value = d.currentValue().toString();
                int cnn = d.getProperty(VariableProperty.CONTEXT_NOT_NULL);

                if (d.variable() instanceof ParameterInfo in1 && "in".equals(in1.name)) {
                    if ("0".equals(d.statementId()) || "1".equals(d.statementId())) {
                        Assert.assertEquals(NULLABLE_INSTANCE, value);
                    }
                    if ("2".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<p:in>" : NULLABLE_INSTANCE;
                        Assert.assertEquals(expectValue, value);
                        Assert.assertEquals(MultiLevel.NULLABLE, cnn);
                    }
                    if ("3".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<p:in>" : NULLABLE_INSTANCE;
                        Assert.assertEquals(expectValue, value);
                        Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, cnn);
                    }
                    if ("4".equals(d.statementId())) {
                        Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, cnn);
                    }
                }
                if ("s1".equals(d.variableName())) {
                    if ("0".equals(d.statementId()) || "1".equals(d.statementId())) {
                        Assert.assertEquals("in", value);
                    }
                    if ("2".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<v:s1>" : "in";
                        Assert.assertEquals(expectValue, value);
                    }
                }
                if ("s2".equals(d.variableName())) {
                    if ("1".equals(d.statementId()) || "2".equals(d.statementId())) {
                        Assert.assertEquals("in", value);
                    }
                    if ("3".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<v:s2>" : "in";
                        Assert.assertEquals(expectValue, value);
                        Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, cnn);
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
                int enn = d.getProperty(VariableProperty.EXTERNAL_NOT_NULL);
                int nne = d.getProperty(VariableProperty.NOT_NULL_EXPRESSION);
                int cnn = d.getProperty(VariableProperty.CONTEXT_NOT_NULL);
                int cm = d.getProperty(VariableProperty.CONTEXT_MODIFIED);
                String value = d.currentValue().toString();
                String linkedVariables = d.variableInfo().getLinkedVariables().toString();
                String staticallyAssignedVariables = d.variableInfo().getStaticallyAssignedVariables().toString();

                if (d.variable() instanceof ParameterInfo in1 && "in1".equals(in1.name)) {
                    if ("0".equals(d.statementId())) {
                        // means: there are no fields, we have no opinion, right from the start ->
                        Assert.assertEquals(MultiLevel.NOT_INVOLVED, enn);
                        Assert.assertEquals(MultiLevel.NULLABLE, nne);
                        Assert.assertEquals(MultiLevel.NULLABLE, cnn);
                        Assert.assertEquals(Level.FALSE, cm);
                        Assert.assertEquals("nullable instance type String", value);
                        Assert.assertEquals("", linkedVariables);
                    }
                    if ("4".equals(d.statementId())) {
                        Assert.assertEquals(MultiLevel.NULLABLE, cnn);
                        Assert.assertEquals(Level.FALSE, cm);
                        Assert.assertEquals("", linkedVariables);
                    }
                }
                if (d.variable() instanceof ParameterInfo in2 && "in2".equals(in2.name)) {
                    if ("0".equals(d.statementId())) {
                        Assert.assertEquals(MultiLevel.NOT_INVOLVED, enn);
                        Assert.assertEquals(MultiLevel.NULLABLE, nne);
                        Assert.assertEquals(MultiLevel.NULLABLE, cnn);
                        Assert.assertEquals(Level.FALSE, cm);
                        Assert.assertEquals("", linkedVariables);
                        Assert.assertEquals("nullable instance type String", value);
                    }
                    if ("4".equals(d.statementId())) {
                        Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, cnn);
                        Assert.assertEquals(Level.FALSE, cm);
                        Assert.assertEquals("", linkedVariables);
                    }
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("0".equals(d.statementId())) {
                        Assert.assertEquals("<return value>", value);
                        Assert.assertEquals("", staticallyAssignedVariables);
                        Assert.assertEquals(MultiLevel.NULLABLE, cnn);
                        Assert.assertEquals(MultiLevel.NULLABLE, nne);
                        Assert.assertEquals(MultiLevel.NOT_INVOLVED, enn);
                        Assert.assertEquals(Level.FALSE, cm);
                        Assert.assertEquals("", linkedVariables);
                    }
                    if ("4".equals(d.statementId())) {
                        Assert.assertEquals("in1", value);
                        Assert.assertEquals("b", staticallyAssignedVariables);
                        Assert.assertEquals(MultiLevel.NULLABLE, cnn);
                        Assert.assertEquals(MultiLevel.NULLABLE, nne);
                        Assert.assertEquals(MultiLevel.NOT_INVOLVED, enn);
                        Assert.assertEquals(Level.FALSE, cm);
                        Assert.assertEquals("", linkedVariables);
                    }
                }
                if ("a".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        Assert.assertEquals("in1", value);
                        Assert.assertTrue(d.variableInfo().valueIsSet());
                        Assert.assertEquals("in1", staticallyAssignedVariables);
                        Assert.assertEquals(MultiLevel.NOT_INVOLVED, enn);
                        Assert.assertEquals(MultiLevel.NULLABLE, cnn);
                        Assert.assertEquals(MultiLevel.NULLABLE, nne);
                        Assert.assertEquals(Level.FALSE, cm);
                        Assert.assertEquals("", linkedVariables);
                    }
                    if ("2".equals(d.statementId())) {
                        Assert.assertEquals("in2", staticallyAssignedVariables);
                        Assert.assertTrue(d.variableInfo().valueIsSet());
                        Assert.assertEquals(MultiLevel.NULLABLE, cnn);
                        Assert.assertEquals(MultiLevel.NOT_INVOLVED, enn);
                    }
                    if ("3".equals(d.statementId())) {
                        Assert.assertFalse(d.variableInfoContainer().hasMerge());
                        Assert.assertEquals("in2", staticallyAssignedVariables);
                        Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, cnn);
                        Assert.assertEquals(MultiLevel.NOT_INVOLVED, enn);
                        Assert.assertEquals(Level.FALSE, cm);
                        Assert.assertEquals("", linkedVariables);
                    }
                    if ("4".equals(d.statementId())) {
                        Assert.assertEquals("in2", staticallyAssignedVariables);
                        Assert.assertEquals("", linkedVariables);
                    }
                }
                if ("b".equals(d.variableName())) {
                    if ("1".equals(d.statementId())) {
                        Assert.assertEquals("a", staticallyAssignedVariables);
                        Assert.assertEquals(MultiLevel.NULLABLE, cnn);
                        Assert.assertEquals(MultiLevel.NULLABLE, nne);
                        Assert.assertEquals(MultiLevel.NOT_INVOLVED, enn);
                        Assert.assertEquals(Level.FALSE, cm);
                        Assert.assertEquals("", linkedVariables);
                        Assert.assertTrue(d.variableInfo().valueIsSet());
                    }
                    if ("2".equals(d.statementId())) {
                        Assert.assertEquals("in1", staticallyAssignedVariables);
                        Assert.assertEquals(MultiLevel.NULLABLE, cnn);
                        Assert.assertEquals(MultiLevel.NOT_INVOLVED, enn);
                        Assert.assertEquals(Level.FALSE, cm);
                        Assert.assertEquals("", linkedVariables);
                        Assert.assertTrue(d.variableInfo().valueIsSet());
                    }
                    if ("3".equals(d.statementId())) {
                        Assert.assertFalse(d.variableInfoContainer().hasMerge());
                        Assert.assertEquals("in1", staticallyAssignedVariables);
                        Assert.assertEquals(MultiLevel.NULLABLE, cnn);
                        Assert.assertEquals(Level.FALSE, cm);
                        Assert.assertEquals("", linkedVariables);
                    }
                    if ("4".equals(d.statementId())) {
                        Assert.assertEquals(MultiLevel.NULLABLE, cnn);
                        Assert.assertEquals(Level.FALSE, cm);
                        Assert.assertEquals("in1", value);
                        Assert.assertEquals("", linkedVariables);
                        Assert.assertEquals(MultiLevel.NOT_INVOLVED, enn);
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
            int cnn = d.getProperty(VariableProperty.CONTEXT_NOT_NULL);
            if ("getT".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference t && t.fieldInfo.name.equals("t")) {
                    if("0".equals(d.statementId())) {
                        VariableInfo initial = d.variableInfoContainer().getPreviousOrInitial();
                        Assert.assertEquals(MultiLevel.NULLABLE, initial.getProperty(VariableProperty.CONTEXT_NOT_NULL));

                        VariableInfo eval = d.variableInfoContainer().best(VariableInfoContainer.Level.EVALUATION);
                        Assert.assertEquals(MultiLevel.NULLABLE, eval.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                    }

                    String expectValue = d.iteration() == 0 ? "<f:t>" : "nullable instance type T";
                    Assert.assertEquals(expectValue, d.currentValue().toString());

                    if ("0.0.0".equals(d.statementId())) {
                        Assert.assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                    } else {
                        int expectCnn = MultiLevel.NULLABLE;
                        Assert.assertEquals("Stmt " + d.statementId() + " it " + d.iteration(), expectCnn, cnn);
                    }
                }
                if ("t$0".equals(d.variableInfo().variable().simpleName())) {
                    Assert.assertEquals(MultiLevel.NULLABLE, cnn);
                }
            }
        };
        testClass("Basics_14", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }
}
