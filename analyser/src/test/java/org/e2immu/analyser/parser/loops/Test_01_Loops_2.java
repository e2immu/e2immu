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

import static org.e2immu.analyser.analyser.Property.CONTEXT_NOT_NULL;
import static org.e2immu.analyser.analyser.Property.NOT_NULL_EXPRESSION;
import static org.e2immu.analyser.analysis.FlowData.ALWAYS;
import static org.junit.jupiter.api.Assertions.*;

/*
the loop variable "s" gets a value in evaluation only when the forEach expression is evaluated without delay.
the variable is not merged back into the loop statement; its value is replaced in ConditionAndVariableInfo.

The decision not to merge back prompts some exception code.

 */
public class Test_01_Loops_2 extends CommonTestRunner {

    public Test_01_Loops_2() {
        super(true);
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
                // res = s, only statement in loop
                if ("1.0.0".equals(d.statementId())) {
                    assertEquals("s", d.evaluationResult().value().toString());
                }
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("s".equals(d.variableName())) {
                    if (d.variableInfoContainer().variableNature() instanceof VariableNature.LoopVariable loopVariable) {
                        assertEquals("1", loopVariable.statementIndex());
                    } else fail();
                    assertEquals("java.lang.String", d.variableInfo().variable()
                            .parameterizedType().typeInfo.fullyQualifiedName);
                    if ("1".equals(d.statementId())) {
                        String expectValue = "instance type String";
                        assertTrue(d.variableInfoContainer().hasEvaluation());
                        assertFalse(d.variableInfoContainer().hasMerge());

                        assertEquals(expectValue, d.currentValue().toString());
                        assertEquals(MultiLevel.NULLABLE_DV, d.getProperty(CONTEXT_NOT_NULL));

                        assertEquals("s:0", d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("1.0.0".equals(d.statementId())) {
                        assertTrue(d.variableInfoContainer().hasEvaluation());
                        assertEquals("instance type String", d.currentValue().toString());

                        assertEquals(MultiLevel.NULLABLE_DV, d.getProperty(CONTEXT_NOT_NULL));

                        assertEquals("res:0,s:0", d.variableInfo().getLinkedVariables().toString());
                    }
                    assertFalse("2".equals(d.statementId()) || "0".equals(d.statementId()));
                }
                if ("res".equals(d.variableName())) {
                    if ("1.0.0".equals(d.statementId())) {
                        if (d.variableInfoContainer().variableNature() instanceof VariableNature.VariableDefinedOutsideLoop outside) {
                            assertEquals("1", outside.statementIndex());
                        } else fail();

                        assertEquals("s", d.currentValue().toString());
                        assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, NOT_NULL_EXPRESSION);
                        assertEquals("res:0,s:0", d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        if (d.variableInfoContainer().variableNature() instanceof VariableNature.VariableDefinedOutsideLoop outside) {
                            assertEquals("1", outside.statementIndex());
                        } else fail();
                        // loop is guaranteed to be executed, so this value overwrites the one from statement 0
                        assertEquals("instance type String", d.currentValue().toString());
                    }

                    if ("2".equals(d.statementId())) {
                        if (d.variableInfoContainer().variableNature() instanceof VariableNature.NormalLocalVariable normal) {
                            assertEquals("", normal.parentBlockIndex);
                        } else fail();

                        assertEquals("instance type String", d.currentValue().toString());
                        String expectLv = "res:0,return method:0";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());

                        assertEquals(MultiLevel.NULLABLE_DV, d.getProperty(CONTEXT_NOT_NULL));
                        assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, NOT_NULL_EXPRESSION);
                    }
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("2".equals(d.statementId())) {
                        assertEquals("res", d.currentValue().toString());

                        String expectLv = "res:0,return method:0";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());

                        assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, NOT_NULL_EXPRESSION);
                        assertEquals(MultiLevel.NULLABLE_DV, d.getProperty(CONTEXT_NOT_NULL));
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                DV execution = d.statementAnalysis().flowData().getGuaranteedToBeReachedInCurrentBlock();
                if ("1.0.0".equals(d.statementId())) {
                    assertEquals("s.equals(\"a\")||s.equals(\"b\")||s.equals(\"c\")", d.condition().debugOutput());
                    assertEquals("s.equals(\"a\")||s.equals(\"b\")||s.equals(\"c\")", d.absoluteState().debugOutput());
                    assertEquals(ALWAYS, execution);
                }
            }
        };

        // overwrite assignment, because loop is guaranteed to be executed, and assignment is guaranteed to be
        // executed inside the block
        testClass("Loops_2", 1, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }
}