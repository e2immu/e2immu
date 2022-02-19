
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

import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Test_00_Basics_7_3 extends CommonTestRunner {

    public Test_00_Basics_7_3() {
        super(true);
    }

    // more on statement time. Inside the synchronization blocks, variable fields act as local variables:
    // their value cannot change from the outside
    @Test
    public void test() throws IOException {

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("increment3".equals(d.methodInfo().name)) {
                if ("j".equals(d.variableName())) {
                    if ("1.0.2".equals(d.statementId()) || "1.0.3".equals(d.statementId())) {
                        String expect = switch (d.iteration()) {
                            case 0 -> "<f:i>";
                            case 1 -> "<vp:i:initial:java.lang.System.out@Method_increment3_0-C;initial:this.i@Method_increment3_0-C;initial@Field_i;values:this.i@Field_i>";
                            default -> "i";
                        };
                        assertEquals(expect, d.currentValue().toString());
                    }
                }
                // "1" is the end of the synchronized block; note the special code in ConditionAndVariableInfo that
                // avoids making "1.0.3" the statement with the last value for i
                if (d.variable() instanceof FieldReference fr && "i".equals(fr.fieldInfo.name)) {
                    if ("1.0.2".equals(d.statementId()) || "1.0.3".equals(d.statementId())) {
                        String expect = switch (d.iteration()) {
                            case 0 -> "1+<f:i>";
                            case 1 -> "<wrapped:i>";
                            default -> "1+i";
                        };
                        assertEquals(expect, d.currentValue().toString());
                        String causes = switch (d.iteration()) {
                            case 0 -> "initial:java.lang.System.out@Method_increment3_0-C;initial:this.i@Method_increment3_0-C;initial@Field_i";
                            // Important that break_init_delay:this.i@Method_increment3_0 has been filtered out, in 1.0.2 and 1.0.3
                            case 1 -> "initial:java.lang.System.out@Method_increment3_0-C;initial:this.i@Method_increment3_0-C;initial@Field_i;values:this.i@Field_i";
                            default -> "";
                        };
                        assertEquals(causes, d.currentValue().causesOfDelay().toString());
                    }
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("1.0.3".equals(d.statementId())) {
                        String expect = switch (d.iteration()) {
                            case 0 -> "<f:i>==<f:i>";
                            case 1 -> "<wrapped:i>";
                            default -> "true";
                        };
                        assertEquals(expect, d.currentValue().toString());
                    }
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("increment3".equals(d.methodInfo().name)) {
                assertEquals("1", d.methodAnalysis().getLastStatement().index());
            }
        };
        testClass("Basics_7_3", 0, 0, new DebugConfiguration.Builder()
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().setForceExtraDelayForTesting(true).build());
    }
}
