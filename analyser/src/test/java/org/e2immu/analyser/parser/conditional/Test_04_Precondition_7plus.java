
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

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Test_04_Precondition_7plus extends CommonTestRunner {

    public Test_04_Precondition_7plus() {
        super(true);
    }

    @Test
    public void test_7() throws IOException {
        testClass("Precondition_7", 0, 0,
                new DebugConfiguration.Builder().build());
    }

    @Test
    public void test_8() throws IOException {
        testClass("Precondition_8", 0, 1,
                new DebugConfiguration.Builder()
                        .build(),
                new AnalyserConfiguration.Builder()
                        .setForceAlphabeticAnalysisInPrimaryType(true)
                        .build());
    }

    @Test
    public void test_9() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("pop".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "stack".equals(fr.fieldInfo.name)) {
                    assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("pop".equals(d.methodInfo().name)) {
                String expected = d.iteration() == 0
                        ? "Precondition[expression=!<m:isEmpty>, causes=[escape]]"
                        : "Precondition[expression=true, causes=[]]";
                assertEquals(expected, d.methodAnalysis().getPrecondition().toString());
                if (d.iteration() > 0) assertTrue(d.methodAnalysis().getPrecondition().isEmpty());
            }
        };
        testClass("Precondition_9", 0, 0,
                new DebugConfiguration.Builder()
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .build());
    }

}
