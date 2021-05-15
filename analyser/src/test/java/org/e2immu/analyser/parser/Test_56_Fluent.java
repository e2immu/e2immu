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
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.AnnotatedAPIConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.TypeAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class Test_56_Fluent extends CommonTestRunner {
    public Test_56_Fluent() {
        super(false);
    }

    /*
    for now, we raise a warning that there is a circular type dependency between a.IFluent_0 and Fluent_0
     */
    @Test
    public void test_0() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("copyOf".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    if ("0.0.0".equals(d.statementId())) {
                        assertEquals("instance", d.currentValue().toString());
                        assertTrue(d.currentValue() instanceof VariableExpression);
                    }
                    if ("1".equals(d.statementId())) {
                        String expect = d.iteration() <= 1 ? "instance instanceof Fluent_0?instance:<m:build>" :
                                "instance instanceof Fluent_0?instance:new Fluent_0(value)";
                        assertEquals(expect, d.currentValue().toString());
                        String expectLinks = d.iteration() <= 1 ? LinkedVariables.DELAY_STRING : "instance";
                        assertEquals(expectLinks, d.variableInfo().getLinkedVariables().toString());
                    }
                }
            }
            if ("from".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo i && "instance".equals(i.name)) {
                    int cm = d.getProperty(VariableProperty.CONTEXT_MODIFIED);
                    if ("0".equals(d.statementId())) {
                        assertEquals(Level.FALSE, cm);
                    }
                    if ("1".equals(d.statementId())) {
                        int expectCm = d.iteration() <= 1 ? Level.DELAY : Level.FALSE;
                        assertEquals(expectCm, cm);
                    }
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("copyOf".equals(d.methodInfo().name)) {
                // @NotModified
                int expectModified = d.iteration() <= 2 ? Level.DELAY : Level.FALSE;
                assertEquals(expectModified, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));

                if (d.iteration() <= 2) {
                    assertNull(d.methodAnalysis().getSingleReturnValue());
                } else {
                    assertEquals("instance instanceof Fluent_0?instance:new Fluent_0(value)",
                            d.methodAnalysis().getSingleReturnValue().toString());
                }

                int expectFluent = d.iteration() <= 2 ? Level.DELAY : Level.FALSE;
                assertEquals(expectFluent, d.methodAnalysis().getProperty(VariableProperty.FLUENT));

                // @NotNull
                int expectNne = d.iteration() <= 2 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                assertEquals(expectNne, d.methodAnalysis().getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                // @Independent
                int expectIndependent = d.iteration() <= 2 ? Level.DELAY : MultiLevel.INDEPENDENT;
                assertEquals(expectIndependent, d.methodAnalysis().getProperty(VariableProperty.INDEPENDENT));
            }

            if ("build".equals(d.methodInfo().name)) {
                int expectNne = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                assertEquals(expectNne, d.methodAnalysis().getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                int expectModified = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                assertEquals(expectModified, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
                int expectIndependent = d.iteration() == 0 ? Level.DELAY : MultiLevel.INDEPENDENT;
                assertEquals(expectIndependent, d.methodAnalysis().getProperty(VariableProperty.INDEPENDENT));
            }

            if ("from".equals(d.methodInfo().name)) {
                if (d.iteration() <= 1) {
                    assertNull(d.methodAnalysis().getSingleReturnValue());
                } else {
                    assertEquals("this", d.methodAnalysis().getSingleReturnValue().toString());
                }

                int expectFluent = d.iteration() <= 1 ? Level.DELAY : Level.TRUE;
                assertEquals(expectFluent, d.methodAnalysis().getProperty(VariableProperty.FLUENT));

                int expectNne = d.iteration() <= 1 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                assertEquals(expectNne, d.methodAnalysis().getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                int expectModified = d.iteration() <= 1 ? Level.DELAY : Level.TRUE;
                assertEquals(expectModified, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));

                // a fluent method is dependent
                int expectIndependent = d.iteration() <= 1 ? Level.DELAY : MultiLevel.DEPENDENT;
                assertEquals(expectIndependent, d.methodAnalysis().getProperty(VariableProperty.INDEPENDENT));
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Fluent_0".equals(d.typeInfo().simpleName)) {
                assertEquals("[]", d.typeAnalysis().getImplicitlyImmutableDataTypes().toString());
            }
            if ("org.e2immu.analyser.testexample.Fluent_0.Builder".equals(d.typeInfo().fullyQualifiedName)) {
                assertEquals("[Type org.e2immu.analyser.testexample.a.IFluent_0.Builder]",
                        d.typeAnalysis().getImplicitlyImmutableDataTypes().toString());
            }
            if ("IFluent_0".equals(d.typeInfo().simpleName)) {
                fail("Not analysed: no code");
            }
            if ("Type org.e2immu.analyser.testexample.a.IFluent_0.Builder".equals(d.typeInfo().fullyQualifiedName)) {
                fail("Not analysed: no code");
            }
        };

        testClass(List.of("a.IFluent_0", "Fluent_0"), 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .build(), new AnalyserConfiguration.Builder().build(), new AnnotatedAPIConfiguration.Builder().build());
    }

}
