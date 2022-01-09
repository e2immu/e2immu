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

package org.e2immu.analyser.parser.loops;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.VariableInfoContainer;
import org.e2immu.analyser.analysis.impl.StatementAnalysisImpl;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.expression.BooleanConstant;
import org.e2immu.analyser.model.statement.WhileStatement;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.EvaluationResultVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.e2immu.analyser.analysis.FlowData.ALWAYS;
import static org.e2immu.analyser.analysis.FlowData.CONDITIONALLY;
import static org.junit.jupiter.api.Assertions.*;

public class Test_01_Loops_0 extends CommonTestRunner {

    public Test_01_Loops_0() {
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
            if (d.variable() instanceof This) {
                if ("2.0.0".equals(d.statementId())) {
                    assertTrue(d.variableInfo().getLinkedVariables().isEmpty());
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
                    assertTrue(d.variableInfoContainer().hasMerge());
                    // FIXME 2+ should be 1+
                    String expect = d.iteration() == 0 ? "1+<v:i>" : "2+instance type int";
                    assertEquals(expect, d.currentValue().toString());
                }
                if ("2.0.0".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? "0" : "1+i$2";
                    assertEquals(expect, d.currentValue().toString());
                }
                if ("2.0.1".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? "1+<v:i>" : "1+i$2";
                    assertEquals(expect, d.currentValue().toString());
                }
                if ("3".equals(d.statementId())) {
                    // FIXME 2+ should be 1+ reeval
                    String expect = d.iteration() == 0 ? "1+<v:i>" : "2+instance type int";
                    assertEquals(expect, d.currentValue().toString());
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if (!"method".equals(d.methodInfo().name)) return;
            if ("2".equals(d.statementId())) {
                if (d.statementAnalysis().statement() instanceof WhileStatement whileStatement) {
                    DV exec = whileStatement.structure.statementExecution()
                            .apply(new BooleanConstant(((StatementAnalysisImpl) d.statementAnalysis()).primitives, true),
                                    d.evaluationContext());
                    assertEquals(ALWAYS, exec);
                } else fail();
                String expectState = d.iteration() == 0 ? "<v:i>>=n" : "1+instance type int>=n";
                assertEquals(expectState, d.state().toString());

                assertEquals(ALWAYS, d.statementAnalysis().flowData().getGuaranteedToBeReachedInMethod());
                assertEquals(ALWAYS, d.statementAnalysis().flowData().getGuaranteedToBeReachedInCurrentBlock());
            }
            if ("2.0.0".equals(d.statementId())) {
                assertEquals("true", d.condition().toString());
                assertEquals("true", d.state().toString());
                assertTrue(d.localConditionManager().precondition().isEmpty());
                if (d.iteration() == 0) {
                    VariableInfoContainer vic = d.statementAnalysis().getVariable("i");
                    assertEquals("0", vic.current().getValue().toString());
                }
                assertEquals(ALWAYS, d.statementAnalysis().flowData().getGuaranteedToBeReachedInMethod());
                assertEquals(ALWAYS, d.statementAnalysis().flowData().getGuaranteedToBeReachedInCurrentBlock());
            }
            if ("2.0.1".equals(d.statementId())) {
                assertTrue(d.localConditionManager().precondition().isEmpty());
            }
            if ("2.0.2".equals(d.statementId())) {
                assertEquals("true", d.condition().toString());
                String expectState = d.iteration() == 0 ? "-1+n>=<v:i>" : "-2-i$2+n>=0";
                assertEquals(expectState, d.state().toString());
                assertEquals(d.iteration() == 0, d.statementAnalysis()
                        .stateData().conditionManagerForNextStatement.isVariable());

                assertEquals(ALWAYS, d.statementAnalysis().flowData().getGuaranteedToBeReachedInMethod());
                assertEquals(ALWAYS, d.statementAnalysis().flowData().getGuaranteedToBeReachedInCurrentBlock());
            }
            // shows that the BREAK statement, always executed in its own block, is dependent on a valid condition
            if ("2.0.2.0.0".equals(d.statementId())) {
                String expect = d.iteration() == 0 ? "<v:i>>=n" : "1+i$2>=n";
                assertEquals(expect, d.condition().toString());

                assertDv(d, 1, CONDITIONALLY, d.statementAnalysis().flowData().getGuaranteedToBeReachedInMethod());
                assertEquals(ALWAYS, d.statementAnalysis().flowData().getGuaranteedToBeReachedInCurrentBlock());
            }
        };
        testClass("Loops_0", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build());
    }

}
