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

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.expression.ConstantExpression;
import org.e2immu.analyser.model.expression.StringConcat;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class Test_29_TryStatement extends CommonTestRunner {

    public Test_29_TryStatement() {
        super(false);
    }

    @Test
    public void test_0() throws IOException {
        final String TYPE = "org.e2immu.analyser.testexample.TryStatement_0";
        final String METHOD_FQN = TYPE + ".method(java.lang.String)";

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name) && METHOD_FQN.equals(d.variableName())) {
                if ("0".equals(d.statementId())) {
                    // meaning: no idea
                    assertEquals("nullable instance type String", d.currentValue().toString());
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("0.0.0".equals(d.statementId())) {
                    Expression value0 = d.statementAnalysis().variables.get(METHOD_FQN).current().getValue();
                    assertTrue(value0 instanceof StringConcat, "Got " + value0.getClass());
                }
                if ("0.1.0".equals(d.statementId())) {
                    Expression value1 = d.statementAnalysis().variables.get(METHOD_FQN).current().getValue();
                    assertTrue(value1 instanceof ConstantExpression, "Got " + value1.getClass());
                }
                assertFalse(d.statementAnalysis().methodLevelData.internalObjectFlowNotYetFrozen(),
                        "Statement " + d.statementId() + ", it " + d.iteration());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                Expression srv = d.methodAnalysis().getSingleReturnValue();
                assertEquals("nullable instance type String", srv.toString());
            }
        };

        testClass("TryStatement_0", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

    @Test
    public void test_1() throws IOException {
        testClass("TryStatement_1", 0, 0, new DebugConfiguration.Builder()
                .build());
    }


    @Test
    public void test_2() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name) && "npe".equals(d.variableName())) {
                if ("1.1.0".equals(d.statementId())) {
                    assertEquals("instance type NullPointerException", d.currentValue().toString());
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name) && "1.1.1".equals(d.statementId())) {
                assertEquals("ERROR in M:method:1.1.1: Useless assignment: res",
                        d.haveError(Message.USELESS_ASSIGNMENT));
            }
            if ("method".equals(d.methodInfo().name) && "1.2.1".equals(d.statementId())) {
                assertEquals("ERROR in M:method:1.2.1: Useless assignment: res",
                        d.haveError(Message.USELESS_ASSIGNMENT));
            }
        };

        // warn: unused parameter
        testClass("TryStatement_2", 2, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

    @Test
    public void test_3() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name) && "res".equals(d.variableName())) {
                if ("1".equals(d.statementId()) || "2".equals(d.statementId())) {
                    assertEquals("null", d.currentValue().toString());
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                assertEquals(Level.TRUE, d.methodAnalysis().getProperty(VariableProperty.CONSTANT));
            }
        };

        testClass("TryStatement_3", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test_4() throws IOException {
        testClass("TryStatement_4", 0, 0, new DebugConfiguration.Builder()
                .build());
    }
}
