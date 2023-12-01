
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

package org.e2immu.analyser.parser.basics;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analyser.nonanalyserimpl.VariableInfoImpl;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.EvaluationResultVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Test_00_Basics_28 extends CommonTestRunner {

    public Test_00_Basics_28() {
        super(true);
    }

    /*
    FIXME Runs green because we have temporarily commented out modificationTime as part of MethodCall.equals
     */
    @Test
    public void test() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("same3".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    Variable builder = findBuilder(d);
                    assertEquals(0, d.evaluationResult().changeData().get(builder).modificationTimeIncrement());
                }
                if ("1.0.0".equals(d.statementId())) {
                    Variable builder = findBuilder(d);
                    assertEquals(1, d.evaluationResult().changeData().get(builder).modificationTimeIncrement());
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("same3".equals(d.methodInfo().name)) {
                if ("builder".equals(d.variableName())) {
                    int modTime = d.variableInfo().getModificationTimeOrNegative();
                    if ("0".equals(d.statementId())) {
                        assertEquals(0, d.variableInfo().getModificationTimeOrNegative());
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                        assertEquals("instance 0 type Builder", d.currentValue().toString());
                    }
                    if ("1.0.0".equals(d.statementId())) {
                        assertDv(d, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                        int expect = d.iteration() == 0 ? VariableInfoImpl.NO_MODIFICATION_TIME : 1;
                        assertEquals(expect, modTime);
                        assertEquals("instance 1.0.0 type Builder", d.currentValue().toString());
                    }
                    if ("1.1.0".equals(d.statementId())) {
                        assertDv(d, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                        assertEquals(1, modTime);
                    }
                    if ("1".equals(d.statementId())) {
                        assertDv(d, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                        int expect = d.iteration() == 0 ? VariableInfoImpl.NO_MODIFICATION_TIME : 1;
                        assertEquals(expect, modTime);
                    }
                    if ("3".equals(d.statementId()) || "4".equals(d.statementId())) {
                        assertDv(d, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                        int expect = d.iteration() == 0 ? VariableInfoImpl.NO_MODIFICATION_TIME : 3;
                        assertEquals(expect, modTime);
                    }
                }
            }
        };
        testClass("Basics_28", 0, 0, new DebugConfiguration.Builder()
                        .addEvaluationResultVisitor(evaluationResultVisitor)
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().build());
    }

    private static Variable findBuilder(EvaluationResultVisitor.Data d) {
        return d.evaluationResult().changeData().keySet().stream()
                .filter(v -> "builder".equals(v.simpleName()))
                .findFirst().orElseThrow();
    }

}
