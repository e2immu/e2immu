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

import org.e2immu.analyser.analysis.range.Range;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Test_21_Range extends CommonTestRunner {

    public Test_21_Range() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method1".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    Range range = d.statementAnalysis().rangeData().getRange();
                    assertEquals("NumericRange[startIncl=0, endExcl=10, increment=1, variableExpression=i]",
                            range.toString());
                    Expression conditions = range.conditions(d.evaluationContext());
                    assertEquals("i<=9&&i>=0", conditions.toString());
                }
                if ("0.0.0".equals(d.statementId())) {
                    assertEquals("i<=9&&i>=0", d.condition().toString());
                }
                if ("0.0.1".equals(d.statementId())) {
                    assertEquals("i<=9&&i>=0", d.condition().toString());
                    assertEquals("i<=9&&i>=0", d.absoluteState().toString());
                }
            }
            if ("method2".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    Range range = d.statementAnalysis().rangeData().getRange();
                    assertEquals("NumericRange[startIncl=0, endExcl=11, increment=1, variableExpression=i]",
                            range.toString());
                    Expression conditions = range.conditions(d.evaluationContext());
                    assertEquals("i<=10&&i>=0", conditions.toString());
                }
            }
            if ("method3".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    Range range = d.statementAnalysis().rangeData().getRange();
                    assertEquals("NumericRange[startIncl=0, endExcl=12, increment=1, variableExpression=i]",
                            range.toString());
                    Expression conditions = range.conditions(d.evaluationContext());
                    assertEquals("i<=11&&i>=0", conditions.toString());
                }
            }
            if ("method4".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    Range range = d.statementAnalysis().rangeData().getRange();
                    assertEquals("NumericRange[startIncl=13, endExcl=-1, increment=-1, variableExpression=i]",
                            range.toString());
                    Expression conditions = range.conditions(d.evaluationContext());
                    assertEquals("i<=13&&i>=0", conditions.toString());
                }
            }
            if ("method5".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    Range range = d.statementAnalysis().rangeData().getRange();
                    assertEquals("NumericRange[startIncl=0, endExcl=14, increment=4, variableExpression=i]",
                            range.toString());
                    Expression conditions = range.conditions(d.evaluationContext());
                    assertEquals("i<=13&&i>=0", conditions.toString());
                }
            }
        };
        // 2x: always false, block not executed
        testClass("Range_0", 4, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }
}
