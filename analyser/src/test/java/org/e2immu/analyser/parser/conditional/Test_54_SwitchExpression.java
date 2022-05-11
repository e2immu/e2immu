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

package org.e2immu.analyser.parser.conditional;

import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.Statement;
import org.e2immu.analyser.model.expression.Lambda;
import org.e2immu.analyser.model.statement.IfElseStatement;
import org.e2immu.analyser.model.statement.ReturnStatement;
import org.e2immu.analyser.model.statement.ThrowStatement;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Test_54_SwitchExpression extends CommonTestRunner {
    public Test_54_SwitchExpression() {
        super(false);
    }

    @Test
    public void test_0() throws IOException {
        testClass("SwitchExpression_0", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_1() throws IOException {
        testClass("SwitchExpression_1", 1, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_2() throws IOException {
        testClass("SwitchExpression_2", 1, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_3() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo p && "b".equals(p.name)) {
                    if ("0".equals(d.statementId())) {
                        assertTrue(d.variableInfo().isRead());
                    }
                }
            }
        };
        // one potential null ptr
        testClass("SwitchExpression_3", 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_4() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("apply".equals(d.methodInfo().name)) {
                assertEquals("$1", d.methodInfo().typeInfo.simpleName);
                if (d.variable() instanceof ParameterInfo pi && "b".equals(pi.name)) {
                    Statement statement = d.context().evaluationContext().getCurrentStatement().getStatementAnalysis().statement();
                    if ("2".equals(d.statementId())) { // if(b) {...}...
                        assertTrue(statement instanceof IfElseStatement);
                        assertDv(d, 2, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                    }
                    if ("3".equals(d.statementId())) { // throws
                        assertTrue(statement instanceof ThrowStatement);
                        assertDv(d, 2, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                    }
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                assertEquals("0", d.statementId());
                assertTrue(d.statementAnalysis().statement() instanceof ReturnStatement rs
                        && rs.expression instanceof Lambda lambda
                        && "$1".equals(lambda.definesType().simpleName));
                String properties = switch (d.iteration()) {
                    case 0 -> """
                            b={modified in context=false:0, not null in context=initial:Choice.ONE@Method_apply_0-C;initial:Choice.THREE@Method_apply_2-C;initial:Choice.TWO@Method_apply_1-C;initial:c@Method_apply_0-E, read=true:1}, \
                            c={modified in context=false:0, not null in context=nullable:1, read=true:1}, \
                            this={modified in context=false:0, not null in context=not_null:5}\
                            """;
                    case 1 -> """
                            b={modified in context=false:0, not null in context=initial:Choice.ONE@Method_apply_0-C;initial:Choice.TWO@Method_apply_1-C;initial:c@Method_apply_0-E, read=true:1}, \
                            c={modified in context=false:0, not null in context=nullable:1, read=true:1}, \
                            this={modified in context=false:0, not null in context=not_null:5}\
                            """;
                    default -> """
                            b={modified in context=false:0, not null in context=not_null:5, read=true:1}, \
                            c={modified in context=false:0, not null in context=nullable:1, read=true:1}, \
                            this={modified in context=false:0, not null in context=not_null:5}\
                            """;
                };
                assertEquals(properties, d.statementAnalysis().propertiesFromSubAnalysersSortedToString());
            }
        };
        testClass("SwitchExpression_4", 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }
}
