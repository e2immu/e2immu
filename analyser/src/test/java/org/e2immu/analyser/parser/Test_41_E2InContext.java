
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

package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.LinkedVariables;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.visitor.FieldAnalyserVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.TypeAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class Test_41_E2InContext extends CommonTestRunner {

    public static final String INSTANCE_TYPE_EVENTUALLY_STRING = "instance type Eventually<String>";

    public Test_41_E2InContext() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Eventually".equals(d.typeInfo().simpleName)) {
                int expectImm = d.iteration() == 0 ? Level.DELAY : MultiLevel.EVENTUALLY_E2IMMUTABLE;
                assertEquals(expectImm, d.typeAnalysis().getProperty(VariableProperty.IMMUTABLE));
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("error".equals(d.methodInfo().name)) {
                if ("eventually".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        int expectImm = d.iteration() <= 1 ? Level.DELAY : MultiLevel.EVENTUALLY_E2IMMUTABLE_BEFORE_MARK;
                        assertEquals(expectImm, d.getProperty(VariableProperty.IMMUTABLE));
                    }
                }
            }
        };

        testClass("E2InContext_0", 1, 0, new DebugConfiguration.Builder()
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_1() throws IOException {
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("eventually".equals(d.fieldInfo().name)) {
                if (d.iteration() <= 1) {
                    assertNull(d.fieldAnalysis().getEffectivelyFinalValue());
                } else {
                    assertEquals(INSTANCE_TYPE_EVENTUALLY_STRING,
                            d.fieldAnalysis().getEffectivelyFinalValue().toString());
                }
                int expectImm = d.iteration() <= 2 ? Level.DELAY : MultiLevel.EVENTUALLY_E2IMMUTABLE;
                assertEquals(expectImm, d.fieldAnalysis().getProperty(VariableProperty.EXTERNAL_IMMUTABLE));
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("getEventually".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "eventually".equals(fr.fieldInfo.name)) {
                    int expectImm = d.iteration() <= 3 ? Level.DELAY : MultiLevel.EVENTUALLY_E2IMMUTABLE;
                    assertEquals(expectImm, d.getProperty(VariableProperty.EXTERNAL_IMMUTABLE));

                    String expectValue = d.iteration() <= 2 ? "<f:eventually>" : INSTANCE_TYPE_EVENTUALLY_STRING;
                    assertEquals(expectValue, d.currentValue().toString());
                }
                if (d.variable() instanceof ReturnVariable) {
                    String expectLinked = d.iteration() <= 2 ? LinkedVariables.DELAY_STRING : "this.eventually";
                    assertEquals(expectLinked, d.variableInfo().getLinkedVariables().toString());

                    int expectExtImm = d.iteration() <= 3 ? Level.DELAY : MultiLevel.EVENTUALLY_E2IMMUTABLE;
                    assertEquals(expectExtImm, d.getProperty(VariableProperty.EXTERNAL_IMMUTABLE));

                    int expectImm = d.iteration() <= 2 ? Level.DELAY : MultiLevel.EVENTUALLY_E2IMMUTABLE;
                    assertEquals(expectImm, d.getProperty(VariableProperty.IMMUTABLE));
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("getEventually".equals(d.methodInfo().name)) {
                int expectImm = d.iteration() <= 3 ? Level.DELAY : MultiLevel.EVENTUALLY_E2IMMUTABLE;
                assertEquals(expectImm, d.methodAnalysis().getProperty(VariableProperty.IMMUTABLE));

                int expectIndependent = d.iteration() <= 3 ? Level.DELAY : MultiLevel.DEPENDENT;
                assertEquals(expectIndependent, d.methodAnalysis().getProperty(VariableProperty.INDEPENDENT));
            }
        };

        testClass("E2InContext_1", 0, 0, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test_2() throws IOException {
        testClass("E2InContext_2", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    /* small variant on 1; here the field is public */

    @Test
    public void test_3() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("getEventually".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "eventually".equals(fr.fieldInfo.name)) {
                    int expectImmInitial = d.iteration() <= 2 ? Level.DELAY : MultiLevel.EVENTUALLY_E2IMMUTABLE;
                    assertEquals(expectImmInitial, d.variableInfoContainer().getPreviousOrInitial()
                            .getProperty(VariableProperty.IMMUTABLE));

                    int expectExtImm = d.iteration() <= 2 ? Level.DELAY : MultiLevel.EVENTUALLY_E2IMMUTABLE;
                    assertEquals(expectExtImm, d.getProperty(VariableProperty.EXTERNAL_IMMUTABLE));

                    int expectImm = d.iteration() <= 2 ? Level.DELAY : MultiLevel.EVENTUALLY_E2IMMUTABLE;
                    assertEquals(expectImm, d.getProperty(VariableProperty.IMMUTABLE));

                    String expectValue = d.iteration() <= 2 ? "<f:eventually>" : INSTANCE_TYPE_EVENTUALLY_STRING;
                    assertEquals(expectValue, d.currentValue().toString());
                }
                if (d.variable() instanceof ReturnVariable) {
                    int expectExtImm = d.iteration() <= 2 ? Level.DELAY : MultiLevel.EVENTUALLY_E2IMMUTABLE;
                    assertEquals(expectExtImm, d.getProperty(VariableProperty.EXTERNAL_IMMUTABLE));

                    String expectValue = d.iteration() <= 2 ? "<f:eventually>" : "eventually";
                    assertEquals(expectValue, d.currentValue().toString());

                    int expectImm = d.iteration() <= 2 ? Level.DELAY : MultiLevel.EVENTUALLY_E2IMMUTABLE;
                    //    assertEquals(expectImm, d.getProperty(VariableProperty.IMMUTABLE));
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("getEventually".equals(d.methodInfo().name)) {
                int expectImm = d.iteration() <= 2 ? Level.DELAY : MultiLevel.EVENTUALLY_E2IMMUTABLE;
                assertEquals(expectImm, d.methodAnalysis().getProperty(VariableProperty.IMMUTABLE));
            }
        };

        testClass("E2InContext_3", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }
}
