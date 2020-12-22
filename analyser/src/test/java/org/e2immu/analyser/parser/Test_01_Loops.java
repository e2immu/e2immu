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

import org.e2immu.analyser.analyser.FlowData;
import org.e2immu.analyser.analyser.StatementAnalyser;
import org.e2immu.analyser.analyser.StatementAnalysis;
import org.e2immu.analyser.analyser.VariableInfoContainer;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.expression.ArrayInitializer;
import org.e2immu.analyser.model.expression.BooleanConstant;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.statement.ForEachStatement;
import org.e2immu.analyser.model.statement.WhileStatement;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

public class Test_01_Loops extends CommonTestRunner {

    public Test_01_Loops() {
        super(true);
    }

    @Test
    public void test0() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("method".equals(d.methodInfo().name) && "2.0.2".equals(d.statementId())) {
                Assert.assertEquals(StatementAnalyser.STEP_3, d.step());
                String expect = d.iteration() == 0 ? EmptyExpression.NO_VALUE.toString() : "1+i$2.0.1:3>=n";
                Assert.assertEquals(expect, d.evaluationResult().value().debugOutput());
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if (!"method".equals(d.methodInfo().name)) return;
            if ("res1".equals(d.variableName())) {
                if (d.variable() instanceof LocalVariableReference) {
                    boolean expect = d.statementId().startsWith("2.");
                    boolean inLoop = d.variableInfoContainer().isLocalVariableInLoopDefinedOutside();
                    Assert.assertEquals("In " + d.statementId(), expect, inLoop);
                } else Assert.fail();
            }
            if ("i".equals(d.variableName())) {
                if (d.variable() instanceof LocalVariableReference) {
                    boolean expect = d.statementId().startsWith("2.");
                    boolean inLoop = d.variableInfoContainer().isLocalVariableInLoopDefinedOutside();
                    Assert.assertEquals("In " + d.statementId(), expect, inLoop);
                } else Assert.fail();
                if ("1".equals(d.statementId())) {
                    Assert.assertEquals("0", d.currentValue().toString());
                }
                if ("2.0.1".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? EmptyExpression.NO_VALUE.toString() : "1+i$2.0.1:3";
                    Assert.assertEquals(expect, d.currentValue().debugOutput());
                }
                if ("3".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? EmptyExpression.NO_VALUE.toString() : "1+i$2.0.1:3";
                    Assert.assertEquals(expect, d.currentValue().toString());
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if (!"method".equals(d.methodInfo().name)) return;
            if ("2".equals(d.statementId())) {
                if (d.statementAnalysis().statement instanceof WhileStatement whileStatement) {
                    FlowData.Execution exec = whileStatement.structure.statementExecution
                            .apply(new BooleanConstant(d.statementAnalysis().primitives, true),
                                    d.evaluationContext());
                    Assert.assertSame(FlowData.Execution.ALWAYS, exec);
                } else Assert.fail();
            }
            if ("2.0.0".equals(d.statementId())) {
                if (d.iteration() == 0) {
                    VariableInfoContainer vic = d.statementAnalysis().variables.get("i");
                    Assert.assertEquals(1, vic.getCurrentLevel());
                    Assert.assertSame(EmptyExpression.NO_VALUE, vic.current().getValue());
                }
            }
            // shows that the BREAK statement, always executed in its own block, is dependent on a valid condition
            if ("2.0.2.0.0".equals(d.statementId())) {
                String expect = d.iteration() == 0 ? EmptyExpression.NO_VALUE.toString() : "1+i$2.0.1:3>=n";
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

        testClass("Loops_1", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test2() throws IOException {

        testClass("Loops_2", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    // explicitly empty loop
    @Test
    public void test3() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name) && "1".equals(d.statementId())) {
                if (d.statementAnalysis().statement instanceof ForEachStatement forEachStatement) {
                    FlowData.Execution exec = forEachStatement.structure.statementExecution
                            .apply(new ArrayInitializer(d.statementAnalysis().primitives, ObjectFlow.NO_FLOW,
                                    List.of()), d.evaluationContext());
                    Assert.assertSame(FlowData.Execution.NEVER, exec);

                    StatementAnalysis firstInBlock = d.statementAnalysis().navigationData.blocks.get().get(0).orElseThrow();
                    Assert.assertTrue(firstInBlock.flowData.isUnreachable());

                    Assert.assertNotNull(d.haveError(Message.EMPTY_LOOP));
                } else Assert.fail();
            }
        };
        testClass("Loops_3", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

    @Test
    public void test4() throws IOException {

        testClass("Loops_4", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test5() throws IOException {

        testClass("Loops_5", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test6() throws IOException {

        testClass("Loops_6", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test7() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                Assert.assertNotNull(d.haveError(Message.LOOP_WITHOUT_MODIFICATION));
            }
        };
        testClass("Loops_7", 3, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

}
