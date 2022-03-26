
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
import org.e2immu.analyser.analysis.impl.FieldAnalysisImpl;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.FieldAnalyserVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.e2immu.analyser.analyser.Property.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Test_00_Basics_7_1 extends CommonTestRunner {

    public Test_00_Basics_7_1() {
        super(true);
    }

    // more on statement time. Inside the synchronization blocks, variable fields act as local variables:
    // their value cannot change from the outside
    @Test
    public void test() throws IOException {
        final String I = "org.e2immu.analyser.parser.basics.testexample.Basics_7.i";


        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("increment".equals(d.methodInfo().name)) {
                if (I.equals(d.variableName())) {
                    if ("2".equals(d.statementId()) && d.iteration() > 0) {
                        assertEquals("i+q", d.currentValue().toString());
                    }
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("increment".equals(d.methodInfo().name)) {
                assertTrue(d.methodInfo().isSynchronized());
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("i".equals(d.fieldInfo().name)) {
                assertEquals("<variable value>", d.fieldAnalysis().getValue().toString());
                assertEquals(DV.FALSE_DV, d.fieldAnalysis().getProperty(FINAL));
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.fieldAnalysis().getProperty(EXTERNAL_NOT_NULL));
                String sortedValues = d.iteration() == 0
                        ? "initial:System.out@Method_increment_1-C;initial:q@Method_increment_1-E;initial:this.i@Method_increment_0-C;initial@Field_i;values:this.i@Field_i"
                        : "0,instance type int";
                assertEquals(sortedValues, ((FieldAnalysisImpl.Builder) (d.fieldAnalysis())).sortedValuesString());
                assertDv(d, 1, MultiLevel.NOT_IGNORE_MODS_DV, EXTERNAL_IGNORE_MODIFICATIONS);
            }
        };

        testClass("Basics_7_1", 0, 0, new DebugConfiguration.Builder()
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().setForceExtraDelayForTesting(true).build());
    }
}
