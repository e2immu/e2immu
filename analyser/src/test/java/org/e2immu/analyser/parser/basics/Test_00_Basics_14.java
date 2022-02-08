
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
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analyser.VariableInfoContainer;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.FieldAnalyserVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.e2immu.analyser.analyser.Property.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Test_00_Basics_14 extends CommonTestRunner {

    public Test_00_Basics_14() {
        super(false);
    }

    /*
    copy of the SetOnce delay problems (20210304)
     */
    @Test
    public void test_14() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            DV cnn = d.getProperty(CONTEXT_NOT_NULL);
            DV enn = d.getProperty(EXTERNAL_NOT_NULL);
            if ("setT".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "t".equals(fr.fieldInfo.name)) {
                    if ("0.0.0".equals(d.statementId()) || "0".equals(d.statementId()) || "1".equals(d.statementId())) {
                        assertDv(d, 2, MultiLevel.NULLABLE_DV, CONTEXT_NOT_NULL);
                        assertDv(d, MultiLevel.MUTABLE_DV, CONTEXT_IMMUTABLE);
                    }
                    // now comes the assignment this.t = t;
                    if ("2".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<s:T>" : "t";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, CONTEXT_NOT_NULL);
                        assertDv(d, 1, MultiLevel.MUTABLE_DV, CONTEXT_IMMUTABLE);
                    }
                }
                if (d.variable() instanceof ParameterInfo p && "t".equals(p.name)) {
                    if ("0.0.0".equals(d.statementId())) {
                        assertDv(d, MultiLevel.NULLABLE_DV, CONTEXT_NOT_NULL);
                        assertDv(d, MultiLevel.MUTABLE_DV, CONTEXT_IMMUTABLE);
                    }
                }
            }
            if ("getT".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference t && t.fieldInfo.name.equals("t")) {
                    if ("0".equals(d.statementId())) {
                        VariableInfo initial = d.variableInfoContainer().getPreviousOrInitial();
                        assertEquals(MultiLevel.NULLABLE_DV, initial.getProperty(CONTEXT_NOT_NULL));

                        VariableInfo eval = d.variableInfoContainer().best(VariableInfoContainer.Level.EVALUATION);
                        assertEquals(MultiLevel.NULLABLE_DV, eval.getProperty(CONTEXT_NOT_NULL));
                    }

                    String expectValue = d.iteration() <= 1 ? "<f:t>" : "nullable instance type T";
                    assertEquals(expectValue, d.currentValue().toString());

                    assertDv(d, 2, MultiLevel.NULLABLE_DV, EXTERNAL_NOT_NULL);

                    if ("0.0.0".equals(d.statementId())) {
                        assertEquals(MultiLevel.NULLABLE_DV, d.getProperty(CONTEXT_NOT_NULL));
                    } else {
                        assertDv(d, 2, MultiLevel.EFFECTIVELY_NOT_NULL_DV, CONTEXT_NOT_NULL);
                    }
                }
                if ("t$0".equals(d.variableInfo().variable().simpleName())) {
                    if ("1".equals(d.statementId())) {
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, cnn);
                    }
                    assertEquals(MultiLevel.NULLABLE_DV, enn);
                }
            }
            if (d.iteration() > 1) {
                assertTrue(enn.isDone());
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("t".equals(d.fieldInfo().name)) {
                assertDv(d, 1, MultiLevel.NULLABLE_DV, EXTERNAL_NOT_NULL);
                assertEquals(DV.FALSE_DV, d.fieldAnalysis().getProperty(FINAL));
                assertEquals("<variable value>", d.fieldAnalysis().getValue().toString());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("setT".equals(d.methodInfo().name)) {
                ParameterAnalysis p0 = d.parameterAnalyses().get(0);
                // not contracted
                assertEquals(MultiLevel.NOT_CONTAINER_DV, p0.getProperty(CONTAINER));
            }
        };

        testClass("Basics_14", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }
}
