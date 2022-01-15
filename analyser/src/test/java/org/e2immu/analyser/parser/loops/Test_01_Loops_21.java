
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

import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.variable.VariableNature;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class Test_01_Loops_21 extends CommonTestRunner {


    public Test_01_Loops_21() {
        super(true);
    }

    @Test
    public void test() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("array".equals(d.variableName())) {
                    assertEquals("Type java.lang.String[][]", d.currentValue().returnType().toString());
                }
                if ("inner".equals(d.variableName())) {
                    assertTrue(d.statementId().startsWith("2"), "Known in statement " + d.statementId());
                    if (d.statementId().startsWith("2.0.1.0")) {
                        if (d.variableInfoContainer().variableNature() instanceof VariableNature.VariableDefinedOutsideLoop outsideLoop) {
                            assertEquals("2.0.1", outsideLoop.statementIndex());
                        } else fail();
                    }
                }
                if ("outer".equals(d.variableName())) {
                    if (d.statementId().startsWith("2.0.1.0")) {
                        if (d.variableInfoContainer().variableNature() instanceof VariableNature.VariableDefinedOutsideLoop outsideLoop) {
                            assertEquals("2.0.1", outsideLoop.statementIndex());
                        } else fail();
                    } else if (d.statementId().startsWith("2.0")) {
                        if (d.variableInfoContainer().variableNature() instanceof VariableNature.VariableDefinedOutsideLoop outsideLoop) {
                            assertEquals("2", outsideLoop.statementIndex());
                        } else fail();
                    } else {
                        if (d.variableInfoContainer().variableNature() instanceof VariableNature.NormalLocalVariable normalLocalVariable) {
                            assertEquals("", normalLocalVariable.parentBlockIndex);
                        } else fail();
                    }
                }
                if ("outerMod".equals(d.variableName())) {
                    assertTrue(d.statementId().startsWith("2.0.1"), "Known in statement " + d.statementId());
                }
            }
        };
        testClass("Loops_21", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

}
