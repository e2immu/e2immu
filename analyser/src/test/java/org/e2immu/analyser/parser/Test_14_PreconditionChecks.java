
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

import org.e2immu.analyser.analyser.StatementAnalyser;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MethodAnalysis;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class Test_14_PreconditionChecks extends CommonTestRunner {

    public Test_14_PreconditionChecks() {
        super(false);
    }

    // either
    @Test
    public void test0() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("either".equals(d.methodInfo().name) && "0".equals(d.statementId())) {
                Assert.assertEquals("null!=e1||null!=e2",
                        d.statementAnalysis().stateData.conditionManager.get().state.toString());
                Assert.assertEquals("null!=e1||null!=e2",
                        d.statementAnalysis().stateData.precondition.get().toString());
            }
        };

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("either".equals(d.methodInfo().name) && "0".equals(d.statementId())) {
                Assert.assertEquals("null==e1&&null==e2", d.evaluationResult().value.toString());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            String name = d.methodInfo().name;
            if ("either".equals(name)) {
                MethodAnalysis methodAnalysis = d.methodAnalysis();
                Assert.assertEquals("null!=e1||null!=e2", methodAnalysis.getPrecondition().toString());
            }
        };


        testClass("PreconditionChecks_0", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build());
    }

    // positive
    @Test
    public void test1() throws IOException {


        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("setPositive1".equals(d.methodInfo().name)) {
                if ("0.0.0".equals(d.statementId())) {
                    if (d.iteration() == 0) {
                        Assert.assertSame(EmptyExpression.NO_VALUE, d.condition());
                        Assert.assertSame(EmptyExpression.NO_VALUE, d.state());
                    } else if (d.iteration() == 1) {
                        Assert.assertEquals("((-1) + (-this.i)) >= 0", d.condition().toString());
                        Assert.assertEquals("((-1) + (-this.i)) >= 0", d.state().toString());
                    } else if (d.iteration() > 1) {
                        Assert.assertEquals("((-1) + (-this.i)) >= 0", d.condition().toString());
                        // the precondition is now fed into the initial state, results in
                        // (((-1) + (-this.i)) >= 0 and this.i >= 0) which should resolve to false
                        Assert.assertEquals("false", d.state().toString());
                    }
                }
                if ("0".equals(d.statementId())) {
                    if (d.iteration() == 0) {
                        Assert.assertSame(EmptyExpression.NO_VALUE, d.condition()); // condition is EMPTY, but because state is NO_VALUE, not written
                        Assert.assertSame(EmptyExpression.NO_VALUE, d.state());
                    } else {
                        Assert.assertSame(EmptyExpression.EMPTY_EXPRESSION, d.condition());
                        Assert.assertEquals("this.i >= 0", d.state().toString());
                    }
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            String name = d.methodInfo().name;
            if ("either".equals(name)) {
                MethodAnalysis methodAnalysis = d.methodAnalysis();
                Assert.assertEquals("null!=e1||null!=e2", methodAnalysis.getPrecondition().toString());
            }
        };


        testClass("PreconditionChecks_1", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    // combined
    @Test
    public void test2() throws IOException {
        testClass("PreconditionChecks_2", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    // integer
    @Test
    public void test3() throws IOException {
        final String TYPE = "org.e2immu.analyser.testexample.PreconditionChecks_3";
        final String INTEGER = TYPE + ".integer";

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("integer".equals(d.fieldInfo().name)) {
                int expectFinal = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                Assert.assertEquals(expectFinal, d.fieldAnalysis().getProperty(VariableProperty.FINAL));
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            String name = d.methodInfo().name;
            if ("setInteger".equals(name)) {
                if (d.iteration() > 0) {
                    Assert.assertEquals("(null == this.integer and ii >= 0)", d.methodAnalysis().getPrecondition().toString());
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("setInteger".equals(d.methodInfo().name)) {
                if (INTEGER.equals(d.variableName())) {
                    if ("0.0.1".equals(d.statementId())) {
                        Assert.assertEquals(Level.TRUE, d.getProperty(VariableProperty.READ));
                    }
                    if ("0.0.2".equals(d.statementId())) {
                        Assert.assertEquals(Level.TRUE, d.getProperty(VariableProperty.READ));
                        Assert.assertEquals(Level.TRUE, d.getProperty(VariableProperty.ASSIGNED));
                    }
                    if ("1".equals(d.statementId())) {
                        Assert.assertEquals(Level.TRUE, d.getProperty(VariableProperty.ASSIGNED));
                        Assert.assertEquals(Level.TRUE, d.getProperty(VariableProperty.READ));
                    }
                }
            }
        };

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("setInteger".equals(d.methodInfo().name)) {
                if ("0.0.1".equals(d.statementId())) {
                    Assert.assertEquals(StatementAnalyser.STEP_3, d.step());
                    String expect = d.iteration() == 0 ? EmptyExpression.NO_VALUE.toString() : "ii";
                    Assert.assertEquals(expect, d.evaluationResult().value.toString());
                }
                if ("0.0.2".equals(d.statementId())) {
                    Assert.assertEquals(StatementAnalyser.STEP_3, d.step());
                    Assert.assertEquals("ii", d.evaluationResult().value.toString());
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("setInteger".equals(d.methodInfo().name)) {
                FieldInfo integer = d.methodInfo().typeInfo.getFieldByName("integer", true);
                if ("0.0.0".equals(d.statementId())) {
                    Assert.assertEquals("ii>=0", d.state().toString());
                }
                if ("0.0.1".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? EmptyExpression.NO_VALUE.toString() : "ii>=0";
                    Assert.assertEquals(expect, d.state().toString());
                }
                if ("0.0.2".equals(d.statementId())) {
                    Assert.assertTrue(d.statementAnalysis().variables.isSet(INTEGER));
                    VariableInfo variableInfo = d.getFieldAsVariable(integer);
                    Assert.assertEquals(Level.TRUE, variableInfo.getProperty(VariableProperty.ASSIGNED));
                }
                if ("0".equals(d.statementId())) {
                    // the synchronized block
                    Assert.assertTrue(d.statementAnalysis().variables.isSet(INTEGER));
                    VariableInfo variableInfo = d.getFieldAsVariable(integer);
                    Assert.assertEquals(Level.TRUE, variableInfo.getProperty(VariableProperty.ASSIGNED));
                }
                if ("1".equals(d.statementId())) {
                    Assert.assertTrue(d.statementAnalysis().variables.isSet(INTEGER));
                    VariableInfo variableInfo = d.getFieldAsVariable(integer);
                    Assert.assertEquals(Level.TRUE, variableInfo.getProperty(VariableProperty.ASSIGNED));

                    if (d.iteration() > 0) {
                        Assert.assertNotNull(d.haveError(Message.CONDITION_EVALUATES_TO_CONSTANT)); // TODO
                    }
                }
            }
        };

        testClass("PreconditionChecks_3", 1, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build());
    }


}
