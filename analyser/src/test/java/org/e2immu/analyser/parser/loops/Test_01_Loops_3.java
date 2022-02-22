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
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analysis.FlowData;
import org.e2immu.analyser.analysis.StatementAnalysis;
import org.e2immu.analyser.analysis.impl.StatementAnalysisImpl;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.Identifier;
import org.e2immu.analyser.model.expression.ArrayInitializer;
import org.e2immu.analyser.model.statement.ForEachStatement;
import org.e2immu.analyser.model.variable.VariableNature;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class Test_01_Loops_3 extends CommonTestRunner {

    public Test_01_Loops_3() {
        super(true);
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
                    if (d.statementAnalysis().statement() instanceof ForEachStatement forEachStatement) {
                        DV exec = forEachStatement.structure.statementExecution()
                                .apply(new ArrayInitializer(Identifier.generate("test"),
                                        d.context().getAnalyserContext(),
                                        List.of(), ((StatementAnalysisImpl) d.statementAnalysis()).primitives.stringParameterizedType()), d.context());
                        assertSame(FlowData.NEVER, exec);

                        StatementAnalysis firstInBlock = d.statementAnalysis().navigationData().blocks.get().get(0).orElseThrow();
                        assertEquals("1.0.0", firstInBlock.index());
                        if (d.iteration() > 0) {
                            assertTrue(firstInBlock.flowData().isUnreachable());
                            assertNotNull(d.haveError(Message.Label.EMPTY_LOOP));
                        }
                    } else fail();
                }
                if ("2".equals(d.statementId())) {
                    assertFalse(d.statementAnalysis().variableIsSet("s"));
                }
            }
        };
        // empty loop
        testClass("Loops_3", 1, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }


}
