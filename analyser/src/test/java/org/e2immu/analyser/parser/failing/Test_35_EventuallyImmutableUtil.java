
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

import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.analysis.Analysis;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.expression.InlinedMethod;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.failing.testexample.*;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.e2immu.analyser.visitor.TypeAnalyserVisitor;
import org.e2immu.support.AddOnceSet;
import org.e2immu.support.FlipSwitch;
import org.e2immu.support.Freezable;
import org.e2immu.support.SetOnce;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class Test_35_EventuallyImmutableUtil extends CommonTestRunner {

    public Test_35_EventuallyImmutableUtil() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("isSet".equals(d.methodInfo().name)) {
                if (d.iteration() == 0) {
                    assertNull(d.methodAnalysis().getSingleReturnValue());
                } else {
                    assertEquals("isSet$0", d.methodAnalysis().getSingleReturnValue().toString());
                    if (d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod inlinedMethod) {
                        assertEquals("isSet$0", inlinedMethod.expression().toString());
                        if (inlinedMethod.expression() instanceof VariableExpression variableExpression) {
                            // TODO
                        } else fail();
                    } else fail();
                }
            }
        };
        TypeContext typeContext = testSupportAndUtilClasses(List.of(EventuallyImmutableUtil_0.class, FlipSwitch.class),
                0, 0, new DebugConfiguration.Builder()
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .build());

        TypeInfo flipSwitch = typeContext.getFullyQualified(FlipSwitch.class);
        MethodInfo isSet = flipSwitch.findUniqueMethod("isSet", 0);
        assertSame(Analysis.AnalysisMode.COMPUTED, isSet.methodAnalysis.get().analysisMode());
        assertFalse(flipSwitch.typeResolution.get().hasOneKnownGeneratedImplementation());
        assertTrue(flipSwitch.typeResolution.get().circularDependencies().isEmpty());
        assertTrue(flipSwitch.typeResolution.get().superTypesExcludingJavaLangObject().isEmpty());

        TypeInfo eventually = typeContext.getFullyQualified(EventuallyImmutableUtil_0.class);
        MethodInfo isReady = eventually.findUniqueMethod("isReady", 0);
        assertSame(Analysis.AnalysisMode.COMPUTED, isReady.methodAnalysis.get().analysisMode());
        assertFalse(eventually.typeResolution.get().hasOneKnownGeneratedImplementation());
        assertTrue(eventually.typeResolution.get().circularDependencies().isEmpty());
        assertTrue(eventually.typeResolution.get().superTypesExcludingJavaLangObject().isEmpty());
    }

    @Test
    public void test_1() throws IOException {
        testSupportAndUtilClasses(List.of(EventuallyImmutableUtil_1.class, FlipSwitch.class, SetOnce.class),
                0, 0, new DebugConfiguration.Builder()
                        .build());
    }

    @Test
    public void test_2() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("set2".equals(d.methodInfo().name)) {
                String expectPre = switch (d.iteration()) {
                    case 0 -> "<precondition>";
                    case 1 -> "null==<f:t>";
                    default -> "null==value.t";
                };
                assertEquals(expectPre, d.statementAnalysis().stateData().getPrecondition().expression().toString());
                assertEquals(d.iteration() > 1, d.statementAnalysis().stateData().preconditionIsFinal());
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("EventuallyImmutableUtil_2".equals(d.typeInfo().simpleName)) {
                assertTrue(d.typeAnalysis().getApprovedPreconditionsE1().isEmpty());
                String expectEvImm = d.iteration()<= 1 ? "[]": "[value]";
                assertEquals(expectEvImm, d.typeAnalysis().getEventuallyImmutableFields().toString());
                String expectE2 = d.iteration() <= 1 ? "{}" : "{value.t=null==value.t}";
                assertEquals(expectE2, d.typeAnalysis().getApprovedPreconditionsE2().toString());
            }
        };

        testSupportAndUtilClasses(List.of(EventuallyImmutableUtil_2.class, FlipSwitch.class, SetOnce.class),
                0, 0, new DebugConfiguration.Builder()
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
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

        testSupportAndUtilClasses(List.of(EventuallyImmutableUtil_3.class, FlipSwitch.class, SetOnce.class),
                0, 0, new DebugConfiguration.Builder()
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .build());
    }

    @Test
    public void test_4() throws IOException {
        testSupportAndUtilClasses(List.of(EventuallyImmutableUtil_4.class, FlipSwitch.class, SetOnce.class),
                0, 0, new DebugConfiguration.Builder()
                        .build());
    }

    @Test
    public void test_5() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            final String expectedT = "t.s1.bool.isSet()&&t.s2.bool.isSet()&&t.s1.string.isSet()&&t.s2.string.isSet()";
            final String expected = "s1.bool.isSet()&&s2.bool.isSet()&&s1.string.isSet()&&s2.string.isSet()";

            if ("isReady1".equals(d.methodInfo().name) && d.iteration() > 6) {
                assertEquals(expectedT, d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("isReady2".equals(d.methodInfo().name) && d.iteration() > 5) {
                assertEquals(expectedT, d.methodAnalysis().getSingleReturnValue().toString());
            }

            if ("isTReady".equals(d.methodInfo().name) && d.iteration() > 4) {
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
            }
        };
        testSupportAndUtilClasses(List.of(EventuallyImmutableUtil_5.class, FlipSwitch.class, SetOnce.class),
                0, 0, new DebugConfiguration.Builder()
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .build());
    }

    @Test
    public void test_6() throws IOException {
        testSupportAndUtilClasses(List.of(EventuallyImmutableUtil_6.class, AddOnceSet.class, Freezable.class),
                 0, 1, new DebugConfiguration.Builder()
                        .build());
    }

    @Test
    public void test_7() throws IOException {
        testSupportAndUtilClasses(List.of(EventuallyImmutableUtil_7.class, Freezable.class),
                0, 0, new DebugConfiguration.Builder()
                        .build());
    }

    @Test
    public void test_8() throws IOException {
        testSupportAndUtilClasses(List.of(EventuallyImmutableUtil_8.class, Freezable.class),
                0, 0, new DebugConfiguration.Builder()
                        .build());
    }

    @Test
    public void test_9() throws IOException {
        testSupportAndUtilClasses(List.of(EventuallyImmutableUtil_9.class, Freezable.class),
                0, 0, new DebugConfiguration.Builder()
                        .build());
    }
}
