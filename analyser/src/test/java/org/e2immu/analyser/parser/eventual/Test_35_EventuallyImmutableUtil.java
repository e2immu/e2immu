
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

package org.e2immu.analyser.parser.eventual;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analysis.Analysis;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.InlinedMethod;
import org.e2immu.analyser.model.expression.PropertyWrapper;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.eventual.testexample.EventuallyImmutableUtil_0;
import org.e2immu.analyser.visitor.*;
import org.e2immu.support.EventuallyFinal;
import org.e2immu.support.FlipSwitch;
import org.e2immu.support.SetOnce;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class Test_35_EventuallyImmutableUtil extends CommonTestRunner {

    public Test_35_EventuallyImmutableUtil() {
        super(true);
    }

    // CONTRACTED (only parse the test, read annotations from FlipSwitch)
    @Test
    public void test_0_1() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("isSet".equals(d.methodInfo().name)) {
                String expect = d.iteration() == 0 ? "<m:isSet>" : "/*inline isSet*/isSet$0";
                assertEquals(expect, d.methodAnalysis().getSingleReturnValue().toString());
                if (d.iteration() > 0) {
                    if (d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod inlinedMethod) {
                        assertEquals("isSet$0", inlinedMethod.expression().toString());
                        if (inlinedMethod.expression() instanceof VariableExpression variableExpression) {
                            assertEquals("isSet", variableExpression.variable().simpleName());
                        } else fail();
                    } else fail();
                }
            }
            if ("isReady".equals(d.methodInfo().name)) {
                String expect = d.iteration() == 0 ? "<m:isReady>" : "/*inline isReady*/flipSwitch.isSet()";
                assertEquals(expect, d.methodAnalysis().getSingleReturnValue().toString());
            }
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo flipSwitch = typeMap.get(FlipSwitch.class);
            MethodInfo isSet = flipSwitch.findUniqueMethod("isSet", 0);
            assertSame(Analysis.AnalysisMode.CONTRACTED, isSet.methodAnalysis.get().analysisMode());
            assertFalse(flipSwitch.typeResolution.get().hasOneKnownGeneratedImplementation());
            assertTrue(flipSwitch.typeResolution.get().circularDependencies().isEmpty());
            assertTrue(flipSwitch.typeResolution.get().superTypesExcludingJavaLangObject().isEmpty());
            MethodAnalysis isSetAnalysis = isSet.methodAnalysis.get();
            assertEquals("@TestMark: [isSet]", isSetAnalysis.getEventual().toString());
        };

        testClass("EventuallyImmutableUtil_0", 0, 0,
                new DebugConfiguration.Builder()
                        .addTypeMapVisitor(typeMapVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .build());
    }

    // COMPUTED: also compute FlipSwitch
    @Test
    public void test_0_2() throws IOException {
        TypeContext typeContext = testSupportAndUtilClasses(List.of(EventuallyImmutableUtil_0.class, FlipSwitch.class),
                0, 0, new DebugConfiguration.Builder().build());
        TypeInfo flipSwitch = typeContext.getFullyQualified(FlipSwitch.class);
        MethodInfo isSet = flipSwitch.findUniqueMethod("isSet", 0);
        assertSame(Analysis.AnalysisMode.COMPUTED, isSet.methodAnalysis.get().analysisMode());
        MethodAnalysis isSetAnalysis = isSet.methodAnalysis.get();
        assertEquals("@TestMark: [isSet]", isSetAnalysis.getEventual().toString());
    }

    // contracted is the norm for this test suite
    @Test
    public void test_1() throws IOException {
        testClass("EventuallyImmutableUtil_1",
                0, 0, new DebugConfiguration.Builder().build());
    }

    @Test
    public void test_2() throws IOException {
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("value".equals(d.fieldInfo().name)) {
                assertDv(d, MultiLevel.EVENTUALLY_RECURSIVELY_IMMUTABLE_DV, Property.EXTERNAL_IMMUTABLE);
            }
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("EventuallyImmutableUtil_2".equals(d.typeInfo().simpleName)) {
                assertTrue(d.typeAnalysis().getApprovedPreconditionsE1().isEmpty());
                String expectEvImm = d.iteration() < 2 ? "[]" : "[value]";
                assertEquals(expectEvImm, d.typeAnalysis().getEventuallyImmutableFields().toString());
                assertEquals("{}", d.typeAnalysis().getApprovedPreconditionsE2().toString());
            }
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo setOnce = typeMap.get(SetOnce.class);
            MethodInfo set = setOnce.findUniqueMethod("set", 1);
            assertSame(Analysis.AnalysisMode.CONTRACTED, set.methodAnalysis.get().analysisMode());
            MethodAnalysis setAnalysis = set.methodAnalysis.get();
            assertEquals("@Mark: [t]", setAnalysis.getEventual().toString());

            MethodInfo get = setOnce.findUniqueMethod("get", 0);
            assertSame(Analysis.AnalysisMode.CONTRACTED, get.methodAnalysis.get().analysisMode());
            MethodAnalysis getAnalysis = get.methodAnalysis.get();
            assertEquals("@Only after: [t]", getAnalysis.getEventual().toString());
        };

        testClass("EventuallyImmutableUtil_2", 0, 0, new DebugConfiguration.Builder()
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .build());
    }

    @Test
    public void test_3() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("isReady".equals(d.methodInfo().name)) {
                // preconditions have nothing to do with this
                assertTrue(d.statementAnalysis().stateData().getPrecondition().isEmpty());
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("isReady".equals(d.methodInfo().name) && d.variable() instanceof ReturnVariable) {
                String expectValue = d.iteration() == 0 ? "<m:isSet>&&<m:isSet>" : "bool.isSet()&&string.isSet()";
                assertEquals(expectValue, d.currentValue().toString());
            }
        };

        testClass("EventuallyImmutableUtil_3", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_4() throws IOException {
        testClass("EventuallyImmutableUtil_4", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_5() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("isReady".equals(d.methodInfo().name)) {
                String expected2 = d.iteration() == 0 ? "<m:isReady>" : "/*inline isReady*/bool.isSet()&&string.isSet()";
                assertEquals(expected2, d.methodAnalysis().getSingleReturnValue().toString());
                if (d.iteration() > 0) {
                    if (d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod inlinedMethod) {
                        assertEquals("bool, string, this", inlinedMethod.variablesOfExpressionSorted());
                    } else fail();
                }
            }
            if ("isTReady".equals(d.methodInfo().name)) {
                String expectT = d.iteration() <= 2 ? "<m:isTReady>" : "/*inline isTReady*/`s1.bool`.isSet()&&`s1.string`.isSet()&&`s2.bool`.isSet()&&`s2.string`.isSet()";
                assertEquals(expectT, d.methodAnalysis().getSingleReturnValue().toString());
                assertDv(d, 3, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                if (d.iteration() >= 3) {
                    if (d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod inlinedMethod) {
                        assertEquals("bool, string, this", inlinedMethod.variablesOfExpressionSorted());
                    } else fail();
                }
            }

            final String expected12 = "`t.s1.bool`.isSet()&&`t.s1.string`.isSet()&&`t.s2.bool`.isSet()&&`t.s2.string`.isSet()";
            if ("isReady1".equals(d.methodInfo().name)) {
                String expected1 = d.iteration() <= 3 ? "<m:isReady1>" : "/*inline isReady1*/" + expected12;
                assertEquals(expected1, d.methodAnalysis().getSingleReturnValue().toString());
                assertDv(d, 4, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
            }
            if ("isReady2".equals(d.methodInfo().name)) {
                String expected2 = d.iteration() <= 3 ? "<m:isReady2>" : "/*inline isReady2*/" + expected12;
                assertEquals(expected2, d.methodAnalysis().getSingleReturnValue().toString());
                assertDv(d, 4, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
            }
        };
        testClass("EventuallyImmutableUtil_5", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test_6() throws IOException {
        testClass("EventuallyImmutableUtil_6", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_7() throws IOException {
        testClass("EventuallyImmutableUtil_7", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_8() throws IOException {
        testClass("EventuallyImmutableUtil_8",
                0, 0, new DebugConfiguration.Builder()
                        .build());
    }

    @Test
    public void test_9() throws IOException {
        testClass("EventuallyImmutableUtil_9", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    // eventually e2immutable, even though its parent is Freezable/eventually ER
    @Test
    public void test_10() throws IOException {
        testClass("EventuallyImmutableUtil_10",
                0, 0, new DebugConfiguration.Builder()
                        .build());
    }

    // mutable, even though its parent is Freezable/eventually ER
    @Test
    public void test_11() throws IOException {
        testClass("EventuallyImmutableUtil_11", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_12() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("set".equals(d.methodInfo().name)) {
                assertEquals("0", d.statementId());
                if (d.variable() instanceof FieldReference fr && "eventuallyFinal".equals(fr.fieldInfo.name)) {
                    assertDv(d, 1, MultiLevel.EVENTUALLY_ERIMMUTABLE_BEFORE_MARK_DV, Property.EXTERNAL_IMMUTABLE);
                }
            }
            if ("done".equals(d.methodInfo().name)) {
                assertEquals("0", d.statementId());
                if (d.variable() instanceof FieldReference fr && "eventuallyFinal".equals(fr.fieldInfo.name)) {
                    assertDv(d, 1, MultiLevel.EVENTUALLY_ERIMMUTABLE_AFTER_MARK_DV, Property.EXTERNAL_IMMUTABLE);
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("set".equals(d.methodInfo().name)) {
                // there are no preconditions
                String expect = d.iteration() <= 1 ? "Precondition[expression=<precondition>, causes=[]]"
                        : "Precondition[expression=true, causes=[]]";
                assertEquals(expect, d.methodAnalysis().getPreconditionForEventual().toString());
                // but eventual will pick up the restrictions in EXT_IMM/CTX_IMM
                if (d.iteration() >= 3) {
                    assertEquals("@Only before: [eventuallyFinal]", d.methodAnalysis().getEventual().toString());
                } else {
                    assertTrue(d.methodAnalysis().getEventual().causesOfDelay().isDelayed());
                }
            }
            if ("done".equals(d.methodInfo().name)) {
                // there are no preconditions
                String expect = d.iteration() <= 1 ? "Precondition[expression=<precondition>, causes=[]]"
                        : "Precondition[expression=true, causes=[]]";
                assertEquals(expect, d.methodAnalysis().getPreconditionForEventual().toString());
                // but eventual will pick up the restrictions in EXT_IMM/CTX_IMM
                if (d.iteration() >= 3) {
                    assertEquals("@Mark: [eventuallyFinal]", d.methodAnalysis().getEventual().toString());
                } else {
                    assertTrue(d.methodAnalysis().getEventual().causesOfDelay().isDelayed());
                }
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            Expression initializerValue = d.fieldAnalysis().getInitializerValue();
            assertEquals("new EventuallyFinal<>()", initializerValue.toString());

            // before mark because the value is a ConstructorCall
            DV concrete = initializerValue.getProperty(EvaluationResult.from(d.evaluationContext()), Property.IMMUTABLE, true);
            assertEquals(MultiLevel.EVENTUALLY_ERIMMUTABLE_BEFORE_MARK_DV, concrete);

            DV formally = d.evaluationContext().getAnalyserContext().defaultImmutable(initializerValue.returnType(),
                    false);
            assertEquals(MultiLevel.EVENTUALLY_RECURSIVELY_IMMUTABLE_DV, formally);

            assertEquals("instance type EventuallyFinal<String>", d.fieldAnalysis().getValue().toString());
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("EventuallyImmutableUtil_12".equals(d.typeInfo().simpleName)) {
                String expected = d.iteration() <= 1 ? "[]" : "[eventuallyFinal]";
                assertEquals(expected, d.typeAnalysis().getEventuallyImmutableFields().toString());
            }
        };
        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo eventuallyFinal = typeMap.get(EventuallyFinal.class);
            MethodInfo setVariable = eventuallyFinal.findUniqueMethod("setVariable", 1);
            MethodAnalysis setVariableAnalysis = setVariable.methodAnalysis.get();
            assertEquals("@Only before: [isFinal]", setVariableAnalysis.getEventual().toString());
        };
        testClass("EventuallyImmutableUtil_12", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .build());
    }

    @Test
    public void test_13() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("done".equals(d.methodInfo().name)) {
                assertDv(d, 1, MultiLevel.EVENTUALLY_ERIMMUTABLE_AFTER_MARK_DV, Property.IMMUTABLE);
            }
        };
        testClass("EventuallyImmutableUtil_13", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test_14() throws IOException {
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("count".equals(d.fieldInfo().name)) {
                assertDv(d, MultiLevel.MUTABLE_DV, Property.EXTERNAL_IMMUTABLE);
                assertDv(d, DV.TRUE_DV, Property.FINAL);
            }
        };
        testClass("EventuallyImmutableUtil_14", 0, 0, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }

    @Test
    public void test_15() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("EventuallyImmutableUtil_15".equals(d.methodInfo().name)) {
                assertEquals("0", d.statementId());
                if (d.variable() instanceof FieldReference fr && "eventuallyFinal".equals(fr.fieldInfo.name)) {
                    assertEquals("ev/*@NotNull*/", d.currentValue().toString());
                    if (d.currentValue() instanceof PropertyWrapper pw) {
                        assertFalse(pw.properties().containsKey(Property.IMMUTABLE));
                        assertTrue(pw.expression() instanceof VariableExpression ve && ve.variable() instanceof ParameterInfo);
                    } else fail();
                    // properly linked to the field!
                    assertEquals("ev:1,this.eventuallyFinal:0", d.variableInfo().getLinkedVariables().toString());
                    assertEquals(MultiLevel.EVENTUALLY_ERIMMUTABLE_BEFORE_MARK_DV, d.getProperty(Property.IMMUTABLE));
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("eventuallyFinal".equals(d.fieldInfo().name)) {
                assertDv(d, MultiLevel.EVENTUALLY_RECURSIVELY_IMMUTABLE_DV, Property.EXTERNAL_IMMUTABLE);
                assertDv(d, DV.TRUE_DV, Property.FINAL);
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("EventuallyImmutableUtil_15".equals(d.methodInfo().name)) {
                assertDv(d.p(0), MultiLevel.EVENTUALLY_ERIMMUTABLE_BEFORE_MARK_DV, Property.IMMUTABLE);
            }
        };

        testClass("EventuallyImmutableUtil_15", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }
}
