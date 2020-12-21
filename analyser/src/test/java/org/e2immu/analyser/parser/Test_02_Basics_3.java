
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

import org.e2immu.analyser.analyser.StatementAnalyser;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

/*
Aims to catch problems in assigning properties from the field to the variable in statement 0, setS1.
The values there are a summary of what happened deeper down, which is different from what is in the field.
(The field cannot be @NotNull but locally we know s will not be null at that point.)
 */
public class Test_02_Basics_3 extends CommonTestRunner {


    // not loading in the AnnotatedAPIs, so System.out will have @Modified=1 after println()
    // this also causes a potential null pointer exception, as we don't know if out will be @NotNull

    @Test
    public void test() throws IOException {
        final String TYPE = "org.e2immu.analyser.testexample.Basics_3";
        final String S = TYPE + ".s";

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("setS1".equals(d.methodInfo().name) && "0.0.0".equals(d.statementId())) {
                Assert.assertEquals(StatementAnalyser.STEP_3, d.step());
                if (d.iteration() == 0) {
                    Assert.assertSame(EmptyExpression.NO_VALUE, d.evaluationResult().value());
                } else {
                    Assert.assertSame(EmptyExpression.NO_RETURN_VALUE, d.evaluationResult().value());
                }
            }
            if ("setS1".equals(d.methodInfo().name) && "1".equals(d.statementId())) {
                Assert.assertEquals(StatementAnalyser.STEP_3, d.step());
                // should not be sth like null != s$2, because statement time has not advanced since the assignments
                Assert.assertEquals("null!=input1.contains(\"a\")?\"xyz\":\"abc\"", d.evaluationResult().value().debugOutput());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("setS1".equals(d.methodInfo().name) && S.equals(d.variableName())) {
                if ("1".equals(d.statementId())) {
                    Assert.assertEquals(Level.TRUE, d.getProperty(VariableProperty.ASSIGNED));
                    Assert.assertEquals("input1.contains(\"a\")?\"xyz\":\"abc\"", d.currentValue().toString());
                    Assert.assertEquals("", debug(d.variableInfo().getLinkedVariables()));
                }
            }
            if ("setS2".equals(d.methodInfo().name) && S.equals(d.variableName())) {
                if ("0".equals(d.statementId())) {
                    Assert.assertEquals(Level.TRUE, d.getProperty(VariableProperty.ASSIGNED));
                    Assert.assertEquals("input2", d.currentValue().toString());
                    // not linked to input2, @E2Immutable
                    Assert.assertEquals("", debug(d.variableInfo().getLinkedVariables()));
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("setS1".equals(d.methodInfo().name)) {
                int time1 = d.statementAnalysis().flowData.initialTime.get();
                int time3 = d.statementAnalysis().flowData.timeAfterExecution.get();
                int time4 = d.statementAnalysis().flowData.timeAfterSubBlocks.get();
                if ("0".equals(d.statementId())) {
                    Assert.assertEquals(0, time1);
                    Assert.assertEquals(1, time3);
                    Assert.assertEquals(2, time4); // merge
                }
                if ("0.0.0".equals(d.statementId())) {
                    Assert.assertEquals(1, time1);
                    Assert.assertEquals(2, time3);
                    Assert.assertEquals(2, time4);
                }
                if ("0.0.1".equals(d.statementId())) { // first assignment
                    Assert.assertEquals(2, time1);
                    Assert.assertEquals(2, time3);
                    Assert.assertEquals(2, time4);
                }
                if ("1.0.0".equals(d.statementId())) { // second assignment
                    Assert.assertEquals(0, time1);
                    Assert.assertEquals(0, time3);
                    Assert.assertEquals(0, time4);
                }
                if ("1".equals(d.statementId())) {
                    Assert.assertEquals(2, time1);
                    Assert.assertEquals(2, time3);
                    Assert.assertEquals(2, time4);
                    if (d.iteration() > 0) {
                        Assert.assertNotNull(d.haveError(Message.ASSERT_EVALUATES_TO_CONSTANT_TRUE));
                    }
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("s".equals(d.fieldInfo().name) && d.iteration() > 0) {
                Assert.assertEquals("", debug(d.fieldAnalysis().getLinkedVariables()));
                Assert.assertEquals(MultiLevel.NULLABLE, d.fieldAnalysis().getProperty(VariableProperty.NOT_NULL));
                Assert.assertEquals(Level.FALSE, d.fieldAnalysis().getProperty(VariableProperty.FINAL));
                Assert.assertNull(d.fieldAnalysis().getEffectivelyFinalValue());
            }
        };

        testClass("Basics_3", 0, 2, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

}
