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

package org.e2immu.analyser.parser.failing;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.ArrayInitializer;
import org.e2immu.analyser.model.expression.BooleanConstant;
import org.e2immu.analyser.model.statement.ForEachStatement;
import org.e2immu.analyser.model.statement.WhileStatement;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.model.variable.VariableNature;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.chrono.ChronoLocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static org.e2immu.analyser.analyser.FlowData.ALWAYS;
import static org.e2immu.analyser.analyser.FlowData.CONDITIONALLY;
import static org.e2immu.analyser.analyser.Property.*;
import static org.junit.jupiter.api.Assertions.*;

public class Test_01_Loops extends CommonTestRunner {

    public static final String DELAYED_BY_STATE = "<s:String>";

    public Test_01_Loops() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
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
                    String expect = d.iteration() == 0 ? "1+<v:i>" : "instance type int";
                    assertEquals(expect, d.currentValue().toString());
                }
                if ("2.0.1".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? "1+<v:i>" : "1+i$2";
                    assertEquals(expect, d.currentValue().toString());
                }
                if ("3".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? "1+<v:i>" : "instance type int";
                    assertEquals(expect, d.currentValue().toString());
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if (!"method".equals(d.methodInfo().name)) return;
            if ("2".equals(d.statementId())) {
                if (d.statementAnalysis().statement instanceof WhileStatement whileStatement) {
                    DV exec = whileStatement.structure.statementExecution()
                            .apply(new BooleanConstant(d.statementAnalysis().primitives, true),
                                    d.evaluationContext());
                    assertEquals(ALWAYS, exec);
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

                assertDv(d, 1, CONDITIONALLY, d.statementAnalysis().flowData.getGuaranteedToBeReachedInMethod());
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
    public void test_1() throws IOException {
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
                    String expect = d.iteration() == 0 ? DELAYED_BY_STATE : "res2";
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
                        assertEquals(MultiLevel.NULLABLE_DV, d.getProperty(NOT_NULL_EXPRESSION));
                    }
                    if ("2.0.2".equals(d.statementId())) {
                        assertEquals("-1-i$2+n>=1?\"abc\":nullable instance type String", d.currentValue().toString());
                        assertEquals(MultiLevel.NULLABLE_DV, d.getProperty(NOT_NULL_EXPRESSION));
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
                        assertDv(d, 1, MultiLevel.NULLABLE_DV, NOT_NULL_EXPRESSION);
                    }
                    if ("2".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? DELAYED_BY_STATE : "nullable instance type String";
                        assertEquals(expectValue, d.variableInfo().getValue().toString());
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                DV execution = d.statementAnalysis().flowData.getGuaranteedToBeReachedInCurrentBlock();
                if ("2.0.0".equals(d.statementId())) {
                    assertEquals("true", d.condition().debugOutput());
                    assertEquals("true", d.absoluteState().debugOutput());
                    assertEquals(ALWAYS, execution);
                }
                if ("2.0.1.0.0".equals(d.statementId())) {
                    String expectCondition = d.iteration() == 0 ? "<v:i>>=n" : "1+i$2>=n";
                    assertEquals(expectCondition, d.condition().toString());
                    assertEquals(d.iteration() == 0, d.localConditionManager().isDelayed());
                }
                if ("2.0.1".equals(d.statementId())) { // if (i>=n) break;
                    assertEquals(ALWAYS, execution);

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

                    assertDv(d, 1, CONDITIONALLY, execution);
                }
            }
        };

        // because the assignment to res2 is not guaranteed to be executed, there is no error
        testClass("Loops_1", 0, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

    @Test
    public void test_2() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    assertEquals("{\"a\",\"b\",\"c\"}", d.evaluationResult().value().toString());
                    assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, d.evaluationResult().value()
                            .getProperty(d.evaluationResult().evaluationContext(), NOT_NULL_EXPRESSION, true));
                }
                if ("1.0.0".equals(d.statementId()) && d.iteration() > 0) {
                    assertEquals("s$1", d.evaluationResult().value().toString());
                }
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("s".equals(d.variableName())) {
                    assertEquals("java.lang.String", d.variableInfo().variable()
                            .parameterizedType().typeInfo.fullyQualifiedName);
                    if ("1".equals(d.statementId())) {
                        String expectValue = "nullable instance type String";
                        assertEquals(expectValue, d.currentValue().toString());
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(CONTEXT_NOT_NULL));

                        assertEquals("s:0", d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("1.0.0".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<v:s>" : "nullable instance type String";
                        assertEquals(expectValue, d.currentValue().toString());

                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(CONTEXT_NOT_NULL));

                        String expectLv = d.iteration() == 0 ? "res:0,s:0" : "res:0,s$1:1,s:0";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                    }
                }
                if ("s$1".equals(d.variableName())) {
                    assertTrue(d.iteration() > 0);
                    if ("1.0.0".equals(d.statementId())) {
                        assertEquals("nullable instance type String", d.currentValue().toString());
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(CONTEXT_NOT_NULL));
                        assertEquals(MultiLevel.NULLABLE_DV, d.getProperty(NOT_NULL_EXPRESSION));
                    }
                }
                if ("res$1".equals(d.variableName())) {
                    assertTrue(d.iteration() > 0);
                    if ("1.0.0".equals(d.statementId())) {
                        assertEquals(MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, d.getProperty(IMMUTABLE));
                    }
                }
                if ("res".equals(d.variableName())) {
                    if ("1.0.0".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<v:s>" : "s$1";
                        assertEquals(expectValue, d.currentValue().toString());
                        String expectLv = d.iteration() == 0 ? "res:0,s:0" : "res:0,s$1:1,s:0";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<merge:String>" : "instance type String";
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                    if ("2".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<merge:String>" : "instance type String";
                        assertEquals(expectValue, d.currentValue().toString());
                        String expectLv = d.iteration() == 0 ? "res:0,return method:0,s:-1" : "res:0,return method:0";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());

                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(CONTEXT_NOT_NULL));
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, NOT_NULL_EXPRESSION);
                    }
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("2".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "<merge:String>" : "res"; // indirection
                        assertEquals(expect, d.currentValue().toString());

                        String expectLv = d.iteration() == 0 ? "res:0,return method:0,s:-1" : "res:0,return method:0";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());

                        assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, NOT_NULL_EXPRESSION);
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(CONTEXT_NOT_NULL));
                    }
                }
            }
        };

        // overwrite assignment, because loop is guaranteed to be executed, and assignment is guaranteed to be
        // executed inside the block
        testClass("Loops_2", 1, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    // explicitly empty loop
    @Test
    public void test_3() throws IOException {
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
                        String expectLinked = "res:0";
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
                        DV exec = forEachStatement.structure.statementExecution()
                                .apply(new ArrayInitializer(d.evaluationContext().getAnalyserContext(),
                                        List.of(), d.statementAnalysis().primitives.stringParameterizedType()), d.evaluationContext());
                        assertSame(FlowData.NEVER, exec);

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
    public void test_4() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? "<replace:int>>=10" : "instance type int>=10";
                    assertEquals(expect, d.state().toString());
                    assertNull(d.haveError(Message.Label.INLINE_CONDITION_EVALUATES_TO_CONSTANT));
                }
                if ("1".equals(d.statementId())) {
                    assertEquals("true", d.condition().toString());
                    String expectState = d.iteration() == 0 ? "<replace:int>>=10" : "instance type int>=10";
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
                    } else {
                        fail();
                    }
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
                        String expect = d.iteration() == 0 ? "<replace:int><=9?<merge:int>:<return value>"
                                : "instance type int<=9?instance type int:<return value>";
                        assertEquals(expect, d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        String expect = d.iteration() == 0
                                ? "<replace:int>>=10?0:<replace:int><=9&&<replace:int><=9?<merge:int>:<return value>"
                                : "instance type int>=10?0:instance type int";
                        assertEquals(expect, d.currentValue().toString());
                        String expectVars = d.iteration() == 0 ? "[method]" : "[]";
                        assertEquals(expectVars, d.currentValue().variables().toString());
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
    public void test_5() throws IOException {
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
                if ("1.0.0".equals(d.statementId())) {
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
                        "instance type int<=9?instance type int:<return value>";
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
    public void test_6() throws IOException {
        testClass("Loops_6", 1, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_7() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? "n><v:i>" : "n>i$1";
                    assertEquals(expect, d.evaluationResult().value().toString());
                }
                if ("1.0.0".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? "<v:i>" : "i$1";
                    assertEquals(expect, d.evaluationResult().value().toString());
                }
                if ("1.0.2".equals(d.statementId())) {
                    // in the first iteration (0), we compare 1+<v:i> against <v:i>, which is false
                    String expect = d.iteration() == 0 ? "false" : "true";
                    assertEquals(expect, d.evaluationResult().value().toString());
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("k".equals(d.variableName())) {
                    if ("1.0.0".equals(d.statementId()) || "1.0.1".equals(d.statementId())
                            || "1.0.2".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "<v:i>" : "i$1";
                        assertEquals(expect, d.currentValue().toString());
                    }
                }
                if ("i".equals(d.variableName())) {
                    if ("1.0.0".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "<v:i>" : "0";
                        assertEquals(expect, d.currentValue().toString());
                    }
                    if ("1.0.1".equals(d.statementId())) {
                        // it 1: 1+i$1 rather than n>i$1?1+i$1:i$1
                        String expect = d.iteration() == 0 ? "1+<v:i>" : "1+i$1";
                        assertEquals(expect, d.currentValue().toString());
                    }
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertEquals(ALWAYS, d.statementAnalysis().flowData.getGuaranteedToBeReachedInMethod());
                    assertEquals(ALWAYS, d.statementAnalysis().flowData.getGuaranteedToBeReachedInCurrentBlock());
                }
                if ("1".equals(d.statementId())) {
                    assertEquals(ALWAYS, d.statementAnalysis().flowData.getGuaranteedToBeReachedInMethod());
                    assertEquals(ALWAYS, d.statementAnalysis().flowData.getGuaranteedToBeReachedInCurrentBlock());

                    assertTrue(d.statementAnalysis().localVariablesAssignedInThisLoop.isFrozen());
                    assertEquals("i", d.statementAnalysis().localVariablesAssignedInThisLoop.stream().collect(Collectors.joining()));
                }
                if ("1.0.0".equals(d.statementId())) {
                    assertDv(d, 1, CONDITIONALLY, d.statementAnalysis().flowData.getGuaranteedToBeReachedInMethod());
                    assertEquals(ALWAYS, d.statementAnalysis().flowData.getGuaranteedToBeReachedInCurrentBlock());

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
                    assertDv(d, 1, FlowData.NEVER, d.statementAnalysis().flowData.getGuaranteedToBeReachedInMethod());
                }
            }
        };

        // expression in if statement always true; unreachable statement
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
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("java.time.ZoneOffset.UTC".equals(d.variableName())) {
                    if ("1".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<f:UTC>" : "nullable instance type ZoneOffset";
                        assertEquals(expectValue, d.currentValue().toString());
                        assertEquals("java.time.ZoneOffset.UTC:0", d.variableInfo().getLinkedVariables().toString());

                        assertEquals(Level.TRUE_DV, d.getProperty(CONTEXT_MODIFIED));
                    }
                }
                if ("result".equals(d.variableName())) {
                    if ("4".equals(d.statementId())) {
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(CONTEXT_NOT_NULL));
                        assertEquals("result:0", d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("5".equals(d.statementId())) {
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(CONTEXT_NOT_NULL));
                    }
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("4".equals(d.statementId())) {
                        assertEquals("return method:0", d.variableInfo().getLinkedVariables().toString());
                        assertEquals(MultiLevel.NULLABLE_DV, d.getProperty(CONTEXT_NOT_NULL));
                    }
                    if ("5".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0
                                ? "map.entrySet().isEmpty()?new HashMap<>()/*AnnotatedAPI.isKnown(true)&&0==this.size()*/:<merge:Map<String,String>>"
                                : "map.entrySet().isEmpty()?new HashMap<>()/*AnnotatedAPI.isKnown(true)&&0==this.size()*/:instance type Map<String,String>";
                        assertEquals(expectValue, d.currentValue().toString());
                        String expectLv = d.iteration() == 0 ? "entry:-1,key:-1,result:0,return method:0" : "result:0,return method:0";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(CONTEXT_NOT_NULL));
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, NOT_NULL_EXPRESSION);
                    }
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("3".equals(d.statementId()) && d.iteration() > 0) {
                    assertEquals("count,entry",
                            d.statementAnalysis().localVariablesAssignedInThisLoop.toImmutableSet()
                                    .stream().sorted().collect(Collectors.joining(",")));
                }
            }
        };
        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo typeInfo = typeMap.get(LocalDateTime.class);
            MethodInfo now = typeInfo.findUniqueMethod("now", 0);
            assertTrue(now.methodInspection.get().isStatic());
            assertEquals(Level.FALSE_DV, now.methodAnalysis.get().getProperty(MODIFIED_METHOD));
            TypeInfo chrono = typeMap.get(ChronoLocalDateTime.class);
            MethodInfo toInstant = chrono.findUniqueMethod("toInstant", 1);
            assertFalse(toInstant.methodInspection.get().isStatic());
            assertEquals(Level.FALSE_DV, toInstant.methodAnalysis.get().getProperty(MODIFIED_METHOD));
            ParameterAnalysis utc = toInstant.parameterAnalysis(0);
            assertEquals(Level.TRUE_DV, utc.getProperty(MODIFIED_VARIABLE));
        };

        // potential null pointer exception
        testClass("Loops_11", 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addTypeMapVisitor(typeMapVisitor)
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
        // errors:
        // 1- overwriting previous assignment: in condition
        // 2- empty loop
        // 3- unused local variable (in stmt 3, because the loop is empty, i is not used)
        // warning: parameter n not used
        testClass("Loops_12", 3, 1, new DebugConfiguration.Builder()
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
                        assertEquals("2-E", d.variableInfo().getAssignmentIds().toString());
                    }
                }
            }
        };

        // overwriting previous assignment, for i (in the condition) and res1
        // (even though the compiler forces us to assign a value, this is bad programming)
        testClass("Loops_13", 2, 0, new DebugConfiguration.Builder()
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
                        assertEquals("instance type int", d.currentValue().toString());
                    }
                    if ("2.0.2".equals(d.statementId())) {
                        String expect = "1+i$2";
                        assertEquals(expect, d.currentValue().toString());
                    }
                }
                if ("j".equals(d.variableName())) {
                    if ("2.0.0.0.0".equals(d.statementId())) {
                        assertEquals("10", d.currentValue().toString());
                    }
                    if ("2.0.0".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "3==<v:i>?10:9" : "3==i$2?10:9";
                        assertEquals(expect, d.currentValue().toString());
                    }
                    if ("2.0.1".equals(d.statementId()) || "2.0.2".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "6==<v:i>?<s:int>:3==<v:i>?10:9" : "6==i$2?11:3==i$2?10:9";
                        assertEquals(expect, d.currentValue().toString());
                    }
                }
                if ("j$2".equals(d.variableName())) {
                    assertTrue(d.iteration() > 0);
                    if ("2.0.0.0.0".equals(d.statementId()) || "2.0.0".equals(d.statementId())) {
                        assertEquals("instance type int", d.currentValue().toString());
                    }
                    if ("2.0.1.0.0".equals(d.statementId())) {
                        assertEquals("11", d.currentValue().toString());
                    }
                    if ("2.0.1".equals(d.statementId()) || "2.0.2".equals(d.statementId())) {
                        assertEquals("6==i$2?11:instance type int", d.currentValue().toString());
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

    // IMPROVE this is really not good (res can have only 2 values, 3 and 4)
    // 16, 17 are variants, same problem

    @Test
    public void test_15() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("res".equals(d.variableName())) {
                    if ("1".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<replace:int><=9?<merge:int>:3"
                                : "instance type int<=9?instance type int:3";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
            }
        };
        testClass("Loops_15", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_16() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("res".equals(d.variableName())) {
                    if ("1".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "map.entrySet().isEmpty()?3:<merge:int>"
                                : "map.entrySet().isEmpty()?3:instance type int";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
            }
        };
        testClass("Loops_16", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_17() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("res".equals(d.variableName())) {
                    if ("1".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<m:entrySet>.isEmpty()?3:<merge:int>"
                                : "map$0.entrySet().isEmpty()?3:instance type int";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
            }
        };
        testClass("Loops_17", 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }


    // looks very much like Project_0.recentlyReadAndUpdatedAfterwards, which has multiple problems
    // 20211015: there's a 3rd linked1Variables value for 1.0.1.0.0: empty
    @Test
    public void test_18() throws IOException {

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("1".equals(d.statementId())) {
                String expectValue = d.iteration() == 0 ? "<m:entrySet>" : "kvStore$0.entrySet()";
                assertEquals(expectValue, d.evaluationResult().value().toString());
            }
            if ("1.0.1.0.0".equals(d.statementId())) {
                String expectValue = d.iteration() == 0 ? "<m:getValue>" : "entry$1.getValue()";
                assertEquals(expectValue, d.evaluationResult().value().toString());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("result".equals(d.variableName())) {
                    if ("1".equals(d.statementId())) {
                        String expected = d.iteration() == 0
                                ? "<m:entrySet>.isEmpty()?new HashMap<>()/*AnnotatedAPI.isKnown(true)&&0==this.size()*/:<merge:Map<String,String>>"
                                : "kvStore$0.entrySet().isEmpty()?new HashMap<>()/*AnnotatedAPI.isKnown(true)&&0==this.size()*/:instance type Map<String,String>";
                        assertEquals(expected, d.currentValue().toString());
                        String expectVars = d.iteration() == 0 ? "[entry, key, kvStore, result]" : "[kvStore$0]";
                        assertEquals(expectVars, d.currentValue().variables().stream().map(Variable::toString).sorted().toList().toString());
                    }
                }
                if ("entry".equals(d.variableName())) {
                    if ("1.0.0".equals(d.statementId())) {
                        String expectLv = d.iteration() == 0 ? "entry:0,key:-1" : "entry:0";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                    }

                    if ("1.0.1.0.0".equals(d.statementId())) {
                        String expectL1 = d.iteration() == 0 ? "container:-1,entry:0,key:-1" : "entry:0";
                        assertEquals(expectL1, d.variableInfo().getLinkedVariables().toString());
                    }
                }
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Container".equals(d.typeInfo().simpleName)) {
                assertDv(d, 1, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, IMMUTABLE);
                assertEquals(MultiLevel.INDEPENDENT_DV, d.typeAnalysis().getProperty(INDEPENDENT));
            }
        };

        testClass("Loops_18", 0, 1, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .build());
    }


    // looks very much like Project_0.recentlyReadAndUpdatedAfterwards, which has multiple problems
    // Solution: DelayedExpression.translate()
    @Test
    public void test_19() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("1.0.1.0.1".equals(d.statementId())) {
                EvaluationResult.ChangeData cd = d.findValueChangeByToString("container.read");
                assertFalse(cd.properties().containsKey(CONTEXT_NOT_NULL));
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {

                if ("container".equals(d.variableName())) {
                    if ("1.0.1.0.0".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<m:getValue>" : "entry$1.getValue()";
                        assertEquals(expected, d.currentValue().toString());
                        assertTrue(d.variableInfoContainer().hasEvaluation());
                        assertFalse(d.variableInfoContainer().hasMerge());
                    }
                    if ("1.0.1.0.1".equals(d.statementId())) {
                        String expected = d.iteration() <= 2 ? "<m:getValue>" : "entry$1.getValue()";
                        assertEquals(expected, d.currentValue().toString());
                        assertTrue(d.variableInfoContainer().hasEvaluation());
                        assertTrue(d.variableInfoContainer().hasMerge());
                    }
                }

                if ("org.e2immu.analyser.testexample.Loops_19.Date.time#entry$1.getValue().read".equals(d.variableName())) {
                    assertTrue(d.iteration() >= 2);
                    String expected = d.iteration() == 2 ? "<f:time>" : "instance type long";
                    assertEquals(expected, d.currentValue().toString());
                }

                if ("result".equals(d.variableName())) {
                    if ("1".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0 -> "<m:entrySet>.isEmpty()?new HashMap<>()/*AnnotatedAPI.isKnown(true)&&0==this.size()*/:<merge:Map<String,String>>";
                            case 1, 2 -> "kvStore$0.entrySet().isEmpty()?new HashMap<>()/*AnnotatedAPI.isKnown(true)&&0==this.size()*/:<merge:Map<String,String>>";
                            default -> "kvStore$0.entrySet().isEmpty()?new HashMap<>()/*AnnotatedAPI.isKnown(true)&&0==this.size()*/:instance type Map<String,String>";
                        };
                        assertEquals(expected, d.currentValue().toString());
                    }
                }

                if ("org.e2immu.analyser.testexample.Loops_19.Container.read#container".equals(d.variableName())) {
                    if ("1.0.1.0.1".equals(d.statementId())) {
                        String expectValue = d.iteration() <= 1 ? "<f:read>" : "";
                        assertEquals(expectValue, d.currentValue().toString());

                        String expectLv = d.iteration() <= 1 ? "container.read:0" : "";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());

                        // must be nullable, because of the null check
                        assertEquals(MultiLevel.NULLABLE_DV, d.getProperty(CONTEXT_NOT_NULL));
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("1.0.0".equals(d.statementId())) {
                    String expectCondition = d.iteration() == 0 ? "!<m:entrySet>.isEmpty()" : "!kvStore$0.entrySet().isEmpty()";
                    assertEquals(expectCondition, d.conditionManagerForNextStatement().condition().toString());
                }
            }
        };

        testClass("Loops_19", 0, 1, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }
}
