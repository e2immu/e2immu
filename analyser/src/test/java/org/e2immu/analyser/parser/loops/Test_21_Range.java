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

import org.e2immu.analyser.analyser.CauseOfDelay;
import org.e2immu.analyser.analyser.CausesOfDelay;
import org.e2immu.analyser.analysis.range.Range;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class Test_21_Range extends CommonTestRunner {

    public Test_21_Range() {
        super(true);
    }

    private Range checkRange(StatementAnalyserVisitor.Data d, String rangeExpected, String conditionExpected) {
        if (d.iteration() == 0) {
            CausesOfDelay causes = d.statementAnalysis().rangeData().getRange().causesOfDelay();
            assertTrue(causes.isDelayed());
            assertEquals(CauseOfDelay.Cause.WAIT_FOR_ASSIGNMENT, causes.causesStream().findFirst().orElseThrow().cause());
            return null;
        }
        Range range = d.statementAnalysis().rangeData().getRange();
        assertEquals(rangeExpected, range.toString());
        Expression conditions = range.conditions(d.evaluationContext());
        assertEquals(conditionExpected, conditions.toString());
        return range;
    }

    @Test
    public void test_0() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method1".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    checkRange(d, "NumericRange[startIncl=0, endExcl=10, increment=1, variableExpression=i]",
                            "i<=9&&i>=0");
                }
                if ("0.0.0".equals(d.statementId())) {
                    if (d.iteration() == 0) {
                        assertTrue(d.condition().isDelayed());
                    } else {
                        assertEquals("i<=9&&i>=0", d.condition().toString());
                    }
                }
                if ("0.0.1".equals(d.statementId())) {
                    if (d.iteration() == 0) {
                        assertTrue(d.condition().isDelayed());
                    } else {
                        assertEquals("i<=9&&i>=0", d.condition().toString());
                        assertEquals("i<=9&&i>=0", d.absoluteState().toString());
                    }
                }
            }
            if ("method2".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    checkRange(d, "NumericRange[startIncl=0, endExcl=11, increment=1, variableExpression=i]",
                            "i<=10&&i>=0");
                }
            }
            if ("method3".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    checkRange(d, "NumericRange[startIncl=0, endExcl=12, increment=1, variableExpression=i]",
                            "i<=11&&i>=0");
                }
            }
            if ("method4".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    checkRange(d, "NumericRange[startIncl=13, endExcl=-1, increment=-1, variableExpression=i]",
                            "i<=13&&i>=0");
                }
            }
            if ("method5".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    checkRange(d, "NumericRange[startIncl=0, endExcl=14, increment=4, variableExpression=i]",
                            "0==i%4&&i<=13&&i>=0");
                }
            }
            if ("method6".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    checkRange(d, "NumericRange[startIncl=11, endExcl=0, increment=-2, variableExpression=i]",
                            "1==i%2&&i>=1&&i<=11");
                }
            }
        };
        // 3x: always false, block not executed
        testClass("Range_0", 6, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

    @Test
    public void test_1() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method1".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    checkRange(d, "EMPTY", "false");
                }
            }
            if ("method2".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    checkRange(d, "EMPTY", "false");
                }
            }
            if ("method3".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    Range range = checkRange(d, "NumericRange[startIncl=1, endExcl=10, increment=20, variableExpression=i]", "1==i");
                    if (d.iteration() > 0) {
                        assertNotNull(range);
                        assertEquals(1, range.loopCount());
                    }
                }
            }
            if ("method4".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    checkRange(d, "INFINITE", "true");
                }
            }
            if ("method5".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    checkRange(d, "INFINITE", "true");
                }
            }
            if ("method6".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    checkRange(d, "INFINITE", "true");
                }
            }
        };
        // EMPTY -> error; INFINITE + once -> warning
        testClass("Range_1", 2, 4, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

    @Test
    public void test_2() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method1".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    checkRange(d, "NO RANGE", "true");
                }
            }
        };
        testClass("Range_2", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }


    @Test
    public void test_3() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method1".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    checkRange(d, "NumericRange[startIncl=0, endExcl=10, increment=1, variableExpression=i$1]",
                            "i$1<=9&&i$1>=0");
                }
                if ("1.0.0".equals(d.statementId())) {
                    if (d.iteration() == 0) {
                        assertTrue(d.condition().isDelayed());
                    } else {
                        assertEquals("i$1<=9&&i$1>=0", d.condition().toString());
                    }
                }
                if ("1.0.1".equals(d.statementId())) {
                    if (d.iteration() == 0) {
                        assertTrue(d.condition().isDelayed());
                    } else {
                        assertEquals("i$1<=9&&i$1>=0", d.condition().toString());
                        assertEquals("i$1<=9&&i$1>=0", d.absoluteState().toString());
                    }
                }
            }
            if ("method2".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    checkRange(d, "NumericRange[startIncl=0, endExcl=11, increment=1, variableExpression=i$1]",
                            "i$1<=10&&i$1>=0");
                    if (d.iteration() > 0) {
                        assertNotNull(d.haveError(Message.Label.USELESS_ASSIGNMENT));
                    }
                }
            }
            if ("method3".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    checkRange(d, "NumericRange[startIncl=0, endExcl=12, increment=1, variableExpression=i$1]",
                            "i$1<=11&&i$1>=0");
                }
                if ("2".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? "<s:boolean>" : "12==i";
//                    assertEquals(expect, d.state().toString());
                }
            }
            if ("method4".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    checkRange(d, "NumericRange[startIncl=13, endExcl=-1, increment=-1, variableExpression=i$1]",
                            "i$1<=13&&i$1>=0");
                }
            }
            if ("method5".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    checkRange(d, "NumericRange[startIncl=0, endExcl=14, increment=4, variableExpression=i$1]",
                            "0==i$1%4&&i$1<=13&&i$1>=0");
                }
            }
            if ("method6".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    checkRange(d, "NumericRange[startIncl=11, endExcl=0, increment=-2, variableExpression=i$1]",
                            "1==i$1%2&&i$1>=1&&i$1<=11");
                }
            }
        };
        // 3x: always false, block not executed; 1x useless assignment
        testClass("Range_3", 7, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }
}
