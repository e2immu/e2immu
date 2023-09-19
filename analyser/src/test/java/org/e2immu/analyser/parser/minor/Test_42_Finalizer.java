
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

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.FieldAnalyserVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.TypeAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Test_42_Finalizer extends CommonTestRunner {

    public Test_42_Finalizer() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Finalizer_0".equals(d.typeInfo().simpleName)) {
                assertEquals(DV.TRUE_DV, d.typeAnalysis().getProperty(Property.FINALIZER));
                assertDv(d, 1, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("done".equals(d.methodInfo().name)) {
                assertEquals(DV.TRUE_DV, d.methodAnalysis().getProperty(Property.FINALIZER));
            }
            if ("set".equals(d.methodInfo().name)) {
                assertEquals("this", d.methodAnalysis().getSingleReturnValue().toString());
                assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("set".equals(d.methodInfo().name)) {
                if (d.variable() instanceof This) {
                    if ("2".equals(d.statementId())) {
                        assertDv(d, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
                        assertDv(d, 2, MultiLevel.MUTABLE_DV, Property.EXTERNAL_IMMUTABLE);
                    }
                }
            }
            if ("testLinking".equals(d.methodInfo().name)) {
                if ("ff".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("f/*@NotNull*/", d.currentValue().toString());
                        assertEquals("f:1", d.variableInfo().getLinkedVariables().toString());
                    }
                }
            }
        };

        testClass("Finalizer_0", 1, 0, new DebugConfiguration.Builder()
         //       .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
          //      .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
           //     .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test_1() throws IOException {
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("eventuallyFinal".equals(d.fieldInfo().name)) {
                assertDv(d, MultiLevel.EVENTUALLY_IMMUTABLE_DV, Property.EXTERNAL_IMMUTABLE);
                assertDv(d, 2, DV.TRUE_DV, Property.BEFORE_MARK);
            }
        };
        testClass("Finalizer_1", 0, 2, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }

    @Test
    public void test_2() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("done".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "eventuallyFinal".equals(fr.fieldInfo.name)) {
                    if ("0".equals(d.statementId()) || "1".equals(d.statementId())) {
                        assertDv(d, 1, MultiLevel.EVENTUALLY_IMMUTABLE_BEFORE_MARK_DV, Property.CONTEXT_IMMUTABLE);
                    }
                }
            }
        };
        testClass("Finalizer_2", 0, 2, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }
    // TODO Finalizer_3
}
