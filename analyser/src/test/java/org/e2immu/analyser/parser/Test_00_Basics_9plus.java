
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
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterAnalysis;
import org.e2immu.analyser.model.ParameterInfo;
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
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, p0.getProperty(VariableProperty.EXTERNAL_NOT_NULL));
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
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("string".equals(d.fieldInfo().name)) {
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.fieldAnalysis().getProperty(VariableProperty.EXTERNAL_NOT_NULL));
            }
        };
        testClass("Basics_10", 0, 0, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }

    @Test
    public void test_11() throws IOException {
        testClass("Basics_11", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_12() throws IOException {
        testClass("Basics_12", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_13() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("test".equals(d.methodInfo().name)) {
                int nne = d.getProperty(VariableProperty.NOT_NULL_EXPRESSION);
                int cnn = d.getProperty(VariableProperty.CONTEXT_NOT_NULL);
                String value = d.currentValue().toString();

                if (d.variable() instanceof ParameterInfo in1 && "in1".equals(in1.name)) {
                    if ("0".equals(d.statementId())) {
                        // means: there are no fields, we have no opinion, right from the start ->
                        Assert.assertEquals(MultiLevel.DELAY, d.getProperty(VariableProperty.EXTERNAL_NOT_NULL));
                        Assert.assertEquals(MultiLevel.NULLABLE, nne);
                        Assert.assertEquals(MultiLevel.NULLABLE, cnn);
                        Assert.assertEquals("nullable instance type String", value);
                    }
                    if ("4".equals(d.statementId())) {
                        Assert.assertEquals(MultiLevel.NULLABLE, cnn);
                    }
                }
                if (d.variable() instanceof ParameterInfo in2 && "in2".equals(in2.name)) {
                    if ("0".equals(d.statementId())) {
                        Assert.assertEquals(MultiLevel.DELAY, d.getProperty(VariableProperty.EXTERNAL_NOT_NULL));
                        Assert.assertEquals(MultiLevel.NULLABLE, nne);
                        Assert.assertEquals(MultiLevel.NULLABLE, cnn);
                        Assert.assertEquals("nullable instance type String", value);
                    }
                    if ("4".equals(d.statementId())) {
                        Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, cnn);
                    }
                }
                if ("a".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        Assert.assertEquals("in1", value);
                        Assert.assertEquals("in1", d.variableInfo().getStaticallyAssignedVariables().toString());
                        Assert.assertEquals(MultiLevel.NULLABLE, cnn);
                        Assert.assertEquals(MultiLevel.NULLABLE, nne);
                    }
                    if ("2".equals(d.statementId())) {
                        Assert.assertEquals("in2", d.variableInfo().getStaticallyAssignedVariables().toString());
                        Assert.assertEquals(MultiLevel.NULLABLE, cnn);
                    }
                    if ("3".equals(d.statementId())) {
                        Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, cnn);
                    }
                }
                if ("b".equals(d.variableName())) {
                    if ("1".equals(d.statementId())) {
                        Assert.assertEquals("a", d.variableInfo().getStaticallyAssignedVariables().toString());
                        Assert.assertEquals(MultiLevel.NULLABLE, cnn);
                        Assert.assertEquals(MultiLevel.NULLABLE, nne);
                    }
                    if ("2".equals(d.statementId())) {
                        Assert.assertEquals("in1", d.variableInfo().getStaticallyAssignedVariables().toString());
                        Assert.assertEquals(MultiLevel.NULLABLE, cnn);
                    }
                    if ("4".equals(d.statementId())) {
                        Assert.assertEquals(MultiLevel.NULLABLE, cnn);
                    }
                }
            }
        };
        testClass("Basics_13", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }
}
