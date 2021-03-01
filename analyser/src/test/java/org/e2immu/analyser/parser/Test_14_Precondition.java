
/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.testexample.Precondition_4;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class Test_14_Precondition extends CommonTestRunner {

    public Test_14_Precondition() {
        super(true);
    }

    // either
    @Test
    public void test0() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("either".equals(d.methodInfo().name)) {
                if ("0.0.0".equals(d.statementId())) {
                    Assert.assertEquals("null==e1&&null==e2",
                            d.statementAnalysis().stateData.getConditionManagerForNextStatement().condition().toString());
                    Assert.assertEquals("null!=e1||null!=e2",
                            d.statementAnalysis().stateData.getPrecondition().toString());
                }
                if ("0".equals(d.statementId())) {
                    Assert.assertEquals("true",
                            d.statementAnalysis().stateData.getConditionManagerForNextStatement().state().toString());
                    Assert.assertTrue(d.statementAnalysis().stateData.getPrecondition().isBoolValueTrue());
                    Assert.assertEquals("null!=e1||null!=e2",
                            d.statementAnalysis().methodLevelData.getCombinedPrecondition().toString());
                }
            }
        };

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("either".equals(d.methodInfo().name) && "0".equals(d.statementId())) {
                Assert.assertEquals("null==e1&&null==e2", d.evaluationResult().value().toString());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            String name = d.methodInfo().name;
            if ("either".equals(name)) {
                MethodAnalysis methodAnalysis = d.methodAnalysis();
                Assert.assertEquals("null!=e1||null!=e2", methodAnalysis.getPrecondition().toString());
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> Assert.assertEquals(4, d.typeInfo().typeInspection.get().methods().size());

        testClass("Precondition_0", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .build());
    }

    // positive
    @Test
    public void test1() throws IOException {


        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("setPositive1".equals(d.methodInfo().name)) {
                if ("0.0.0".equals(d.statementId())) {
                    if (d.iteration() == 0) {
                        Assert.assertTrue(d.localConditionManager().isDelayed());
                        Assert.assertNull(d.statementAnalysis().stateData.getPrecondition());
                        Assert.assertNull(d.statementAnalysis().methodLevelData.getCombinedPrecondition());
                    } else if (d.iteration() == 1) {
                        Assert.assertEquals("org.e2immu.analyser.testexample.Precondition_1.i$0<=-1", d.condition().toString());
                        Assert.assertEquals("i>=0", d.statementAnalysis().stateData.getPrecondition().toString());
                        Assert.assertEquals("i>=0", d.statementAnalysis().methodLevelData.getCombinedPrecondition().toString());
                    }
                }
                if ("0".equals(d.statementId())) {
                    Assert.assertTrue(d.condition().isBoolValueTrue());
                    Assert.assertTrue(d.state().isBoolValueTrue());
                    if (d.iteration() > 0) {
                        Assert.assertEquals("i>=0", d.statementAnalysis().methodLevelData.getCombinedPrecondition().toString());
                    }
                }
                if ("0".equals(d.statementId())) {
                    if (d.iteration() == 0) {
                        Assert.assertNull("Statement " + d.statementId(), d.statementAnalysis().methodLevelData.getCombinedPrecondition());
                    } else {
                        Assert.assertEquals("i>=0", d.statementAnalysis().methodLevelData.getCombinedPrecondition().toString());
                    }
                }
                if ("1".equals(d.statementId())) {
                    Assert.assertEquals(d.iteration() == 0, d.localConditionManager().isDelayed());
                }
            }
        };

        testClass("Precondition_1", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

    // combined
    @Test
    public void test2() throws IOException {
        testClass("Precondition_2", 1, 0, new DebugConfiguration.Builder()
                .build());
    }

    // integer
    @Test
    public void test3() throws IOException {
        final String TYPE = "org.e2immu.analyser.testexample.Precondition_3";
        final String INTEGER = TYPE + ".integer";
        final String RETURN_VAR = TYPE + ".setInteger(int)";

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("integer".equals(d.fieldInfo().name)) {
                Assert.assertEquals(Level.FALSE, d.fieldAnalysis().getProperty(VariableProperty.FINAL));
                // the return value is delayed
                int expectEnn = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
                Assert.assertEquals(expectEnn,
                        d.fieldAnalysis().getProperty(VariableProperty.EXTERNAL_NOT_NULL));
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            String name = d.methodInfo().name;
            if ("setInteger".equals(name)) {
                if (d.iteration() > 0) {
                    Assert.assertEquals("null==integer&&ii>=0", d.methodAnalysis().getPrecondition().toString());
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("setInteger".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "integer".equals(fr.fieldInfo.name)) {
                    Assert.assertEquals(INTEGER, d.variableName());
                    if ("0.0.1.0.0".equals(d.statementId())) {
                        Assert.assertTrue(d.variableInfo().isRead());
                        // that system only works for ==null, not for !=null
                        Assert.assertEquals(Level.DELAY, d.getProperty(VariableProperty.CONTEXT_NOT_NULL_FOR_PARENT_DELAY));
                    }
                    if ("0.0.1".equals(d.statementId())) {
                        Assert.assertTrue(d.variableInfo().isRead());
                        String expectValue = d.iteration() == 0 ? "<f:integer>" : "nullable instance type Integer";
                //        Assert.assertEquals(expectValue, d.currentValue().toString());
                    }
                    if ("0.0.2".equals(d.statementId())) {
                        Assert.assertTrue(d.variableInfo().isRead());
                        Assert.assertTrue(d.variableInfo().isAssigned());
                    }
                    if ("1".equals(d.statementId())) {
                        Assert.assertTrue(d.variableInfo().isRead());
                        Assert.assertTrue(d.variableInfo().isAssigned());
                    }
                }

                if (d.variable() instanceof ReturnVariable) {
                    Assert.assertEquals(RETURN_VAR, d.variableName());
                    if ("1".equals(d.statementId())) {
                        if (d.iteration() == 0) {
                            // <s:int>
                            Assert.assertEquals("<s:int>", d.currentValue().toString());
                        } else {
                            Assert.assertEquals("ii", d.currentValue().toString());
                        }
                        Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL,
                                d.getPropertyOfCurrentValue(VariableProperty.NOT_NULL_EXPRESSION));
                    }
                }
            }
        };

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("setInteger".equals(d.methodInfo().name)) {
                if ("0.0.1".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? "null!=<f:integer>" :
                            "null!=org.e2immu.analyser.testexample.Precondition_3.integer$0";
                    Assert.assertEquals(expect, d.evaluationResult().value().toString());
                }
                if ("0.0.2".equals(d.statementId())) {
                    String expect = "ii";
                    Assert.assertEquals(expect, d.evaluationResult().value().toString());
                }
                if ("1".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? "ii>=0?ii:null" : "ii";
                    Assert.assertEquals(expect, d.evaluationResult().value().toString());
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("setInteger".equals(d.methodInfo().name)) {
                FieldInfo integer = d.methodInfo().typeInfo.getFieldByName("integer", true);
                if ("0.0.0".equals(d.statementId())) {
                    Assert.assertEquals("true", d.state().toString());
                    Assert.assertEquals("ii>=0", d.statementAnalysis().methodLevelData.getCombinedPrecondition().toString());
                }
                if ("0.0.0.0.0".equals(d.statementId())) {
                    Assert.assertEquals("ii<=-1", d.condition().toString());
                }
                if ("0.0.1.0.0".equals(d.statementId())) {
                    String expectCondition = d.iteration() == 0 ? "null!=<f:integer>" :
                            "null!=org.e2immu.analyser.testexample.Precondition_3.integer$0";
                    Assert.assertEquals(expectCondition, d.condition().toString());
                }
                if ("0.0.2".equals(d.statementId())) {
                    Assert.assertEquals(d.iteration() == 0, d.localConditionManager().isDelayed());

                    Assert.assertTrue(d.statementAnalysis().variables.isSet(INTEGER));
                    VariableInfo variableInfo = d.getFieldAsVariable(integer);
                    Assert.assertTrue(variableInfo.isAssigned());

                    if (d.iteration() == 0) {
                        Assert.assertNull(d.statementAnalysis().methodLevelData.getCombinedPrecondition());
                    } else {
                        Assert.assertEquals("null==integer&&ii>=0",
                                d.statementAnalysis().methodLevelData.getCombinedPrecondition().toString());
                    }
                }
                if ("0".equals(d.statementId())) {
                    // the synchronized block
                    Assert.assertTrue(d.statementAnalysis().variables.isSet(INTEGER));
                    VariableInfo variableInfo = d.getFieldAsVariable(integer);
                    Assert.assertTrue(variableInfo.isAssigned());

                    String expect = d.iteration() == 0 ? "true" : "null==integer&&ii>=0";
                    Assert.assertEquals(expect, d.statementAnalysis().methodLevelData.getCombinedPreconditionOrDelay().toString());
                    Assert.assertEquals(d.iteration() == 0, d.statementAnalysis().methodLevelData.combinedPreconditionIsDelayed());
                }
                if ("1".equals(d.statementId())) {
                    Assert.assertEquals(d.iteration() == 0, d.localConditionManager().isDelayed());

                    Assert.assertTrue(d.statementAnalysis().variables.isSet(INTEGER));
                    VariableInfo variableInfo = d.getFieldAsVariable(integer);
                    Assert.assertTrue(variableInfo.isAssigned());

                    if (d.iteration() > 0) {
                        Assert.assertNotNull(d.haveError(Message.INLINE_CONDITION_EVALUATES_TO_CONSTANT));
                    }
                }
            }
        };

        testClass("Precondition_3", 1, 0, new DebugConfiguration.Builder()
           //     .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
            //    .addStatementAnalyserVisitor(statementAnalyserVisitor)
           //     .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
           //     .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
           //     .addEvaluationResultVisitor(evaluationResultVisitor)
                .build());
    }

    @Test
    public void test_4() throws IOException {
        TypeContext typeContext = testClass("Precondition_4", 0, 0, new DebugConfiguration.Builder()
                .build());
        TypeInfo pc4 = typeContext.getFullyQualified(Precondition_4.class);
        MethodInfo test = pc4.findUniqueMethod("test", 1);

        MethodAnalysis methodAnalysis = test.methodAnalysis.get();
        Assert.assertEquals(1, methodAnalysis.getComputedCompanions().size());
        Assert.assertEquals("return !strings.contains(\"a\");", methodAnalysis.getComputedCompanions().values()
                .stream().findFirst().orElseThrow()
                .methodInspection.get().getMethodBody().structure.statements().get(0).minimalOutput());
    }

}
