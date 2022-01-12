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

package org.e2immu.analyser.parser.failing;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analysis.Analysis;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.AnnotatedAPIConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.PropertyWrapper;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.failing.testexample.Fluent_1;
import org.e2immu.analyser.parser.failing.testexample.a.IFluent_1;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.e2immu.analyser.visitor.TypeAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class Test_56_Fluent extends CommonTestRunner {

    public static final String INSTANCE_TYPE_BUILDER_BUILD =
            "!(instance instanceof Fluent_0)||null==instance?(instance type Builder).build():instance";

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
                        assertTrue(d.currentValue() instanceof PropertyWrapper,
                                "Have " + d.currentValue().getClass());
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(Property.NOT_NULL_EXPRESSION));
                    }
                    if ("0".equals(d.statementId())) {
                        String expect = "instance instanceof Fluent_0&&null!=instance?instance:<return value>";
                        assertEquals(expect, d.currentValue().toString());
                        // <return value> is nullable
                        assertEquals(MultiLevel.NULLABLE_DV, d.getProperty(Property.NOT_NULL_EXPRESSION));
                    }
                    if ("1".equals(d.statementId())) {
                        String expect = d.iteration() == 0
                                ? "!(instance instanceof Fluent_0)||null==instance?<m:build>:instance" :
                                INSTANCE_TYPE_BUILDER_BUILD;
                        assertEquals(expect, d.currentValue().toString());

                        String expectLinks = d.iteration() == 0 ? "?" : "instance";
                        assertEquals(expectLinks, d.variableInfo().getLinkedVariables().toString());

                        // computation of NNE is important here!
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                    }
                }
            }
            if ("from".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo i && "instance".equals(i.name)) {
                    DV cm = d.getProperty(Property.CONTEXT_MODIFIED);
                    if ("0".equals(d.statementId())) {
                        assertEquals(DV.FALSE_DV, cm);
                    }
                    if ("1".equals(d.statementId())) {
                        assertEquals(DV.FALSE_DV, cm);
                    }
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("copyOf".equals(d.methodInfo().name)) {
                // @NotModified
                assertDv(d, 2, DV.FALSE_DV, Property.MODIFIED_METHOD);

                if (d.iteration() <= 1) {
                    assertNull(d.methodAnalysis().getSingleReturnValue());
                } else {
                    assertEquals(INSTANCE_TYPE_BUILDER_BUILD, d.methodAnalysis().getSingleReturnValue().toString());
                }

                assertDv(d, 2, DV.FALSE_DV, Property.FLUENT);
                assertDv(d, 2, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                assertDv(d, 2, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
            }

            if ("build".equals(d.methodInfo().name)) {
                assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                assertDv(d, 1, DV.FALSE_DV, Property.MODIFIED_METHOD);
                assertDv(d, 1, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
            }

            if ("from".equals(d.methodInfo().name)) {
                if (d.iteration() == 0) {
                    assertNull(d.methodAnalysis().getSingleReturnValue());
                } else {
                    assertEquals("this", d.methodAnalysis().getSingleReturnValue().toString());
                }
                assertDv(d, 1, DV.TRUE_DV, Property.FLUENT);
                assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                assertDv(d, 1, MultiLevel.DEPENDENT_DV, Property.INDEPENDENT);
                assertDv(d, 1, DV.TRUE_DV, Property.MODIFIED_METHOD);
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Fluent_0".equals(d.typeInfo().simpleName)) {
                assertEquals("[]", d.typeAnalysis().getTransparentTypes().toString());
                assertFalse(d.typeInfo().typePropertiesAreContracted());
            }
            if ("IFluent_0".equals(d.typeInfo().simpleName)) {
                // property has been contracted in the code: there is no computing
                assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE_DV, d.typeAnalysis().getProperty(Property.IMMUTABLE));
                assertTrue(d.typeInfo().typePropertiesAreContracted());
            }
        };

        testClass(List.of("a.IFluent_0", "Fluent_0"), 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .build(), new AnalyserConfiguration.Builder().build(), new AnnotatedAPIConfiguration.Builder().build());
    }

    @Test
    public void test_1() throws IOException {
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("IFluent_1".equals(d.typeInfo().simpleName)) {
                assertFalse(d.typeInfo().typePropertiesAreContracted()); // they are aggregated!
                assertTrue(d.typeInfo().typeResolution.get().hasOneKnownGeneratedImplementation());
                assertDv(d, 2, MultiLevel.EFFECTIVELY_E2IMMUTABLE_DV, Property.IMMUTABLE);
            }
            if ("Fluent_1".equals(d.typeInfo().simpleName)) {
                assertDv(d, 2, MultiLevel.EFFECTIVELY_E2IMMUTABLE_DV, Property.IMMUTABLE);
                assertFalse(d.typeInfo().typePropertiesAreContracted());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("value".equals(d.methodInfo().name) && "IFluent_1".equals(d.methodInfo().typeInfo.simpleName)) {
                assertEquals(DV.FALSE_DV, d.methodAnalysis().getProperty(Property.MODIFIED_METHOD));
                assertEquals(DV.FALSE_DV, d.methodAnalysis().getProperty(Property.FLUENT));
                assertEquals(DV.FALSE_DV, d.methodAnalysis().getProperty(Property.IDENTITY));
                assertEquals(MultiLevel.INDEPENDENT_DV, d.methodAnalysis().getProperty(Property.INDEPENDENT));
                assertEquals(MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, d.methodAnalysis().getProperty(Property.IMMUTABLE));
            }
            if ("identity".equals(d.methodInfo().name)) {
                assertEquals(DV.FALSE_DV, d.methodAnalysis().getProperty(Property.MODIFIED_METHOD));
                assertEquals(DV.TRUE_DV, d.methodAnalysis().getProperty(Property.IDENTITY));
            }
            if ("value".equals(d.methodInfo().name) && "Fluent_1".equals(d.methodInfo().typeInfo.simpleName)) {
                assertEquals(DV.FALSE_DV, d.methodAnalysis().getProperty(Property.MODIFIED_METHOD));
            }
            if ("value".equals(d.methodInfo().name) && "IFluent_1".equals(d.methodInfo().typeInfo.simpleName)) {
                // via aggregation
                assertEquals(DV.FALSE_DV, d.methodAnalysis().getProperty(Property.MODIFIED_METHOD));
            }

            if ("from".equals(d.methodInfo().name)) {
                // STEP 1 links have not been established
                assertDv(d, 1, DV.TRUE_DV, Property.MODIFIED_METHOD);
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("from".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertNull(d.conditionManagerForNextStatement().preconditionIsDelayed());
                    assertTrue(d.statementAnalysis().methodLevelData().combinedPrecondition.isFinal());
                    assertTrue(d.statementAnalysis().stateData().preconditionIsFinal());
                }
                if ("1".equals(d.statementId())) {
                    // STEP 6: evaluation renders a delayed precondition: hardcoded in AggregatingMethodAnalyser
                    assertTrue(d.statementAnalysis().stateData().preconditionIsFinal());

                    // STEP 5: check preconditionIsDelayed in previous statement, that's OK
                    assertNull(d.conditionManagerForNextStatement().preconditionIsDelayed());
                    // STEP 5bis: combined precondition never becomes final
                    assertTrue(d.statementAnalysis().methodLevelData().combinedPrecondition.isFinal());
                }
                if ("2".equals(d.statementId())) {
                    // STEP 2 parameter 'instance'
                    assertEquals(d.iteration() > 0, d.statementAnalysis().methodLevelData().linksHaveBeenEstablished());
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("from".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo p && "instance".equals(p.name)) {
                    if ("0".equals(d.statementId())) {
                        // because parameters are @Modified by default, we're without annotated APIs
                        assertEquals(DV.TRUE_DV, d.getProperty(Property.CONTEXT_MODIFIED));
                        assertEquals("nullable instance type IFluent_1/*@Identity*/", d.currentValue().toString());
                        assertEquals("instance:0", d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("2".equals(d.statementId())) {
                        // STEP 3 value of 0 not set because no linked variables set for return variable
                        assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if (d.variable() instanceof ReturnVariable && "2".equals(d.statementId())) {
                    // STEP 4 <s:Builder> value delayed -> linked variables delayed; why is value delayed?
                    // Precondition in state is delayed (empty set instead of null)
                    assertEquals("this/*(Builder)*/", d.currentValue().toString());
                }
            }
        };

        TypeContext typeContext = testClass(List.of("a.IFluent_1", "Fluent_1"),
                List.of("jmods/java.compiler.jmod"),
                0, 1, new DebugConfiguration.Builder()
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
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
                        .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
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
                        .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                        .build(), new AnalyserConfiguration.Builder().build(), new AnnotatedAPIConfiguration.Builder().build());
    }
}
