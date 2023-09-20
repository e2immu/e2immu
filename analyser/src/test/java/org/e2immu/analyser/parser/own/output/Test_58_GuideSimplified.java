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

package org.e2immu.analyser.parser.own.output;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Test_58_GuideSimplified extends CommonTestRunner {

    public Test_58_GuideSimplified() {
        super(false);
    }

    // original minus some dependencies
    // minimal, debug should be marked static
    @Test
    public void test_0() throws IOException {
        testClass("GuideSimplified_0", 2, 0, new DebugConfiguration.Builder()
                        .build(),
                new AnalyserConfiguration.Builder()
                        .setComputeContextPropertiesOverAllMethods(true)
                        .setComputeFieldAnalyserAcrossAllMethods(true)
                        .build());
    }

    // too simple
    @Test
    public void test_1() throws IOException {
        testClass("GuideSimplified_1", 0, 0, new DebugConfiguration.Builder()
                .build(), new AnalyserConfiguration.Builder()
                .setComputeContextPropertiesOverAllMethods(true)
                .setComputeFieldAnalyserAcrossAllMethods(true)
                .build());
    }

    @Test
    public void test_2() throws IOException {
        // minimal, debug should be marked static
        testClass("GuideSimplified_2", 2, 0, new DebugConfiguration.Builder()
                .build(), new AnalyserConfiguration.Builder()
                .setComputeContextPropertiesOverAllMethods(true)
                .setComputeFieldAnalyserAcrossAllMethods(true)
                .build());
    }

    // simplest situation
    @Test
    public void test_3() throws IOException {
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Position".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.EFFECTIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("values".equals(d.methodInfo().name)) {
                assertEquals("Position", d.methodInfo().typeInfo.simpleName);
                String expected = d.iteration() <= 1 ? "<m:values>" : "{Position.START,Position.MID,Position.END}";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("GuideSimplified_3".equals(d.methodInfo().name)) {
                assertDv(d.p(1), 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                assertDv(d.p(1), 1, DV.FALSE_DV, Property.MODIFIED_OUTSIDE_METHOD);
                assertDv(d.p(1), 1, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("START".equals(d.fieldInfo().name)) {
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.fieldAnalysis().getProperty(Property.EXTERNAL_NOT_NULL));

                assertDv(d, DV.FALSE_DV, Property.MODIFIED_OUTSIDE_METHOD);
            }
            if ("position".equals(d.fieldInfo().name)) {
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_OUTSIDE_METHOD);
            }
        };

        testClass("GuideSimplified_3", 1, 0, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }

    @Test
    public void test_4() throws IOException {

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("position".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "position".equals(fr.fieldInfo.name)) {
                    String expectValue = d.iteration() <= 1 ? "<f:position>" : "instance type Position";
                    assertEquals(expectValue, d.currentValue().toString());
                    assertEquals("", d.variableInfo().getLinkedVariables().toString());

                    // this one must wait for Position to become @E2Immutable
                    assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("trace".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertTrue(d.iteration() < 4);
                }
            }
            if ("position".equals(d.methodInfo().name)) {
                assertTrue(d.statementAnalysis().methodLevelData().linksHaveBeenEstablished());
            }
            if ("values".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertTrue(d.statementAnalysis().methodLevelData().linksHaveBeenEstablished());
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("values".equals(d.methodInfo().name)) {
                assertEquals("Position", d.methodInfo().typeInfo.simpleName);

                String expected = d.iteration() <= 1 ? "<m:values>" : "{Position.START,Position.MID,Position.END}";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("GuideSimplified_4".equals(d.methodInfo().name)) {
                assertDv(d.p(1), 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                assertDv(d.p(1), 1, DV.FALSE_DV, Property.MODIFIED_OUTSIDE_METHOD);
                assertDv(d.p(1), 1, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
            }
            if ("trace".equals(d.methodInfo().name)) {
                String expected = d.iteration() <= 1 ? "<m:trace>" : "\"/*\"+position.msg+\"*/\"";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());

                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("msg".equals(d.fieldInfo().name)) {
                assertEquals(DV.TRUE_DV, d.fieldAnalysis().getProperty(Property.FINAL));
                assertEquals(MultiLevel.EFFECTIVELY_IMMUTABLE_DV,
                        d.fieldAnalysis().getProperty(Property.EXTERNAL_IMMUTABLE));
                assertDv(d, 5, DV.FALSE_DV, Property.MODIFIED_OUTSIDE_METHOD);
                assertEquals("msg:0", d.fieldAnalysis().getLinkedVariables().toString());
            }

            if ("START".equals(d.fieldInfo().name)) {
                assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.EXTERNAL_NOT_NULL);
                assertDv(d, 8, DV.FALSE_DV, Property.MODIFIED_OUTSIDE_METHOD);
            }
            if ("position".equals(d.fieldInfo().name)) {
                assertEquals("position", d.fieldAnalysis().getValue().toString());
                assertDv(d, 7, DV.FALSE_DV, Property.MODIFIED_OUTSIDE_METHOD);
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Position".equals(d.typeInfo().simpleName)) {
                assertDv(d, 5, MultiLevel.EFFECTIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
            }
        };

        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("----MF-----", d.delaySequence());

        testClass("GuideSimplified_4", 0, 0, new DebugConfiguration.Builder()
                //     .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                //    .addStatementAnalyserVisitor(statementAnalyserVisitor)
                //     .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .addBreakDelayVisitor(breakDelayVisitor)
                .build(), new AnalyserConfiguration.Builder()
                .setComputeContextPropertiesOverAllMethods(true)
                .setComputeFieldAnalyserAcrossAllMethods(true)
                .build());
    }

    // contrast to test_4 solved with refactoring of fieldAccess
    @Test
    public void test_5() throws IOException {
        final String TRACE_RETURN = "\"/*\"+(this.position()).msg+\"*/\"";

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("trace".equals(d.methodInfo().name) && d.variable() instanceof ReturnVariable) {
                String expectValue = d.iteration() <= 1 ? "\"/*\"+<f:this.position().msg>+\"*/\"" : TRACE_RETURN;
                assertEquals(expectValue, d.currentValue().toString());
            }
            if ("GuideSimplified_5".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo p && "position".equals(p.name)) {
                    if ("2".equals(d.statementId())) {
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(Property.CONTEXT_NOT_NULL));
                    }
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("trace".equals(d.methodInfo().name)) {
                String expect = d.iteration() <= 1 ? "<m:trace>" : TRACE_RETURN;
                assertEquals(expect, d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("position".equals(d.methodInfo().name)) {
                assertDv(d, 2, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
            }
        };

        testClass("GuideSimplified_5", 1, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

}
