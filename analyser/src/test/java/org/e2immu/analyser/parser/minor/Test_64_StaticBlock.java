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

package org.e2immu.analyser.parser.minor;

import org.e2immu.analyser.analyser.Stage;
import org.e2immu.analyser.analysis.impl.FieldAnalysisImpl;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.FieldAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Test_64_StaticBlock extends CommonTestRunner {

    /*
    Static blocks are executed sequentially; their code follows.
    Fields act as local variables (or at least, time does not increase when in a sync block,
    so that the values they have remain stable.)
     */
    public Test_64_StaticBlock() {
        super(true);
    }

    // a little too complicated for initial debugging, use test 1
    @Test
    public void test_0() throws IOException {
        testClass("StaticBlock_0", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    // basics
    @Test
    public void test_1() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("$staticBlock$0".equals(d.methodInfo().name)) {
                assertEquals("StaticBlock_1", d.methodInfo().typeInfo.simpleName);
                if (d.variable() instanceof FieldReference fr && "map".equals(fr.fieldInfo.name)) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("new HashMap<>()/*AnnotatedAPI.isKnown(true)&&0==this.size()*/",
                                d.currentValue().toString());
                        assertTrue(d.variableInfoContainer().hasEvaluation());
                    }
                    if ("1".equals(d.statementId())) {
                        String expect = "instance type HashMap<String,String>";
                        assertEquals(expect, d.currentValue().toString());
                    }
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("$staticBlock$0".equals(d.methodInfo().name)) {
                assertEquals("StaticBlock_1", d.methodInfo().typeInfo.simpleName);

                // we're in a sync block, statement time does not increase: variable fields do not lose their value
                assertTrue(d.statementAnalysis().inSyncBlock());
                assertEquals(0, d.statementAnalysis().statementTime(Stage.INITIAL));
                assertEquals(0, d.statementAnalysis().statementTime(Stage.EVALUATION));
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("map".equals(d.fieldInfo().name)) {
                String expect = d.iteration() == 0 ? "<f:map>" : "instance type HashMap<String,String>";
                assertEquals(expect, d.fieldAnalysis().getValue().toString());
            }
        };
        testClass("StaticBlock_1", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }

    // @Variable, assignment in constructor
    @Test
    public void test_2() throws IOException {
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("map".equals(d.fieldInfo().name)) {
                if (d.iteration() > 0) {
                    assertEquals(2, ((FieldAnalysisImpl.Builder) d.fieldAnalysis()).getValues().size());
                }
            }
        };

        testClass("StaticBlock_2", 0, 1, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }

    // @Variable, assignment in sub
    // potential null pointer
    @Test
    public void test_3() throws IOException {
        testClass("StaticBlock_3", 0, 1, new DebugConfiguration.Builder()
                .build());
    }

    // @Modified, modification in sub
    @Test
    public void test_4() throws IOException {
        testClass("StaticBlock_4", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

}
