
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
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.e2immu.analyser.analyser.Property.EXTERNAL_NOT_NULL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Test_00_Basics_4 extends CommonTestRunner {
    public Test_00_Basics_4() {
        super(true);
    }

    // i = i + 1 on a field
    @Test
    public void test4() throws IOException {
        final String TYPE = "org.e2immu.analyser.parser.basics.testexample.Basics_4";
        final String I = TYPE + ".i";

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("increment".equals(d.methodInfo().name) && "0".equals(d.statementId())) {
                if (I.equals(d.variableName())) {
                    String expect = switch (d.iteration()) {
                        case 0 -> "1+<f:i>";
                        case 1 -> "<wrapped:i>";
                        default -> "1+i$0";
                    };
                    assertEquals(expect, d.currentValue().toString());
                    assertTrue(d.variableInfo().getLinkedVariables().isEmpty());
                }
            }
            if ("getI".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "i".equals(fr.fieldInfo.name)) {
                    assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, EXTERNAL_NOT_NULL);
                }
                if (d.variable() instanceof ReturnVariable) {
                    String expect = switch (d.iteration()) {
                        case 0 -> "<f:i>";
                        case 1 -> "<vp:i:initial:this.i@Method_increment_0-C;values:this.i@Field_i>";
                        default -> "i$0";
                    };
                    assertEquals(expect, d.currentValue().toString());
                    assertEquals("this.i:0", d.variableInfo().getLinkedVariables().toString());
                    assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, EXTERNAL_NOT_NULL);
                }
            }
        };
        testClass("Basics_4", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build(),
                new AnalyserConfiguration.Builder().setForceExtraDelayForTesting(true).build());
    }
}
