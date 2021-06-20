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

import org.e2immu.analyser.analyser.FieldAnalysisImpl;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Test_60_StaticSideEffects extends CommonTestRunner {

    // AtomicInteger, System.out, ...
    public Test_60_StaticSideEffects() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        testClass("StaticSideEffects_0", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_1() throws IOException {

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("StaticSideEffects_1".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    String expected = d.iteration() == 0 ? "null==<f:counter>" : "";
                    assertEquals(expected, d.evaluationResult().getExpression().toString());
                }
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("StaticSideEffects_1".equals(d.typeInfo().simpleName)) {
                assertEquals("[Type param K]", d.typeAnalysis().getImplicitlyImmutableDataTypes().toString());
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("StaticSideEffects_1".equals(d.methodInfo().name)) {
                if ("1.0.0".equals(d.statementId())) {
                    String expected = d.iteration() == 0 ? "null==<f:counter>" : "";
                    assertEquals(expected, d.condition().toString());
                }
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("StaticSideEffects_1".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "counter".equals(fr.fieldInfo.name)) {
                    if ("1".equals(d.statementId())) {
                        String expected = d.iteration() == 0
                                ? "null==<f:counter>?<s:AtomicInteger>:<f:counter>" : "";
                        assertEquals(expected, d.currentValue().toString());

                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                    }
                    if ("1.0.0".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<s:AtomicInteger>" : "";
                        assertEquals(expected, d.currentValue().toString());

                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                    }
                }
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("counter".equals(d.fieldInfo().name)) {
                assertEquals(Level.FALSE, d.fieldAnalysis().getProperty(VariableProperty.FINAL));

                int expectEnn = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
                assertEquals(expectEnn, d.fieldAnalysis().getProperty(VariableProperty.EXTERNAL_NOT_NULL));

                String expected = "[null, null==<f:counter>?<s:AtomicInteger>:<f:counter>]";
                assertEquals(expected, ((FieldAnalysisImpl.Builder) d.fieldAnalysis()).getValues().stream()
                        .map(FieldAnalysisImpl.ValueAndPropertyProxy::getValue).sorted().toList().toString());
            }
        };

        testClass("StaticSideEffects_1", 0, 0, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build());
    }

    @Test
    public void test_2() throws IOException {
        testClass("StaticSideEffects_2", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_3() throws IOException {
        testClass("StaticSideEffects_3", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_4() throws IOException {

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("counter".equals(d.fieldInfo().name)) {
                assertEquals(Level.TRUE, d.fieldAnalysis().getProperty(VariableProperty.IGNORE_MODIFICATIONS));
                int expectMom = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                assertEquals(expectMom, d.fieldAnalysis().getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD));
            }
        };

        testClass("StaticSideEffects_4", 0, 0, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }
}
