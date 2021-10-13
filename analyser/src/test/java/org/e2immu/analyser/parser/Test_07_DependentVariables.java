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

import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analyser.VariableInfoContainer;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.visitor.EvaluationResultVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class Test_07_DependentVariables extends CommonTestRunner {
    public Test_07_DependentVariables() {
        super(false);
    }

    @Test
    public void test_0() throws IOException {

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method1".equals(d.methodInfo().name)) {

                String read = d.variableInfo().getReadId();
                String assigned = d.variableInfo().getAssignmentIds().getLatestAssignment();

                if ("1".equals(d.statementId()) && "array[0]".equals(d.variableName())) {
                    assertEquals("12", d.currentValue().toString());
                }
                if ("2".equals(d.statementId()) && "array[0]".equals(d.variableName())) {
                    assertTrue(assigned.compareTo(read) > 0);
                    assertEquals("12", d.variableInfo().getValue().toString());
                }
                if ("2".equals(d.statementId()) && "array[1]".equals(d.variableName())) {
                    assertEquals("13", d.currentValue().toString());
                }
                if ("4".equals(d.statementId()) && "array[0]".equals(d.variableName())) {
                    assertTrue(assigned.compareTo(read) < 0);
                    assertEquals("12", d.variableInfo().getValue().toString());
                }
                if ("4".equals(d.statementId()) && "array[1]".equals(d.variableName())) {
                    assertTrue(assigned.compareTo(read) < 0);
                    assertEquals("13", d.variableInfo().getValue().toString());
                }
                if ("4".equals(d.statementId()) && "array[2]".equals(d.variableName())) {
                    assertTrue(assigned.compareTo(read) < 0);
                    assertEquals("31", d.variableInfo().getValue().toString());
                }
                if ("4".equals(d.statementId()) && "array".equals(d.variableName())) {
                    assertEquals("4" + VariableInfoContainer.Level.EVALUATION, read);
                }
            }
            if ("method2".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo) {
                    assertEquals("instance type int/*@Identity*/", d.currentValue().toString());
                    if ("1".equals(d.statementId())) {
                        assertEquals(Level.FALSE, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                    }
                }
                if ("b".equals(d.variableName())) {
                    assertEquals("a", d.variableInfo().getValue().toString());
                }
                if ("3".equals(d.statementId())) {
                    if ("array[a]".equals(d.variableName())) {
                        fail("This variable should not be produced");
                    }
                    if ("array[b]".equals(d.variableName())) {
                        fail("This variable should not be produced");
                    }
                    if (("array[org.e2immu.analyser.testexample.DependentVariables.method2(int):0:a]").equals(d.variableName())) {
                        assertEquals("12", d.variableInfo().getValue().toString());
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method1".equals(d.methodInfo().name) && "4".equals(d.statementId())) {
                VariableInfo tv = d.getReturnAsVariable();
                assertEquals("56", tv.getValue().toString());
            }
            if ("method2".equals(d.methodInfo().name) && "3".equals(d.statementId())) {
                VariableInfo tv = d.getReturnAsVariable();
                assertEquals("12", tv.getValue().toString());
            }
        };

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("method2".equals(d.methodInfo().name)) {
                if ("2".equals(d.statementId())) {
                    assertEquals("12", d.evaluationResult().value().toString());

                }
            }
        };

        // unused parameter in method1
        testClass("DependentVariables_0", 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build());
    }

    @Test
    public void test_1() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("getX".equals(d.methodInfo().name)) {
                int vars = d.iteration() == 0 ? 4 : 5;
                assertEquals(vars, d.evaluationResult().changeData().keySet().size());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("getX".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    String expectValue = d.iteration() == 0 ? "<v:<f:xs>[index]>" : "instance type X/*{L1 xs}*/";
                    assertEquals(expectValue, d.currentValue().toString());
                    String expectLv1 = d.iteration() == 0 ? "*" : "xs,xs[index]";
                    assertEquals(expectLv1, d.variableInfo().getLinked1Variables().toSimpleString());
                }
                if (d.variable() instanceof ParameterInfo) {
                    assertEquals(d.falseFrom1(), d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("getX".equals(d.methodInfo().name)) {
                int expectIndependent = d.iteration() == 0 ? Level.DELAY : MultiLevel.INDEPENDENT_1;
                assertEquals(expectIndependent, d.methodAnalysis().getProperty(VariableProperty.INDEPENDENT));
            }
        };

        testClass("DependentVariables_1", 0, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }
}
