
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

import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.FieldAnalyserVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.e2immu.analyser.analyser.Property.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class Test_00_Basics_10 extends CommonTestRunner {

    public Test_00_Basics_10() {
        super(false);
    }

    @Test
    public void test_10() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("Basics_10".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "string".equals(fr.fieldInfo.name)) {
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(NOT_NULL_EXPRESSION));
                }
            }
            if ("getString".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "string".equals(fr.fieldInfo.name)) {
                    assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, EXTERNAL_NOT_NULL);
                    assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, NOT_NULL_EXPRESSION);

                    String expectValue = d.iteration() == 0 ? "<f:string>" : "instance type String";
                    assertEquals(expectValue, d.currentValue().toString());
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("Basics_10".equals(d.methodInfo().name)) {
                ParameterAnalysis in = d.parameterAnalyses().get(0);
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, in.getProperty(NOT_NULL_PARAMETER));
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("string".equals(d.fieldInfo().name)) {
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV,
                        d.fieldAnalysis().getProperty(EXTERNAL_NOT_NULL));
                assertEquals("in", d.fieldAnalysis().getValue().toString());
            }
        };
        testClass("Basics_10", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }
}
