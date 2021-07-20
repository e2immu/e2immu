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

package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.expression.util.EvaluateInlineConditional;
import org.e2immu.analyser.model.statement.ForEachStatement;
import org.e2immu.analyser.model.statement.WhileStatement;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.VariableNature;
import org.e2immu.analyser.visitor.EvaluationResultVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.e2immu.analyser.analyser.FlowData.Execution.*;
import static org.junit.jupiter.api.Assertions.*;

public class Test_01_Loops extends CommonTestRunner {

    public static final String DELAYED_BY_STATE = "<s:String>";

    public Test_01_Loops() {
        super(true);
    }

    @Test
    public void test0() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if (!"method".equals(d.methodInfo().name)) return;
            if ("2".equals(d.statementId())) {
                assertEquals("true", d.evaluationResult().value().debugOutput());
            }
            if ("2.0.1".equals(d.statementId())) {
                // NOTE: is i$2, and not i$2+1 because the operation is i++, not ++i
                String expect = d.iteration() == 0 ? "<v:i>" : "i$2";
                assertEquals(expect, d.evaluationResult().value().toString());
            }
            if ("2.0.2".equals(d.statementId())) {
                String expect = d.iteration() == 0 ? "<v:i>>=n" : "1+i$2>=n";
                assertEquals(expect, d.evaluationResult().value().toString());
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if (!"method".equals(d.methodInfo().name)) return;
            if ("res1".equals(d.variableName())) {
                assertTrue(d.variable() instanceof LocalVariableReference);
                boolean expect = d.statementId().startsWith("2");
                boolean inLoop = d.variableInfoContainer().variableNature().isLocalVariableInLoopDefinedOutside();
                assertEquals(expect, inLoop);

                if ("2.0.0".equals(d.statementId())) {
                    assertEquals("\"abc\"", d.currentValue().toString());
                }
            }
            if ("org.e2immu.analyser.testexample.Loops_0.this".equals(d.variableName())) {
                if ("2.0.0".equals(d.statementId())) {
                    assertTrue(d.variableInfo().getLinkedVariables().isEmpty());
                }
            }
            if ("i$2".equals(d.variableName())) {
                if ("2.0.0".equals(d.statementId())) {
                    assertTrue(d.iteration() > 0);
                    assertEquals("instance type int", d.currentValue().debugOutput());
                }
            }
            if ("i".equals(d.variableName())) {
                if (d.variable() instanceof LocalVariableReference) {
                    boolean expect = d.statementId().startsWith("2");
                    boolean inLoop = d.variableInfoContainer().variableNature().isLocalVariableInLoopDefinedOutside();
                    assertEquals(expect, inLoop);
                } else fail();
                if ("1".equals(d.statementId())) {
                    assertEquals("0", d.currentValue().toString());
                }
                if ("2".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? "1+<v:i>" : "1+instance type int";
                    assertEquals(expect, d.currentValue().toString());
                }
                if ("2.0.1".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? "1+<v:i>" : "1+i$2";
                    assertEquals(expect, d.currentValue().toString());
                }
                if ("3".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? "1+<v:i>" : "1+instance type int";
                    assertEquals(expect, d.currentValue().toString());
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
                    assertSame(ALWAYS, exec);
                } else fail();
                String expectState = d.iteration() == 0 ? "<v:i>>=n" : "1+instance type int>=n";
                assertEquals(expectState, d.state().toString());

                assertEquals(ALWAYS, d.statementAnalysis().flowData.getGuaranteedToBeReachedInMethod());
                assertEquals(ALWAYS, d.statementAnalysis().flowData.getGuaranteedToBeReachedInCurrentBlock());
            }
            if ("2.0.0".equals(d.statementId())) {
                assertEquals("true", d.condition().toString());
                assertEquals("true", d.state().toString());
                assertTrue(d.localConditionManager().precondition().isEmpty());
                if (d.iteration() == 0) {
                    VariableInfoContainer vic = d.statementAnalysis().variables.get("i");
                    assertEquals("0", vic.current().getValue().toString());
                }
                assertEquals(ALWAYS, d.statementAnalysis().flowData.getGuaranteedToBeReachedInMethod());
                assertEquals(ALWAYS, d.statementAnalysis().flowData.getGuaranteedToBeReachedInCurrentBlock());
            }
            if ("2.0.1".equals(d.statementId())) {
                assertTrue(d.localConditionManager().precondition().isEmpty());
            }
            if ("2.0.2".equals(d.statementId())) {
                assertEquals("true", d.condition().toString());
                String expectState = d.iteration() == 0 ? "n-<v:i>>=1" : "-1-i$2+n>=1";
                assertEquals(expectState, d.state().toString());
                assertEquals(d.iteration() == 0, d.statementAnalysis()
                        .stateData.conditionManagerForNextStatement.isVariable());

                assertEquals(ALWAYS, d.statementAnalysis().flowData.getGuaranteedToBeReachedInMethod());
                assertEquals(ALWAYS, d.statementAnalysis().flowData.getGuaranteedToBeReachedInCurrentBlock());
            }
            // shows that the BREAK statement, always executed in its own block, is dependent on a valid condition
            if ("2.0.2.0.0".equals(d.statementId())) {
                String expect = d.iteration() == 0 ? "<v:i>>=n" : "1+i$2>=n";
                assertEquals(expect, d.condition().toString());
                FlowData.Execution expectExec = d.iteration() == 0 ? DELAYED_EXECUTION : CONDITIONALLY;
                assertEquals(expectExec, d.statementAnalysis().flowData.getGuaranteedToBeReachedInMethod());
                assertEquals(ALWAYS, d.statementAnalysis().flowData.getGuaranteedToBeReachedInCurrentBlock());
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
        final String END_RESULT = "-1-(instance type int)+n>=1?\"abc\":nullable instance type String";
        final String END_RESULT_NO_OPERATIONS = "instance type int>=0?\"abc\":instance type String";

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("2.0.0".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? "1+<v:i>" : "1+i$2";
                    assertEquals(expect, d.evaluationResult().value().toString());
                }
                if ("2.0.1".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? "<v:i>>=n" : "1+i$2>=n";
                    assertEquals(expect, d.evaluationResult().value().toString());
                }
                if ("3".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? DELAYED_BY_STATE : END_RESULT;
                    assertEquals(expect, d.evaluationResult().value().toString());
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("res2$2".equals(d.variableName())) {
                    assertTrue(d.iteration() > 0);
                    if ("2.0.0".equals(d.statementId())) {
                        assertEquals("nullable instance type String", d.currentValue().toString());
                        assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                    }
                    if ("2.0.2".equals(d.statementId())) {
                        assertEquals("-1-i$2+n>=1?\"abc\":nullable instance type String", d.currentValue().toString());
                        assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                    }
                }
                if ("res2".equals(d.variableName())) {
                    if ("2.0.1.0.0".equals(d.statementId())) {
                        if (d.variableInfoContainer().variableNature() instanceof VariableNature.VariableDefinedOutsideLoop v) {
                            assertEquals("2", v.statementIndex());
                        } else fail();
                        assertTrue(d.variableInfoContainer().variableNature() instanceof VariableNature.VariableDefinedOutsideLoop);
                    }
                    if ("2.0.0".equals(d.statementId()) || "2.0.1".equals(d.statementId())) {
                        if (d.variableInfoContainer().variableNature() instanceof VariableNature.VariableDefinedOutsideLoop v) {
                            assertEquals("2", v.statementIndex());
                        } else fail();
                    }
                    if ("2.0.2".equals(d.statementId())) {
                        // statement says: res="abc", but the value takes the state into account
                        String expectValue = d.iteration() == 0 ? DELAYED_BY_STATE : "-1-i$2+n>=1?\"abc\":res2$2";
                        assertEquals(expectValue, d.variableInfo().getValue().toString());
                        // clearly, NNE has to follow the value rather than the actual assignment
                        int expectNNE = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
                        assertEquals(expectNNE, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                    }
                    if ("2".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? DELAYED_BY_STATE : END_RESULT;
                        assertEquals(expectValue, d.variableInfo().getValue().toString());

                        // first, understanding how this works...
                        Primitives primitives = d.evaluationContext().getCurrentStatement().statementAnalysis.primitives;
                        NewObject string1 = NewObject.forTesting(primitives, primitives.stringParameterizedType);
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, string1.getProperty(d.evaluationContext(), VariableProperty.NOT_NULL_EXPRESSION, true));
                        Map<VariableProperty, Integer> map = Map.of(VariableProperty.NOT_NULL_EXPRESSION, MultiLevel.NULLABLE);
                        Expression string1Wrapped = PropertyWrapper.propertyWrapper(string1, map);
                        assertEquals(MultiLevel.NULLABLE, string1Wrapped.getProperty(d.evaluationContext(), VariableProperty.NOT_NULL_EXPRESSION, true));
                        Expression inline = EvaluateInlineConditional.conditionalValueConditionResolved(d.evaluationContext(),
                                GreaterThanZero.greater(d.evaluationContext(), NewObject.forTesting(primitives, primitives.intParameterizedType),
                                        new IntConstant(primitives, 0), true),
                                new StringConstant(primitives, "abc"), string1Wrapped).value();
                        assertEquals(END_RESULT_NO_OPERATIONS, inline.toString());
                        assertEquals(MultiLevel.NULLABLE, inline.getProperty(d.evaluationContext(), VariableProperty.NOT_NULL_EXPRESSION, true));
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                FlowData.Execution execution = d.statementAnalysis().flowData.getGuaranteedToBeReachedInCurrentBlock();
                if ("2.0.0".equals(d.statementId())) {
                    assertEquals("true", d.condition().debugOutput());
                    assertEquals("true", d.absoluteState().debugOutput());
                    assertSame(ALWAYS, execution);
                }
                if ("2.0.1.0.0".equals(d.statementId())) {
                    String expectCondition = d.iteration() == 0 ? "<v:i>>=n" : "1+i$2>=n";
                    assertEquals(expectCondition, d.condition().toString());
                    assertEquals(d.iteration() == 0, d.localConditionManager().isDelayed());
                }
                if ("2.0.1".equals(d.statementId())) { // if (i>=n) break;
                    assertSame(ALWAYS, execution);

                    // both are NO_VALUE in the first iteration, because we're showing the stateData
                    // and not the local condition manager
                    assertEquals("true", d.condition().debugOutput());
                    String expectState = d.iteration() == 0 ? "n-<v:i>>=1" : "-1-i$2+n>=1";
                    assertEquals(expectState, d.absoluteState().toString());
                    assertEquals(d.iteration() == 0, d.conditionManagerForNextStatement().isDelayed());
                }
                if ("2.0.2".equals(d.statementId())) { // res2 = "abc"
                    assertEquals("true", d.condition().debugOutput());
                    assertEquals(d.iteration() == 0, d.localConditionManager().isDelayed());

                    String expectState = d.iteration() == 0 ? "n-<v:i>>=1" : "-1-i$2+n>=1";

                    assertEquals(expectState, d.localConditionManager().state().toString());
                    assertEquals(expectState, d.absoluteState().toString());

                    FlowData.Execution expect = d.iteration() == 0 ? DELAYED_EXECUTION : CONDITIONALLY;
                    assertSame(expect, execution);
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
                assertEquals("{\"a\",\"b\",\"c\"}", d.evaluationResult().value().toString());
                assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL, d.evaluationResult().value()
                        .getProperty(d.evaluationResult().evaluationContext(), VariableProperty.NOT_NULL_EXPRESSION, true));
            }
            if ("1.0.0".equals(d.statementId()) && d.iteration() > 0) {
                assertEquals("s$1", d.evaluationResult().value().toString());
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if (!"method".equals(d.methodInfo().name)) return;
            if ("s".equals(d.variableName())) {
                if ("1.0.0".equals(d.statementId())) {
                    assertEquals("java.lang.String", d.variableInfo().variable()
                            .parameterizedType().typeInfo.fullyQualifiedName);
                    if (d.iteration() == 0) {
                        assertEquals("<v:s>", d.currentValue().toString());
                        assertEquals(Level.DELAY, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                    } else {
                        // the ENN has been set on s$1, not on s
                        // FIXME the value property must be set when the value is set!
                        assertEquals("nullable instance type String", d.currentValue().toString());
                        int expectNne = d.iteration() == 1 ? Level.DELAY : MultiLevel.NULLABLE;
                        assertEquals(expectNne, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                    }
                    assertEquals("", d.variableInfo().getLinkedVariables().toString());
                }
            }
            // code puts CNN to 5 on evaluation of statement 1, so s$1 seems OK; why do we use CNN? and not NNE?
            // FIXME inconsistent currently s$1 is NNE NULLABLE, s is not (?)
            if ("s$1".equals(d.variableName())) {
                assertTrue(d.iteration() > 0);
                if ("1.0.0".equals(d.statementId())) {
                    assertEquals("nullable instance type String", d.currentValue().toString());
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                    assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                    assertEquals("s", d.variableInfo().getStaticallyAssignedVariables().toString());
                }
            }
            if ("res$1$1_0_0-E".equals(d.variableName())) {
                assertEquals("1.0.0", d.statementId());

                String expectValue = d.iteration() == 0 ? "<v:s>" : "s$1";
                assertEquals(expectValue, d.currentValue().toString());
                assertEquals("s", d.variableInfo().getStaticallyAssignedVariables().toString());
                assertEquals("", d.variableInfo().getLinkedVariables().toString());
                int expectNN = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                assertEquals(expectNN, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
            }
            if ("res".equals(d.variableName())) {
                if ("1.0.0".equals(d.statementId())) {
                    String expectValue = d.iteration() == 0 ? "<v:s>" : "s$1";
                    assertEquals(expectValue, d.currentValue().toString());
                    assertEquals("", d.variableInfo().getLinkedVariables().toString());
                }
                if ("1".equals(d.statementId())) {
                    String expectValue = d.iteration() == 0 ? "<v:s>" : "nullable instance type String";
                    assertEquals(expectValue, d.currentValue().toString());
                }
                if ("2".equals(d.statementId())) {
                    String expectValue = d.iteration() == 0 ? "<v:s>" : "nullable instance type String";
                    assertEquals(expectValue, d.currentValue().toString());
                    int expectNne = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
                    assertEquals(expectNne, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                }
            }
            if (d.variable() instanceof ReturnVariable) {
                if ("2".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? "<v:s>" : "res"; // indirection
                    assertEquals(expect, d.currentValue().toString());
                    int expectNne = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                    assertEquals(expectNne, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
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
            if ("method".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId()) && "s".equals(d.variableName())) {
                    if (d.variableInfoContainer().variableNature() instanceof VariableNature.LoopVariable loopVariable) {
                        assertEquals("1", loopVariable.statementIndex());
                    } else fail();
                }
                if ("res".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("\"a\"", d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        // make sure that res isn't messed with
                        VariableInfo initial = d.variableInfoContainer().getPreviousOrInitial();
                        assertEquals("\"a\"", initial.getValue().toString());

                        // once we have determined that the loop is empty, the merger should take the original value
                        String expectValue = "\"a\"";
                        assertEquals(expectValue, d.currentValue().toString());
                        String expectLinked = "";
                        assertEquals(expectLinked, d.variableInfo().getLinkedVariables().toString());
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("1.0.0".equals(d.statementId())) {
                    assertEquals(0, d.iteration(), "statement should be unreachable after iteration 0");
                }
                if ("1".equals(d.statementId())) {
                    if (d.statementAnalysis().statement instanceof ForEachStatement forEachStatement) {
                        FlowData.Execution exec = forEachStatement.structure.statementExecution()
                                .apply(new ArrayInitializer(d.evaluationContext().getAnalyserContext(),
                                        List.of(), d.statementAnalysis().primitives.stringParameterizedType), d.evaluationContext());
                        assertSame(FlowData.Execution.NEVER, exec);

                        StatementAnalysis firstInBlock = d.statementAnalysis().navigationData.blocks.get().get(0).orElseThrow();
                        assertEquals("1.0.0", firstInBlock.index);
                        if (d.iteration() > 0) {
                            assertTrue(firstInBlock.flowData.isUnreachable());
                            assertNotNull(d.haveError(Message.Label.EMPTY_LOOP));
                        }
                    } else fail();
                }
                if ("2".equals(d.statementId())) {
                    assertFalse(d.statementAnalysis().variables.isSet("s"));
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
            if ("method".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? "<v:i>>=10" : "instance type int>=10";
                    assertEquals(expect, d.state().toString());
                    assertNull(d.haveError(Message.Label.INLINE_CONDITION_EVALUATES_TO_CONSTANT));
                }
                if ("1".equals(d.statementId())) {
                    assertEquals("true", d.condition().toString());
                    String expectState = d.iteration() == 0 ? "<v:i>>=10" : "instance type int>=10";
                    assertEquals(expectState, d.state().toString());
                    assertNull(d.haveError(Message.Label.INLINE_CONDITION_EVALUATES_TO_CONSTANT));
                }
            }

        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("i".equals(d.variableName())) {
                    if (d.variableInfoContainer().variableNature() instanceof VariableNature.LoopVariable loopVariable) {
                        assertEquals("0", loopVariable.statementIndex());
                    } else fail();
                    if ("0.0.0.0.0".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "1+<v:i>" : "1+i$0";
                        assertEquals(expect, d.currentValue().toString());
                    }
                    if ("0.0.0".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "1+<v:i>" : "1+i$0";
                        assertEquals(expect, d.currentValue().toString());
                    }
                    if ("0".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "1+<v:i>" : "1+i$0";
                        assertEquals(expect, d.currentValue().toString());
                    }
                }
                if (d.variable() instanceof ReturnVariable) {
                    assertEquals("org.e2immu.analyser.testexample.Loops_4.method()", d.variableName());
                    if ("0.0.0.0.0".equals(d.statementId())) {
                        // delayed state
                        String expect = "4";
                        assertEquals(expect, d.currentValue().toString());
                    }
                    if ("0.0.0".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "1==<v:i>?4:<return value>" : "0==i$0?4:<return value>";
                        assertEquals(expect, d.currentValue().toString());
                    }
                    if ("0".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "1==<replace:int>?4:<return value>"
                                : "0==instance type int?4:<return value>";
                        assertEquals(expect, d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "<v:i>>=10?0:1==<replace:int>&&<v:i><=9?4:<return value>"
                                : "instance type int>=10?0:0==instance type int?4:<return value>";
                        assertEquals(expect, d.currentValue().toString());
                    }
                }
            }
        };

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    String expectValue = d.iteration() == 0 ? "<v:i><=9" : "i$0<=9";
                    assertEquals(expectValue, d.evaluationResult().value().toString());
                }
                if ("0.0.0".equals(d.statementId())) {
                    String expectValue = d.iteration() == 0 ? "1==<v:i>" : "0==i$0";
                    assertEquals(expectValue, d.evaluationResult().value().toString());
                }
            }
        };

        testClass("Loops_4", 0, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test5() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("1.0.0".equals(d.statementId())) {
                    // instead of 1==i$1, it is 0==i$1 because i's value is i$1+1
                    String expect = d.iteration() == 0 ? "1==<v:i>" : "0==i$1";
                    assertEquals(expect, d.evaluationResult().value().toString());
                }
                if ("2".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? "<v:i>>=9" : "instance type int>=9";
                    assertEquals(expect, d.evaluationResult().value().toString());
                }
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if (!"method".equals(d.methodInfo().name)) return;
            if ("i$1".equals(d.variableName())) {
                assertTrue(d.iteration() > 0);
                if("1.0.0".equals(d.statementId())) {
                    assertEquals("1+i$1", d.currentValue().toString());
                }
            }
            if ("i".equals(d.variableName())) {
                if ("1.0.0".equals(d.statementId())) {
                    if (d.variableInfoContainer().variableNature() instanceof VariableNature.VariableDefinedOutsideLoop v) {
                        assertEquals("1", v.statementIndex());
                    } else fail();
                    String expect = d.iteration() == 0 ? "1+<v:i>" : "1+i$1";
                    assertEquals(expect, d.currentValue().toString());
                }
                if ("1".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? "1+<v:i>" : "1+instance type int";
                    if (d.iteration() > 0) assertTrue(d.variableInfoContainer().hasMerge());
                    assertEquals(expect, d.currentValue().toString());
                }
                if ("2".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? "1+<v:i>" : "1+instance type int";
                    assertEquals(expect, d.currentValue().toString());
                }
            }
            if (d.variable() instanceof ReturnVariable && "2".equals(d.statementId())) {
                String expectReturn = d.iteration() == 0 ? "1==<v:i>?5:<return value>" :
                        "0==instance type int?5:<return value>";
                assertEquals(expectReturn, d.currentValue().toString());
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name) && "2".equals(d.statementId())) {
                String expectState = d.iteration() == 0 ? "<v:i>>=10" : "instance type int>=10";
                assertEquals(expectState, d.state().toString());
            }
        };
        // expect: warning: always true in assert
        testClass("Loops_5", 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build());
    }

    @Test
    public void test6() throws IOException {
        testClass("Loops_6", 2, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test7() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if (!"method".equals(d.methodInfo().name)) return;
            if ("1".equals(d.statementId())) {
                String expect = d.iteration() == 0 ? "n><v:i>" : "n>i$1";
                assertEquals(expect, d.evaluationResult().value().toString());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if (!"method".equals(d.methodInfo().name)) return;
            if ("k".equals(d.variableName()) && "1.0.0".equals(d.statementId())) {
                String expect = d.iteration() == 0 ? "<v:i>" : "i$1";
                assertEquals(expect, d.currentValue().toString());
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if (!"method".equals(d.methodInfo().name)) return;
            if ("0".equals(d.statementId())) {
                assertSame(ALWAYS, d.statementAnalysis().flowData.getGuaranteedToBeReachedInMethod());
                assertSame(ALWAYS, d.statementAnalysis().flowData.getGuaranteedToBeReachedInCurrentBlock());
            }
            if ("1".equals(d.statementId())) {
                assertSame(ALWAYS, d.statementAnalysis().flowData.getGuaranteedToBeReachedInMethod());
                assertSame(ALWAYS, d.statementAnalysis().flowData.getGuaranteedToBeReachedInCurrentBlock());

                assertTrue(d.statementAnalysis().localVariablesAssignedInThisLoop.isFrozen());
                assertEquals("i", d.statementAnalysis().localVariablesAssignedInThisLoop.stream().collect(Collectors.joining()));
            }
            if ("1.0.0".equals(d.statementId())) {
                FlowData.Execution expectExec = d.iteration() == 0 ? DELAYED_EXECUTION : CONDITIONALLY;
                assertSame(expectExec, d.statementAnalysis().flowData.getGuaranteedToBeReachedInMethod());
                assertSame(ALWAYS, d.statementAnalysis().flowData.getGuaranteedToBeReachedInCurrentBlock());

                String expect = d.iteration() == 0 ? "n><v:i>" : "n>i$1";
                assertEquals(expect, d.absoluteState().toString());
            }
            if ("1.0.1".equals(d.statementId())) {
                String expect = d.iteration() == 0 ? "n><v:i>" : "n>i$1";
                assertEquals(expect, d.absoluteState().toString());
                assertEquals(expect, d.condition().toString());
                String expectInterrupt = "{}";
                assertEquals(expectInterrupt, d.statementAnalysis().flowData.getInterruptsFlow().toString());
            }
            if ("1.0.2".equals(d.statementId())) {
                String expect = d.iteration() == 0 ? "true" : "false";
                assertEquals(expect, d.state().toString());
                if (d.iteration() == 0) {
                    assertFalse(d.statementAnalysis().flowData.interruptsFlowIsSet());
                } else {
                    assertEquals("{break=CONDITIONALLY}", d.statementAnalysis().flowData.getInterruptsFlow().toString());
                }
            }
            if ("1.0.2.0.0".equals(d.statementId())) {
                String expect = "{break=ALWAYS}";
                assertEquals(expect, d.statementAnalysis().flowData.getInterruptsFlow().toString());
                if (d.iteration() == 0) {
                    assertFalse(d.statementAnalysis().flowData.blockExecution.isSet());
                } else {
                    assertSame(CONDITIONALLY, d.statementAnalysis().flowData.blockExecution.get());
                }
            }
            if ("1.0.3".equals(d.statementId())) {
                FlowData.Execution expectExec = d.iteration() == 0 ? DELAYED_EXECUTION : FlowData.Execution.NEVER;
                assertSame(expectExec, d.statementAnalysis().flowData.getGuaranteedToBeReachedInMethod());
            }
        };

        testClass("Loops_7", 2, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build());
    }

    @Test
    public void test_9() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                if ("node".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "<f:in>" : "\"abc\"";
                        assertEquals(expect, d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "strings.length>0?null:<f:in>" :
                                "strings.length>0?null:\"abc\"";
                        assertEquals(expect, d.currentValue().toString());
                    }
                }
                if ("node$1$1.0.0-E".equals(d.variableName())) {
                    String expected = d.iteration() == 0 ? "strings.length>0?null:<v:node>"
                            : "strings.length>0?null:node$1";
                    assertEquals(expected, d.currentValue().toString());
                }
            }
        };
        testClass("Loops_9", 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_10() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                if ("node".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "<f:in>" : "\"abc\"";
                        assertEquals(expect, d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "strings.isEmpty()?<f:in>:null" :
                                "strings.isEmpty()?\"abc\":null";
                        assertEquals(expect, d.currentValue().toString());
                    }
                }
                if ("node$1$1.0.0-E".equals(d.variableName())) {
                    String expected = d.iteration() == 0 ? "strings.isEmpty()?<v:node>:null"
                            : "strings.isEmpty()?node$1:null";
                    assertEquals(expected, d.currentValue().toString());
                }
            }
        };
        testClass("Loops_10", 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }


    @Test
    public void test_11() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("3".equals(d.statementId()) && d.iteration() > 0) {
                    assertEquals("count,entry",
                            d.statementAnalysis().localVariablesAssignedInThisLoop.toImmutableSet()
                                    .stream().sorted().collect(Collectors.joining(",")));
                }
            }
        };
        // potential null pointer exception
        testClass("Loops_11", 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }


    @Test
    public void test_12() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("2".equals(d.statementId()) && d.iteration() > 0) {
                    assertEquals("[i]", // and not RES!
                            d.statementAnalysis().localVariablesAssignedInThisLoop.toImmutableSet().toString());
                }
            }
        };
        // errors: overwriting previous assignment, empty loop, unused local variable, useless assignment
        // warning: parameter n not used
        testClass("Loops_12", 4, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

    @Test
    public void test_13() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("2".equals(d.statementId()) && d.iteration() > 0) {
                    assertEquals("i,res1",
                            d.statementAnalysis().localVariablesAssignedInThisLoop.toImmutableSet()
                                    .stream().sorted().collect(Collectors.joining(",")));
                }
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("i".equals(d.variableName())) {
                    if ("2.0.0".equals(d.statementId())) {
                        assertEquals("2-E", d.variableInfo().getAssignmentId());
                    }
                }
            }
        };

        // overwriting previous assignment, 1x (not for i++ !)
        testClass("Loops_13", 1, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_14() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("i".equals(d.variableName())) {
                    if ("2.0.0".equals(d.statementId())) {
                        // the 0 is inaccessible, because inside loop 2, i$2 is read rather than i
                        // this you can see in the "j"-"2.0.0" value
                        String expect = d.iteration() == 0 ? "<v:i>" : "0";
                        assertEquals(expect, d.currentValue().toString());
                    }
                }
                if ("i$2".equals(d.variableName())) {
                    assertTrue(d.iteration() > 0);
                    if ("2.0.0".equals(d.statementId()) || "2.0.1".equals(d.statementId())) {
                        String expect = "instance type int";
                        assertEquals(expect, d.currentValue().toString());
                    }
                    if ("2.0.2".equals(d.statementId())) {
                        String expect = "n>i$2?1+i$2:instance type int";
                        assertEquals(expect, d.currentValue().toString());
                    }
                }
                if ("j".equals(d.variableName())) {
                    if ("2.0.0.0.0".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "3==<v:i>?10:<v:j>" : "3==i$2?10:j$2";
                        assertEquals(expect, d.currentValue().toString());
                    }
                    if ("2.0.0".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "3==<v:i>?10:9" : "3==i$2?10:9";
                        assertEquals(expect, d.currentValue().toString());
                    }
                    if ("2.0.1".equals(d.statementId()) || "2.0.2".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "6==<v:i>?11:3==<v:i>?10:9" : "6==i$2?11:3==i$2?10:9";
                        assertEquals(expect, d.currentValue().toString());
                    }
                }
                if ("j$2".equals(d.variableName())) {
                    assertTrue(d.iteration() > 0);
                    if ("2.0.0.0.0".equals(d.statementId()) || "2.0.0".equals(d.statementId())) {
                        String expect = "3==i$2?10:instance type int";
                        assertEquals(expect, d.currentValue().toString());
                    }
                    if ("2.0.1.0.0".equals(d.statementId())
                            || "2.0.1".equals(d.statementId()) || "2.0.2".equals(d.statementId())) {
                        String expect = "6==i$2?11:3==i$2?10:instance type int";
                        assertEquals(expect, d.currentValue().toString());
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("2".equals(d.statementId())) {
                    assertTrue(d.statementAnalysis().localVariablesAssignedInThisLoop.isFrozen());
                    assertEquals("i,j", d.statementAnalysis().localVariablesAssignedInThisLoop.toImmutableSet()
                            .stream().sorted().collect(Collectors.joining(",")));
                }
                if ("2.0.0".equals(d.statementId())) {
                    String expected = d.iteration() == 0 ? "n><v:i>" : "n>i$2";
                    assertEquals(expected, d.absoluteState().toString());
                }
            }
        };

        testClass("Loops_14", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }
}
