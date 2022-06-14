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
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.VariableNature;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.EvaluationResultVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.e2immu.analyser.analyser.Property.NOT_NULL_EXPRESSION;
import static org.e2immu.analyser.analysis.FlowData.ALWAYS;
import static org.e2immu.analyser.analysis.FlowData.CONDITIONALLY;
import static org.junit.jupiter.api.Assertions.*;

public class Test_01_Loops_1 extends CommonTestRunner {

    public static final String DELAYED_BY_STATE = "<s:String>";

    public Test_01_Loops_1() {
        super(true);
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
                    String expect = d.iteration() == 0 ? DELAYED_BY_STATE : "-2-i$2+n>=0?\"abc\":res2$2";
                    assertEquals(expect, d.evaluationResult().value().toString());
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    if ("2".equals(d.statementId())) {
                        assertEquals("<return value>", d.currentValue().toString());
                    }
                    if ("3".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<s:String>" : "-2-i$2+n>=0?\"abc\":res2$2";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 1, MultiLevel.NULLABLE_DV, NOT_NULL_EXPRESSION);
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
                        assertDv(d, 1, MultiLevel.NULLABLE_DV, NOT_NULL_EXPRESSION);
                    }
                    if ("2.0.2".equals(d.statementId())) {
                        // statement says: res="abc", but the value takes the state into account
                        String expectValue = d.iteration() == 0 ? DELAYED_BY_STATE : "-2-i$2+n>=0?\"abc\":res2$2";
                        assertEquals(expectValue, d.variableInfo().getValue().toString());
                        assertDv(d, 1, MultiLevel.NULLABLE_DV, NOT_NULL_EXPRESSION);
                    }
                    if ("2".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? DELAYED_BY_STATE : "-2-i$2+n>=0?\"abc\":res2$2";
                        assertEquals(expectValue, d.variableInfo().getValue().toString());
                        assertDv(d, 1, MultiLevel.NULLABLE_DV, NOT_NULL_EXPRESSION);
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                DV execution = d.statementAnalysis().flowData().getGuaranteedToBeReachedInCurrentBlock();
                if ("2.0.0".equals(d.statementId())) {
                    assertEquals("true", d.condition().toString());
                    assertEquals("true", d.absoluteState().toString());
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
                    assertEquals("true", d.condition().toString());
                    String expectState = d.iteration() == 0 ? "-1-<v:i>+n>=0" : "-2-i$2+n>=0";
                    assertEquals(expectState, d.absoluteState().toString());
                    assertEquals(d.iteration() == 0, d.conditionManagerForNextStatement().isDelayed());
                }
                if ("2.0.2".equals(d.statementId())) { // res2 = "abc"
                    assertEquals("true", d.condition().toString());
                    assertEquals(d.iteration() == 0, d.localConditionManager().isDelayed());

                    String expectState = d.iteration() == 0 ? "-1-<v:i>+n>=0" : "-2-i$2+n>=0";

                    assertEquals(expectState, d.localConditionManager().state().toString());
                    assertEquals(expectState, d.absoluteState().toString());

                    assertDv(d, 1, CONDITIONALLY, execution);
                }
                if ("3".equals(d.statementId()) || "2".equals(d.statementId())) {
                    String expectState = d.iteration() == 0 ? "<v:i>>=n" : "1+i>=n";
                    assertEquals(expectState, d.state().toString());
                }
            }
        };

        // because the assignment to res2 is not guaranteed to be executed, there is no error
        testClass("Loops_1", 0, 0, new DebugConfiguration.Builder()
             //   .addEvaluationResultVisitor(evaluationResultVisitor)
              //  .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
             //   .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }
}
