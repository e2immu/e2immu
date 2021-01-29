
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

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static org.e2immu.analyser.analyser.FlowData.Execution.CONDITIONALLY;
import static org.e2immu.analyser.analyser.FlowData.Execution.DELAYED_EXECUTION;

public class Test_Own_01_SMapList extends CommonTestRunner {

    EvaluationResultVisitor evaluationResultVisitor = d -> {
        if ("addAll".equals(d.methodInfo().name)) {
            if ("1.0.0".equals(d.statementId())) {
                String expectValue = d.iteration() == 0 ? "xx" : "dest.get(e$1.getKey())";
                Assert.assertEquals(expectValue, d.evaluationResult().value().toString());
            }
            if ("1.0.1".equals(d.statementId())) {
                String expectValue = d.iteration() == 0 ? "xx" : "null==dest.get(e$1.getKey())";
                Assert.assertEquals(expectValue, d.evaluationResult().value().toString());
            }
            if ("1".equals(d.statementId())) {
                Assert.assertEquals("instance type Set<Map.Entry<K, V>>", d.evaluationResult().value().toString());
            }
        }
    };

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("list".equals(d.methodInfo().name)) {
            final String RET_VAR = "org.e2immu.analyser.util.SMapList.list(Map<A, List<B>>,A)";
            if (RET_VAR.equals(d.variableName())) {
                if ("2".equals(d.statementId())) {
                    // note the absence of null!=a
                    Assert.assertEquals("null==map.get(a)?List.of():<return value>", d.currentValue().toString());
                }
            }

            if ("list".equals(d.variableName())) {
                Assert.assertEquals("map.get(a)", d.currentValue().toString());

                // NOTE: this is in contradiction with the state, but here we test the fact that get can return null
                Assert.assertEquals(MultiLevel.NULLABLE, d.getPropertyOfCurrentValue(VariableProperty.NOT_NULL));
            }
        }
        if ("add".equals(d.methodInfo().name) && d.variable() instanceof ParameterInfo bs && "bs".equals(bs.simpleName())) {
            if ("3".equals(d.statementId())) {
                Assert.assertEquals(Level.FALSE, d.getProperty(VariableProperty.MODIFIED));
                Assert.assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL, d.getProperty(VariableProperty.NOT_NULL));
            }
        }

        if ("addAll".equals(d.methodInfo().name)) {
            if ("e".equals(d.variableName()) && "1.0.0".equals(d.statementId())) {
                String expectValue = d.iteration() == 0 ? "xx" : "instance type Entry<A, List<B>>";
                Assert.assertEquals(expectValue, d.currentValue().toString());
            }
            if (d.variable() instanceof ParameterInfo dest && dest.name.equals("dest")) {
                if ("0".equals(d.statementId())) {
                    Assert.assertEquals("nullable? instance type Map<A, List<B>>", d.currentValue().toString());
                    Assert.assertEquals("", d.variableInfo().getLinkedVariables().toString());
                }
            }
            if ("inDest".equals(d.variableName())) {
                if ("1.0.0".equals(d.statementId())) {
                    String expectValue = d.iteration() == 0 ? "xx" : "dest.get(e$1.getKey())";
                    Assert.assertEquals(expectValue, d.currentValue().toString());
                }
            }
            if ("change".equals(d.variableName())) {
                if ("1.0.1.0.1".equals(d.statementId())) {
                    String expectValue = d.iteration() == 0 ? "xx" : "change$1||null==dest.get(e$1.getKey())";
                    Assert.assertEquals(expectValue, d.currentValue().toString());
                    int expectNotNull = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                    Assert.assertEquals(expectNotNull, d.getProperty(VariableProperty.NOT_NULL));
                    String expectLinked = d.iteration() == 0 ? LinkedVariables.DELAY_STRING : "change$1$1_0_1_0_1-E";
                    Assert.assertEquals(expectLinked, d.variableInfo().getLinkedVariables().toString());
                }
                // 2nd branch, merge of an if-statement
                if ("1.0.1.1.0".equals(d.statementId())) {
                    String expectValue = d.iteration() == 0 ? "xx" : "dest.get(e$1.getKey()).addAll(e$1.getValue())";
                    Assert.assertEquals(expectValue, d.currentValue().toString());
                    int expectNotNull = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                    Assert.assertEquals(expectNotNull, d.getProperty(VariableProperty.NOT_NULL));
                    String expectLinked = d.iteration() == 0 ? LinkedVariables.DELAY_STRING : "change$1$1_0_1_1_0_0_0-E";
                    Assert.assertEquals(expectLinked, d.variableInfo().getLinkedVariables().toString());
                }
                // merge of the two above
                if ("1.0.1".equals(d.statementId())) {
                    String expectValue = d.iteration() == 0 ? "xx" : "null==dest.get(e$1.getKey())?change$1||null==dest.get(e$1.getKey()):dest.get(e$1.getKey()).addAll(e$1.getValue())";
                    Assert.assertEquals(expectValue, d.currentValue().toString());
                    int expectNotNull = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                    Assert.assertEquals(expectNotNull, d.getProperty(VariableProperty.NOT_NULL));
                    String expectLinked = d.iteration() == 0 ? LinkedVariables.DELAY_STRING : "change$1$1_0_1_1_0_0_0-E,change$1$1_0_1_0_1-E";
                    Assert.assertEquals(expectLinked, d.variableInfo().getLinkedVariables().toString());
                }
            }
            if ("change$1".equals(d.variableName())) {
                if ("1.0.1.0.1".equals(d.statementId())) {
                    String expectValue = d.iteration() == 0 ? "xx" : "instance type boolean";
                    Assert.assertEquals(expectValue, d.currentValue().toString());
                    String expectLinked = d.iteration() == 0 ? LinkedVariables.DELAY_STRING : "change";
                    Assert.assertEquals(expectLinked, d.variableInfo().getLinkedVariables().toString());
                }
            }
            if ("change$1$1_0_1_0_1-E".equals(d.variableName())) {
                if ("1.0.1.0.1".equals(d.statementId())) {
                    String expectValue = d.iteration() == 0 ? "xx" : "change$1||null==dest.get(e$1.getKey())";
                    Assert.assertEquals(expectValue, d.currentValue().toString());
                    String expectLinked = d.iteration() == 0 ? LinkedVariables.DELAY_STRING : "change";
                    Assert.assertEquals(expectLinked, d.variableInfo().getLinkedVariables().toString());
                    Assert.assertSame(VariableInLoop.VariableType.LOOP_COPY, d.variableInfoContainer().getVariableInLoop().variableType());
                    int expectNotNull = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                    Assert.assertEquals(expectNotNull, d.getProperty(VariableProperty.NOT_NULL));
                }
                if ("1.0.1.0.0".equals(d.statementId()) || "1.0.1.1.0".equals(d.statementId())) {
                    Assert.fail("The variable should not exist here");
                }
                if ("1.0.1".equals(d.statementId())) {
                    Assert.assertSame(VariableInLoop.VariableType.LOOP_COPY, d.variableInfoContainer().getVariableInLoop().variableType());
                    String expectValue = d.iteration() == 0 ? "xx" : "change$1||null==dest.get(e$1.getKey())";
                    Assert.assertEquals(expectValue, d.currentValue().toString());
                    String expectLinked = d.iteration() == 0 ? LinkedVariables.DELAY_STRING : "change";
                    Assert.assertEquals(expectLinked, d.variableInfo().getLinkedVariables().toString());
                }
            }
        }
    };

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if ("list".equals(d.methodInfo().name)) {
            if ("0".equals(d.statementId()) || "1".equals(d.statementId())) {
                Assert.assertEquals("null!=a", d.state().toString());
            }
            if ("2.0.0".equals(d.statementId())) {
                Assert.assertEquals("null==map.get(a)", d.condition().toString());
                Assert.assertEquals("null==map.get(a)&&null!=a", d.absoluteState().toString());
            }
            if ("3".equals(d.statementId())) {
                Assert.assertEquals("null!=map.get(a)&&null!=a", d.state().toString());
            }
        }
        if ("addAll".equals(d.methodInfo().name)) {
            if ("0".equals(d.statementId()) || "1".equals(d.statementId())) {
                Assert.assertSame(FlowData.Execution.ALWAYS, d.statementAnalysis().flowData.getGuaranteedToBeReachedInMethod());
            }
            if ("1.0.0".equals(d.statementId()) || "1.0.1".equals(d.statementId())) {
                FlowData.Execution expect = d.iteration() == 0 ? DELAYED_EXECUTION : CONDITIONALLY;

                Assert.assertSame(expect, d.statementAnalysis().flowData.getGuaranteedToBeReachedInMethod());
            }
            if ("1.0.1.1.0".equals(d.statementId())) {
                String expectCondition = d.iteration() == 0 ? "xx" : "null!=dest.get(e$1.getKey())";
                Assert.assertEquals(expectCondition, d.condition().toString());
            }
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        String name = d.methodInfo().name;

        if ("list".equals(name)) {
            VariableInfo returnValue1 = d.getReturnAsVariable();

            // FIXME null==map.get(a)  heeft geen null!=a, terwijl de state er wel heeft... inconsistentie
            Assert.assertEquals("null!=map.get(a)&&null!=a?map.get(a):null==map.get(a)?List.of():<return value>", d.getReturnAsVariable().getValue().toString());

            int retValNotNull = returnValue1.getProperty(VariableProperty.NOT_NULL);
            int propertyNotNull = d.methodAnalysis().getProperty(VariableProperty.NOT_NULL);
            // FIXME
            Assert.assertTrue(retValNotNull >= 0);
        }
        if ("copy".equals(name)) {
            VariableInfo returnValue = d.getReturnAsVariable();
            int expectImmutable = d.iteration() == 0 ? Level.DELAY : MultiLevel.MUTABLE;
            Assert.assertEquals(expectImmutable, returnValue.getProperty(VariableProperty.IMMUTABLE));
        }
        if ("add".equals(name) && d.methodInfo().methodInspection.get().getParameters().size() == 3) {
            ParameterInfo parameterInfo = d.methodInfo().methodInspection.get().getParameters().get(2);
            if ("bs".equals(parameterInfo.name)) {
                int modified = d.parameterAnalyses().get(2).getProperty(VariableProperty.MODIFIED);
                Assert.assertEquals(Level.FALSE, modified);
            }
        }
    };

    @Test
    public void test() throws IOException {
        testUtilClass(List.of("SMapList"), 0, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

}
