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

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.expression.util.EvaluateInlineConditional;
import org.e2immu.analyser.model.statement.ForEachStatement;
import org.e2immu.analyser.model.statement.WhileStatement;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Test_01_Loops extends CommonTestRunner {

    public Test_01_Loops() {
        super(true);
    }

    @Test
    public void test0() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if (!"method".equals(d.methodInfo().name)) return;
            if ("2".equals(d.statementId())) {
                Assert.assertEquals("true", d.evaluationResult().value().debugOutput());
            }
            if ("2.0.2".equals(d.statementId())) {
                String expect = d.iteration() == 0 ? EmptyExpression.NO_VALUE.toString() : "1+i$2>=n";
                Assert.assertEquals(expect, d.evaluationResult().value().debugOutput());
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if (!"method".equals(d.methodInfo().name)) return;
            if ("res1".equals(d.variableName())) {
                Assert.assertTrue(d.variable() instanceof LocalVariableReference);
                boolean expect = d.statementId().startsWith("2");
                boolean inLoop = d.variableInfoContainer().isLocalVariableInLoopDefinedOutside();
                Assert.assertEquals("In " + d.statementId(), expect, inLoop);

                if ("2.0.0".equals(d.statementId())) {
                    Assert.assertEquals("\"abc\"", d.currentValue().toString());
                }
            }
            if ("org.e2immu.analyser.testexample.Loops_0.this".equals(d.variableName())) {
                if ("2.0.0".equals(d.statementId())) {
                    Assert.assertTrue(d.variableInfo().getLinkedVariables().isEmpty());
                }
            }
            if ("i".equals(d.variableName())) {
                if (d.variable() instanceof LocalVariableReference) {
                    boolean expect = d.statementId().startsWith("2");
                    boolean inLoop = d.variableInfoContainer().isLocalVariableInLoopDefinedOutside();
                    Assert.assertEquals("In " + d.statementId(), expect, inLoop);
                } else Assert.fail();
                if ("1".equals(d.statementId())) {
                    Assert.assertEquals("0", d.currentValue().toString());
                }
                if ("2".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? EmptyExpression.NO_VALUE.toString() : "1+instance type int";
                    Assert.assertEquals(expect, d.currentValue().debugOutput());
                }
                if ("2.0.0".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? EmptyExpression.NO_VALUE.toString() : "i$2";
                    Assert.assertEquals(expect, d.currentValue().debugOutput());
                }
                if ("2.0.1".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? EmptyExpression.NO_VALUE.toString() : "1+i$2";
                    Assert.assertEquals(expect, d.currentValue().debugOutput());
                }
                if ("3".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? EmptyExpression.NO_VALUE.toString() : "1+instance type int";
                    Assert.assertEquals(expect, d.currentValue().toString());
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if (!"method".equals(d.methodInfo().name)) return;
            if ("2".equals(d.statementId())) {
                if (d.statementAnalysis().statement instanceof WhileStatement whileStatement) {
                    FlowData.Execution exec = whileStatement.structure.statementExecution()
                            .apply(new BooleanConstant(d.statementAnalysis().primitives, true),
                                    d.evaluationContext());
                    Assert.assertSame(FlowData.Execution.ALWAYS, exec);
                } else Assert.fail();
                String expectState = d.iteration() == 0 ? EmptyExpression.NO_VALUE.toString() : "1+instance type int>=n";
                Assert.assertEquals(expectState, d.state().toString());
            }
            if ("2.0.0".equals(d.statementId())) {
                if (d.iteration() == 0) {
                    VariableInfoContainer vic = d.statementAnalysis().variables.get("i");
                    //Assert.assertEquals(1, vic.getCurrentLevel());
                    Assert.assertSame(EmptyExpression.NO_VALUE, vic.current().getValue());
                }
            }
            // shows that the BREAK statement, always executed in its own block, is dependent on a valid condition
            if ("2.0.2.0.0".equals(d.statementId())) {
                String expect = d.iteration() == 0 ? EmptyExpression.NO_VALUE.toString() : "1+i$2>=n";
                Assert.assertEquals(expect, d.condition().toString());
            }
        };
        testClass("Loops_0", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build());
    }

    @Test
    public void test1() throws IOException {
        // weirdly written but there is no dedicated logic in place
        final String END_RESULT = "-1-(instance type int)+n>=1?\"abc\":instance type String/**/";
        final String END_RESULT_NO_OPERATIONS = "instance type int>=0?\"abc\":instance type String/**/";

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("2.0.1".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? EmptyExpression.NO_VALUE.toString() : "1+i$2>=n";
                    Assert.assertEquals(expect, d.evaluationResult().value().debugOutput());
                }
                if ("3".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? EmptyExpression.NO_VALUE.toString() : END_RESULT;
                    Assert.assertEquals(expect, d.evaluationResult().value().debugOutput());
                }
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if (!"method".equals(d.methodInfo().name)) return;
            if ("res2".equals(d.variableName())) {
                if ("2.0.0".equals(d.statementId())) {
                    Assert.assertEquals("2", d.variableInfoContainer().getVariableInLoop()
                            .statementId(VariableInLoop.VariableType.IN_LOOP_DEFINED_OUTSIDE));
                    String expectValue = d.iteration() == 0 ? EmptyExpression.NO_VALUE.toString() : "res2$2";
                    Assert.assertEquals(expectValue, d.variableInfo().getValue().toString());
                }
                if ("2.0.1.0.0".equals(d.statementId())) {
                    Assert.assertEquals("2", d.variableInfoContainer().getVariableInLoop()
                            .statementId(VariableInLoop.VariableType.IN_LOOP_DEFINED_OUTSIDE));
                    String expectValue = d.iteration() == 0 ? EmptyExpression.NO_VALUE.toString() : "res2$2";
                    Assert.assertEquals(expectValue, d.variableInfo().getValue().toString());
                }
                if ("2.0.1".equals(d.statementId())) {
                    Assert.assertEquals("2", d.variableInfoContainer().getVariableInLoop()
                            .statementId(VariableInLoop.VariableType.IN_LOOP_DEFINED_OUTSIDE));

                    String expectValue = d.iteration() == 0 ? EmptyExpression.NO_VALUE.toString() : "res2$2";
                    Assert.assertEquals(expectValue, d.variableInfo().getValue().toString());
                }
                if ("2.0.2".equals(d.statementId())) {
                    String expectValue = d.iteration() == 0 ? EmptyExpression.NO_VALUE.toString() : "-1-i$2+n>=1?\"abc\":res2$2";
                    Assert.assertEquals(expectValue, d.variableInfo().getValue().toString());
                }
                if ("2".equals(d.statementId())) {
                    String expectValue = d.iteration() == 0 ? EmptyExpression.NO_VALUE.toString() : END_RESULT;
                    Assert.assertEquals(expectValue, d.variableInfo().getValue().toString());

                    // first, understanding how this works...
                    Primitives primitives = d.evaluationContext().getCurrentStatement().statementAnalysis.primitives;
                    NewObject string1 = NewObject.forTesting(primitives, primitives.stringParameterizedType);
                    Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, string1.getProperty(d.evaluationContext(), VariableProperty.NOT_NULL));
                    Map<VariableProperty, Integer> map = Map.of(VariableProperty.NOT_NULL, MultiLevel.NULLABLE);
                    Expression string1Wrapped = PropertyWrapper.propertyWrapperForceProperties(string1, map);
                    Assert.assertEquals(MultiLevel.NULLABLE, string1Wrapped.getProperty(d.evaluationContext(), VariableProperty.NOT_NULL));
                    Expression inline = EvaluateInlineConditional.conditionalValueConditionResolved(d.evaluationContext(),
                            GreaterThanZero.greater(d.evaluationContext(), NewObject.forTesting(primitives, primitives.intParameterizedType),
                                    new IntConstant(primitives, 0), true),
                            new StringConstant(primitives, "abc"),
                            string1Wrapped, ObjectFlow.NO_FLOW).value();
                    Assert.assertEquals(END_RESULT_NO_OPERATIONS, inline.toString());
                    Assert.assertEquals(MultiLevel.NULLABLE, inline.getProperty(d.evaluationContext(), VariableProperty.NOT_NULL));

                    // so...
                    if (d.iteration() > 0) {
                        Assert.assertEquals(MultiLevel.NULLABLE, d.currentValue().getProperty(d.evaluationContext(), VariableProperty.NOT_NULL));

                        Assert.assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.NOT_NULL));
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                FlowData.Execution execution = d.statementAnalysis().flowData.getGuaranteedToBeReachedInCurrentBlock();
                if ("2.0.0".equals(d.statementId())) {
                    Assert.assertEquals("true", d.condition().debugOutput());
                    Assert.assertEquals("true", d.absoluteState().debugOutput());
                    Assert.assertSame(FlowData.Execution.ALWAYS, execution);
                }
                if ("2.0.1".equals(d.statementId())) {
                    Assert.assertSame(FlowData.Execution.ALWAYS, execution);

                    // both are NO_VALUE in the first iteration, because we're showing the stateData
                    // and not the local condition manager
                    String expectCondition = d.iteration() == 0 ? EmptyExpression.NO_VALUE.toString() : "true";
                    Assert.assertEquals(expectCondition, d.condition().debugOutput());
                    String expectState = d.iteration() == 0 ? EmptyExpression.NO_VALUE.toString() : "-1-i$2+n>=1";
                    Assert.assertEquals(expectState, d.absoluteState().debugOutput());
                }
                if ("2.0.2".equals(d.statementId())) {
                    FlowData.Execution expect = d.iteration() == 0 ? FlowData.Execution.DELAYED_EXECUTION : FlowData.Execution.CONDITIONALLY;
                    Assert.assertSame(expect, execution);

                    String expectCondition = d.iteration() == 0 ? EmptyExpression.NO_VALUE.toString() : "true";
                    Assert.assertEquals(expectCondition, d.condition().debugOutput());
                    String expectState = d.iteration() == 0 ? EmptyExpression.NO_VALUE.toString() : "-1-i$2+n>=1";
                    Assert.assertEquals(expectState, d.absoluteState().debugOutput());
                }
            }
        };

        testClass("Loops_1", 0, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

    @Test
    public void test2() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if (!"method".equals(d.methodInfo().name)) return;
            if ("1".equals(d.statementId())) {
                Assert.assertEquals("{\"a\",\"b\",\"c\"}", d.evaluationResult().value().toString());
                Assert.assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL, d.evaluationResult().value()
                        .getProperty(d.evaluationResult().evaluationContext(), VariableProperty.NOT_NULL));
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if (!"method".equals(d.methodInfo().name)) return;
            if ("s".equals(d.variableName())) {
                if ("1.0.0".equals(d.statementId())) {
                    Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.NOT_NULL));
                    Assert.assertEquals("java.lang.String", d.variableInfo().variable()
                            .parameterizedType().typeInfo.fullyQualifiedName);
                }
            }
            if ("s$1".equals(d.variableName())) { // only exists in iteration 1+
                if ("1.0.0".equals(d.statementId())) {
                    Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.NOT_NULL));
                }
            }
            if ("res".equals(d.variableName())) {
                if ("1.0.0".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? EmptyExpression.NO_VALUE.toString() : "s$1";
                    Assert.assertEquals(expect, d.currentValue().toString());
                    int expectNn = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                    Assert.assertEquals(expectNn, d.getProperty(VariableProperty.NOT_NULL));
                }
                if ("2".equals(d.statementId())) {
                    int expectNn = d.iteration() == 0 ? MultiLevel.NULLABLE : MultiLevel.EFFECTIVELY_NOT_NULL;
                    Assert.assertEquals(expectNn, d.getProperty(VariableProperty.NOT_NULL));
                    String expect = d.iteration() == 0 ? EmptyExpression.NO_VALUE.toString() : "instance type String/*@NotNull*/";
                    Assert.assertEquals(expect, d.currentValue().toString());
                }
            }
            if ("org.e2immu.analyser.testexample.Loops_2.method()".equals(d.variableName())) {
                if ("2".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? EmptyExpression.NO_VALUE.toString() : "instance type String/*@NotNull*/";
                    Assert.assertEquals(expect, d.currentValue().toString());
                }
            }
        };
        testClass("Loops_2", 0, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    // explicitly empty loop
    @Test
    public void test3() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name) && "1".equals(d.statementId()) && "s".equals(d.variableName())) {
                Assert.assertEquals("1", d.variableInfoContainer().getStatementIndexOfThisLoopVariable());
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("1.0.0".equals(d.statementId())) {
                    Assert.fail("statement should be unreachable");
                }
                if ("1".equals(d.statementId())) {
                    if (d.statementAnalysis().statement instanceof ForEachStatement forEachStatement) {
                        FlowData.Execution exec = forEachStatement.structure.statementExecution()
                                .apply(new ArrayInitializer(d.statementAnalysis().primitives, ObjectFlow.NO_FLOW,
                                        List.of()), d.evaluationContext());
                        Assert.assertSame(FlowData.Execution.NEVER, exec);

                        StatementAnalysis firstInBlock = d.statementAnalysis().navigationData.blocks.get().get(0).orElseThrow();
                        Assert.assertEquals("1.0.0", firstInBlock.index);
                        Assert.assertTrue(firstInBlock.flowData.isUnreachable());

                        Assert.assertNotNull(d.haveError(Message.EMPTY_LOOP));
                    } else Assert.fail();
                }
                if ("2".equals(d.statementId())) {
                    Assert.assertFalse(d.statementAnalysis().variables.isSet("s"));
                }
            }
        };
        // empty loop
        testClass("Loops_3", 1, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

    @Test
    public void test4() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name) && "0".equals(d.statementId())) {
                String expect = d.iteration() == 0 ? EmptyExpression.NO_VALUE.toString() : "instance type int>=10";
                Assert.assertEquals(expect, d.state().toString());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("i".equals(d.variableName())) {
                    Assert.assertEquals("0", d.variableInfoContainer().getStatementIndexOfThisLoopVariable());

                    if (d.statementId().startsWith("0")) {
                        String expect = d.iteration() == 0 ? EmptyExpression.NO_VALUE.toString() : "i$0";
                        Assert.assertEquals(expect, d.currentValue().toString());
                    }
                }
                if ("org.e2immu.analyser.testexample.Loops_4.method()".equals(d.variableName())) {
                    if ("0.0.0.0.0".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "<return value>" : "4";
                        Assert.assertEquals(expect, d.currentValue().toString());
                    }
                    if ("0.0.0".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "<return value>" : "1==i$0?4:<return value>";
                        Assert.assertEquals(expect, d.currentValue().toString());
                    }
                    if ("0".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "<return value>" : "instance type int<=9?1==instance type int?4:<return value>:<return value>";
                        Assert.assertEquals(expect, d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "<return value>" : "instance type int>=10?0:instance type int<=9?1==instance type int?4:<return value>:<return value>";
                        Assert.assertEquals(expect, d.currentValue().toString());
                    }
                }
            }
        };
        testClass("Loops_4", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test5() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("method".equals(d.methodInfo().name) && "1.0.0".equals(d.statementId())) {
                String expect = d.iteration() == 0 ? EmptyExpression.NO_VALUE.toString() : "1==i$1";
                Assert.assertEquals(expect, d.evaluationResult().value().toString());
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name) && "1.0.0".equals(d.statementId()) && "i".equals(d.variableName())) {
                Assert.assertEquals("1", d.variableInfoContainer().getVariableInLoop()
                        .statementId(VariableInLoop.VariableType.IN_LOOP_DEFINED_OUTSIDE));
            }
        };
        testClass("Loops_5", 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build());
    }

    @Test
    public void test6() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                Assert.assertNotNull(d.haveError(Message.LOOP_WITHOUT_MODIFICATION));
            }
        };
        testClass("Loops_6", 3, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test7() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if (!"method".equals(d.methodInfo().name)) return;
            if ("1".equals(d.statementId())) {
                String expect = d.iteration() == 0 ? EmptyExpression.NO_VALUE.toString() : "n>i$1";
                Assert.assertEquals(expect, d.evaluationResult().value().toString());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if (!"method".equals(d.methodInfo().name)) return;
            if ("k".equals(d.variableName()) && "1.0.0".equals(d.statementId())) {
                String expect = d.iteration() == 0 ? EmptyExpression.NO_VALUE.toString() : "i$1";
                Assert.assertEquals(expect, d.currentValue().toString());
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if (!"method".equals(d.methodInfo().name)) return;
            if ("0".equals(d.statementId())) {
                Assert.assertSame(FlowData.Execution.ALWAYS, d.statementAnalysis().flowData.getGuaranteedToBeReachedInMethod());
                Assert.assertSame(FlowData.Execution.ALWAYS, d.statementAnalysis().flowData.getGuaranteedToBeReachedInCurrentBlock());
            }
            if ("1".equals(d.statementId())) {
                Assert.assertSame(FlowData.Execution.ALWAYS, d.statementAnalysis().flowData.getGuaranteedToBeReachedInMethod());
                Assert.assertSame(FlowData.Execution.ALWAYS, d.statementAnalysis().flowData.getGuaranteedToBeReachedInCurrentBlock());

                Assert.assertTrue(d.statementAnalysis().localVariablesAssignedInThisLoop.isFrozen());
                Assert.assertEquals("i", d.statementAnalysis().localVariablesAssignedInThisLoop.stream().collect(Collectors.joining()));
            }
            if ("1.0.0".equals(d.statementId())) {
                FlowData.Execution expectExec = d.iteration() == 0 ? FlowData.Execution.DELAYED_EXECUTION : FlowData.Execution.CONDITIONALLY;
                Assert.assertSame(expectExec, d.statementAnalysis().flowData.getGuaranteedToBeReachedInMethod());
                Assert.assertSame(FlowData.Execution.ALWAYS, d.statementAnalysis().flowData.getGuaranteedToBeReachedInCurrentBlock());

                String expect = d.iteration() == 0 ? EmptyExpression.NO_VALUE.toString() : "n>i$1";
                Assert.assertEquals(expect, d.state().toString());
            }
            if ("1.0.1".equals(d.statementId())) {
                String expect = d.iteration() == 0 ? EmptyExpression.NO_VALUE.toString() : "n>i$1";
                Assert.assertEquals(expect, d.state().toString());
                String expectInterrupt = "{}";
                Assert.assertEquals(expectInterrupt, d.statementAnalysis().flowData.getInterruptsFlow().toString());
            }
            if ("1.0.2".equals(d.statementId())) {
                String expect = d.iteration() == 0 ? EmptyExpression.NO_VALUE.toString() : "false";
                Assert.assertEquals(expect, d.state().toString());
                if (d.iteration() == 0) {
                    Assert.assertFalse(d.statementAnalysis().flowData.interruptsFlowIsSet());
                } else {
                    Assert.assertEquals("{break=CONDITIONALLY}", d.statementAnalysis().flowData.getInterruptsFlow().toString());
                }
            }
            if ("1.0.2.0.0".equals(d.statementId())) {
                String expect = "{break=ALWAYS}";
                Assert.assertEquals(expect, d.statementAnalysis().flowData.getInterruptsFlow().toString());
                if (d.iteration() == 0) {
                    Assert.assertFalse(d.statementAnalysis().flowData.blockExecution.isSet());
                } else {
                    Assert.assertSame(FlowData.Execution.CONDITIONALLY, d.statementAnalysis().flowData.blockExecution.get());
                }
            }
            if ("1.0.3".equals(d.statementId())) {
                FlowData.Execution expectExec = d.iteration() == 0 ? FlowData.Execution.DELAYED_EXECUTION : FlowData.Execution.NEVER;
                Assert.assertSame(expectExec, d.statementAnalysis().flowData.getGuaranteedToBeReachedInMethod());
            }
        };

        testClass("Loops_7", 2, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build());
    }

}
