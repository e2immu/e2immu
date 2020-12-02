
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
import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MethodAnalysis;
import org.e2immu.analyser.model.value.UnknownValue;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class Test_14_PreconditionChecks extends CommonTestRunner {

    public Test_14_PreconditionChecks() {
        super(false);
    }

    private static final String TYPE = "org.e2immu.analyser.testexample.PreconditionChecks";
    private static final String E1 = TYPE + ".either(String,String):0:e1";
    private static final String E2 = TYPE + ".either(String,String):1:e2";
    private static final String II = TYPE + ".setInteger(int):0:ii";
    private static final String INTEGER = TYPE + ".integer";

    EvaluationResultVisitor evaluationResultVisitor = d -> {
        if ("either".equals(d.methodInfo().name) && "0".equals(d.statementId())) {
            Assert.assertEquals("(null == " + E1 + " and null == " + E2 + ")", d.evaluationResult().value.toString());
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
        if ("setInteger".equals(d.methodInfo().name)) {
            FieldInfo integer = d.methodInfo().typeInfo.getFieldByName("integer", true);
            if ("0.0.0".equals(d.statementId())) {
                Assert.assertEquals(II + " >= 0", d.state().toString());
            }
            if ("0.0.2".equals(d.statementId())) {
                Assert.assertTrue(d.statementAnalysis().variables.isSet(INTEGER));
                VariableInfo tv = d.getFieldAsVariable(integer);
                Assert.assertEquals(Level.TRUE, tv.getProperty(VariableProperty.ASSIGNED));
            }
            if ("1".equals(d.statementId())) {
                Assert.assertTrue(d.statementAnalysis().variables.isSet(INTEGER));
                VariableInfo tv = d.getFieldAsVariable(integer);
                Assert.assertEquals(Level.TRUE, tv.getProperty(VariableProperty.ASSIGNED));

                if (d.iteration() > 0) {
                    Assert.assertNotNull(d.haveError(Message.CONDITION_EVALUATES_TO_CONSTANT)); // TODO
                }
            }
        }
        if ("either".equals(d.methodInfo().name) && "0".equals(d.statementId())) {
            Assert.assertEquals("(not (null == " + E1 + ") or not (null == " + E2 + "))",
                    d.statementAnalysis().stateData.conditionManager.get().state.toString());
            Assert.assertEquals("(not (null == " + E1 + ") or not (null == " + E2 + "))",
                    d.statementAnalysis().stateData.precondition.get().toString());
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        String name = d.methodInfo().name;
        if ("setInteger".equals(name)) {
            if (d.iteration() > 0) {
                Assert.assertEquals("(null == this.integer and ii >= 0)", d.methodAnalysis().getPrecondition().toString());
            }
        }
        if ("either".equals(name)) {
            MethodAnalysis methodAnalysis = d.methodAnalysis();
            Assert.assertEquals("(not (null == " + E1 + ") or not (null == " + E2 + "))", methodAnalysis.getPrecondition().toString());
        }
        if ("setPositive1".equals(name) && d.iteration() > 0) {
            Assert.assertEquals("this.i >= 0", d.methodAnalysis().getPrecondition().toString());
        }
    };

    FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
        if ("integer".equals(d.fieldInfo().name)) {
            int expectFinal = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
            Assert.assertEquals(expectFinal, d.fieldAnalysis().getProperty(VariableProperty.FINAL));
        }
    };

    @Test
    public void test() throws IOException {
        testClass("PreconditionChecks", 1, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build());
    }

}
