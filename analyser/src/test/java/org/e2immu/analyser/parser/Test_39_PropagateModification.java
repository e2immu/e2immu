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

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.testexample.PropagateModification_0;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Test_39_PropagateModification extends CommonTestRunner {
    public Test_39_PropagateModification() {
        super(false);
    }

    @Test
    public void test_0() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("forEach".equals(d.methodInfo().name) && "PropagateModification_0".equals(d.methodInfo().typeInfo.simpleName)) {
                ParameterAnalysis p0 = d.parameterAnalyses().get(0);
                int expectCm = d.iteration() <= 1 ? Level.DELAY : Level.FALSE;
                assertEquals(expectCm, p0.getProperty(VariableProperty.CONTEXT_MODIFIED));
                assertEquals(expectCm, p0.getProperty(VariableProperty.MODIFIED_VARIABLE));
                int expectPm = d.iteration() <= 1 ? Level.DELAY : Level.TRUE;
                assertEquals(expectPm, p0.getProperty(VariableProperty.PROPAGATE_MODIFICATION));
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("forEach".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo p && "myConsumer".equals(p.name)) {
                    int expectCm = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                    assertEquals(expectCm, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                    int expectPm = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                    assertEquals(expectPm, d.getProperty(VariableProperty.PROPAGATE_MODIFICATION));
                }
            }
        };

        TypeContext typeContext = testClass("PropagateModification_0", 0, 0,
                new DebugConfiguration.Builder()
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .build());
        TypeInfo myConsumer = typeContext.getFullyQualified(
                PropagateModification_0.class.getCanonicalName() + ".MyConsumer", true);
        MethodInfo methodInfo = myConsumer.findUniqueMethod("accept", 1);
        assertTrue(methodInfo.isAbstract());
        assertEquals(Level.DELAY, methodInfo.methodAnalysis.get().getProperty(VariableProperty.MODIFIED_METHOD));
        ParameterAnalysis p0 = methodInfo.methodInspection.get().getParameters().get(0).parameterAnalysis.get();
        assertEquals(Level.DELAY, p0.getProperty(VariableProperty.MODIFIED_VARIABLE));
    }

    @Test
    public void test_1() throws IOException {

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("forEach".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo p && "myConsumer".equals(p.name)) {
                    if ("0".equals(d.statementId())) {
                        int expectCm = d.iteration() <= 1 ? Level.DELAY : Level.FALSE;
                        assertEquals(expectCm, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                        int expectPm = d.iteration() <= 1 ? Level.DELAY : Level.FALSE;
                        assertEquals(expectPm, d.getProperty(VariableProperty.PROPAGATE_MODIFICATION));
                    }
                    if ("1".equals(d.statementId())) {
                        int expectCm = d.iteration() <= 1 ? Level.DELAY : Level.FALSE;
                        assertEquals(expectCm, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                        int expectPm = d.iteration() <= 1 ? Level.DELAY : Level.TRUE;
                        assertEquals(expectPm, d.getProperty(VariableProperty.PROPAGATE_MODIFICATION));
                    }
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("forEach".equals(d.methodInfo().name)) {
                ParameterAnalysis myConsumer = d.parameterAnalyses().get(0);
                int expectMv = d.iteration() <= 1 ? Level.DELAY : Level.FALSE;
                assertEquals(expectMv, myConsumer.getProperty(VariableProperty.MODIFIED_VARIABLE));
            }
        };

        // warning: typical System.out potential null pointer exception, no Annotated APIs
        testClass("PropagateModification_1", 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test_2() throws IOException {

        // warning: typical System.out potential null pointer exception, no Annotated APIs
        testClass("PropagateModification_2", 0, 1, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_3() throws IOException {
        testClass("PropagateModification_3", 0, 0, new DebugConfiguration.Builder()
                .build());
    }
}
