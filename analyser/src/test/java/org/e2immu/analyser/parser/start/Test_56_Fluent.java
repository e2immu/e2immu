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

package org.e2immu.analyser.parser.start;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analysis.Analysis;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.AnnotatedAPIConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.expression.PropertyWrapper;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.start.testexample.Fluent_1;
import org.e2immu.analyser.parser.start.testexample.a.IFluent_1;
import org.e2immu.analyser.visitor.*;
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
                if (d.variable() instanceof ParameterInfo p && "instanceCopy".equals(p.name)) {
                    if ("0.0.0".equals(d.statementId())) {
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                        assertEquals("", d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("0".equals(d.statementId())) {
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                        assertEquals("", d.variableInfo().getLinkedVariables().toString());
                    }
                    // calls from, which is CM false in iteration 2
                    if ("1".equals(d.statementId())) {
                        assertDv(d, 13, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                // equals is evaluated after copyOf, so CM in the parameter of equals is only visible in iteration 3
                if (d.variable() instanceof ReturnVariable) {
                    if ("0.0.0".equals(d.statementId())) {
                        assertCurrentValue(d, 27, "instanceCopy/*(Fluent_0)*/");
                        assertTrue(d.currentValue() instanceof PropertyWrapper, "Have " + d.currentValue().getClass());
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(Property.NOT_NULL_EXPRESSION));
                    }
                    if ("1".equals(d.statementId())) {
                        String value = "instanceCopy instanceof Fluent_0?instanceCopy/*(Fluent_0)*/:new Fluent_0(`instance type Builder.value`)";
                        assertCurrentValue(d, 27, value);

                        String expectLinks = d.iteration() < 27 ? "instanceCopy:-1" : "instanceCopy:1";
                        assertEquals(expectLinks, d.variableInfo().getLinkedVariables().toString());

                        // computation of NNE is important here!
                        assertDv(d, 27, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                    }
                }
            }
            if ("from".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo i && "instanceFrom".equals(i.name)) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("1".equals(d.statementId())) {
                        assertDv(d, 7, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if (d.variable() instanceof This) {
                    if ("1".equals(d.statementId())) {
                        assertDv(d, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("2".equals(d.statementId())) {
                        String linked = d.iteration() == 0 ? "instanceFrom:-1" : "";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("2".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "instanceFrom:-1,this:-1" : "this:1";
                        assertEquals(expected, d.variableInfo().getLinkedVariables().toString());
                    }
                }
            }
            if ("equals".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo p && "another".equals(p.name)) {
                    if ("1".equals(d.statementId())) {
                        assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("0".equals(d.statementId())) {
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }
            if ("equalTo".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo p && "another2".equals(p.name)) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("equals".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 2, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
            }

            if ("copyOf".equals(d.methodInfo().name)) {
                // @NotModified
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);

                String expect = d.iteration() < 27 ? "<m:copyOf>"
                        : "/*inline copyOf*/instanceCopy instanceof Fluent_0?instanceCopy/*(Fluent_0)*/:new Fluent_0(`instance type Builder.value`)";
                assertEquals(expect, d.methodAnalysis().getSingleReturnValue().toString());


                assertDv(d, DV.FALSE_DV, Property.FLUENT);
                assertDv(d, 27, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                assertDv(d, 2, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
            }

            if ("build".equals(d.methodInfo().name)) {
                assertDv(d, 27, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
                assertDv(d, 2, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
            }

            if ("from".equals(d.methodInfo().name)) {

                assertDv(d.p(0), 8, DV.FALSE_DV, Property.MODIFIED_VARIABLE);

                String expect = d.iteration() <= 8 ? "<m:from>" : "this/*(Builder)*/";
                assertEquals(expect, d.methodAnalysis().getSingleReturnValue().toString());

                assertDv(d, 9, DV.TRUE_DV, Property.FLUENT);
                assertDv(d, 9, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                // 'this' is always independent, there's no new information
                assertDv(d, 7, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);

                assertDv(d, DV.TRUE_DV, Property.MODIFIED_METHOD);
            }

            if ("value".equals(d.methodInfo().name)) {
                if ("Builder".equals(d.methodInfo().typeInfo.simpleName)) {
                    assertDv(d, DV.TRUE_DV, Property.MODIFIED_METHOD);
                } else {
                    assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
                }
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Fluent_0".equals(d.typeInfo().simpleName)) {

                assertTrue(d.typeAnalysis().getHiddenContentTypes().isEmpty());

                assertFalse(d.typeInfo().typePropertiesAreContracted());
            }
            if ("IFluent_0".equals(d.typeInfo().simpleName)) {
                // property has been contracted in the code: there is no computing
                assertEquals(MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, d.typeAnalysis().getProperty(Property.IMMUTABLE));
                assertTrue(d.typeInfo().typePropertiesAreContracted());
            }
            if ("Builder".equals(d.typeInfo().simpleName)) {
                if ("Fluent_0".equals(d.typeInfo().packageNameOrEnclosingType.getRight().simpleName)) {
                    assertDv(d, 1, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("value".equals(d.fieldInfo().name)) {
                if ("Fluent_0".equals(d.fieldInfo().owner.simpleName)) {
                    assertDv(d, DV.TRUE_DV, Property.FINAL);
                } else if ("Builder".equals(d.fieldInfo().owner.simpleName)) {
                    assertDv(d, DV.FALSE_DV, Property.FINAL);
                }
            }
        };

        testClass(List.of("a.IFluent_0", "Fluent_0"), 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build(), new AnalyserConfiguration.Builder().build(), new AnnotatedAPIConfiguration.Builder().build());
    }

    @Test
    public void test_1() throws IOException {
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("IFluent_1".equals(d.typeInfo().simpleName)) {
                assertFalse(d.typeInfo().typePropertiesAreContracted()); // they are aggregated!
                assertEquals("", d.typeAnalysis().hiddenContentDelays().toString());
                assertTrue(d.typeAnalysis().getHiddenContentTypes().isEmpty());

                assertTrue(d.typeInfo().typeResolution.get().hasOneKnownGeneratedImplementation());
                assertDv(d, 1, MultiLevel.EFFECTIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
            }
            if ("Fluent_1".equals(d.typeInfo().simpleName)) {
                assertDv(d, 1, MultiLevel.EFFECTIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
                assertFalse(d.typeInfo().typePropertiesAreContracted());
                assertEquals("", d.typeAnalysis().hiddenContentDelays().toString());
                assertTrue(d.typeAnalysis().getHiddenContentTypes().isEmpty());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("value".equals(d.methodInfo().name) && "IFluent_1".equals(d.methodInfo().typeInfo.simpleName)) {
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
                assertDv(d, DV.FALSE_DV, Property.FLUENT);
                assertDv(d, DV.FALSE_DV, Property.IDENTITY);
                assertDv(d, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
                assertDv(d, MultiLevel.EFFECTIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
                assertDv(d, 1, MultiLevel.NOT_IGNORE_MODS_DV, Property.IGNORE_MODIFICATIONS);
            }
            if ("identity".equals(d.methodInfo().name)) {
                assertEquals(DV.FALSE_DV, d.methodAnalysis().getProperty(Property.MODIFIED_METHOD));
                assertDv(d, 16, DV.TRUE_DV, Property.IDENTITY);
            }
            if ("value".equals(d.methodInfo().name) && "Fluent_1".equals(d.methodInfo().typeInfo.simpleName)) {
                assertEquals(DV.FALSE_DV, d.methodAnalysis().getProperty(Property.MODIFIED_METHOD));
            }
            if ("value".equals(d.methodInfo().name) && "IFluent_1".equals(d.methodInfo().typeInfo.simpleName)) {
                // via aggregation
                assertEquals(DV.FALSE_DV, d.methodAnalysis().getProperty(Property.MODIFIED_METHOD));
            }

            if ("from".equals(d.methodInfo().name)) {
                assertDv(d, DV.TRUE_DV, Property.MODIFIED_METHOD);
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("from".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertTrue(d.statementAnalysis().methodLevelData().combinedPreconditionIsFinal());
                    assertTrue(d.statementAnalysis().stateData().preconditionIsFinal());
                }
                if ("1".equals(d.statementId())) {
                    // STEP 6: evaluation renders a delayed precondition: hardcoded in AggregatingMethodAnalyser
                    assertTrue(d.statementAnalysis().stateData().preconditionIsFinal());

                    // STEP 5: check preconditionIsDelayed in previous statement, that's OK
                    assertTrue(d.conditionManagerForNextStatement().precondition().expression().isDone());
                    // STEP 5bis: combined precondition never becomes final
                    assertTrue(d.statementAnalysis().methodLevelData().combinedPreconditionIsFinal());
                }
                if ("2".equals(d.statementId())) {
                    // STEP 2 parameter 'instance'
                    assertEquals(d.iteration() >= 7, d.statementAnalysis().methodLevelData().linksHaveBeenEstablished());
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("from".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo p && "instance".equals(p.name)) {
                    if ("0".equals(d.statementId())) {
                        assertEquals(DV.FALSE_DV, d.getProperty(Property.CONTEXT_MODIFIED));
                        assertEquals("nullable instance type IFluent_1/*@Identity*/", d.currentValue().toString());
                        assertTrue(d.variableInfo().getLinkedVariables().isEmpty());
                    }
                    if ("2".equals(d.statementId())) {
                        assertDv(d, 7, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }
        };

        TypeContext typeContext = testClass(List.of("a.IFluent_1", "Fluent_1"),
                List.of("jmods/java.compiler.jmod"),
                0, 1, new DebugConfiguration.Builder()
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .build(), new AnalyserConfiguration.Builder().build(),
                new AnnotatedAPIConfiguration.Builder().build());
        TypeInfo iFluent1 = typeContext.typeMap.get(IFluent_1.class);
        MethodInfo value = iFluent1.findUniqueMethod("value", 0);
        TypeInfo implementation = iFluent1.typeResolution.get().generatedImplementation();
        TypeInfo fluent1 = typeContext.typeMap.get(Fluent_1.class);
        assertSame(fluent1, implementation, "Generated implementation");

        MethodAnalysis valueAnalysis = value.methodAnalysis.get();
        assertSame(Analysis.AnalysisMode.AGGREGATED, valueAnalysis.analysisMode());
    }


    @Test
    public void test_2() throws IOException {
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("IFluent_2".equals(d.typeInfo().simpleName)) {
                assertTrue(d.typeInfo().typeResolution.get().hasOneKnownGeneratedImplementation());
                assertSame(Analysis.AnalysisMode.AGGREGATED, d.typeAnalysis().analysisMode());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("value".equals(d.methodInfo().name) && "IFluent_2".equals(d.methodInfo().typeInfo.simpleName)) {
                assertSame(Analysis.AnalysisMode.AGGREGATED, d.methodAnalysis().analysisMode());
            }
        };

        testClass(List.of("a.IFluent_2", "Fluent_2"),
                List.of("jmods/java.compiler.jmod"),
                0, 2, new DebugConfiguration.Builder()
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                        .build(), new AnalyserConfiguration.Builder().build(), new AnnotatedAPIConfiguration.Builder().build());
    }

    @Test
    public void test_3() throws IOException {
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("IFluent_3".equals(d.typeInfo().simpleName)) {
                assertTrue(d.typeInfo().typePropertiesAreContracted());
            }
        };
        testClass(List.of("a.IFluent_3", "Fluent_3"),
                List.of("jmods/java.compiler.jmod"),
                0, 1, new DebugConfiguration.Builder()
                        .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                        .build(), new AnalyserConfiguration.Builder().build(), new AnnotatedAPIConfiguration.Builder().build());
    }
}
