
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

package org.e2immu.analyser.parser.basics;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.it;
import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.it0;
import static org.junit.jupiter.api.Assertions.*;

public class Test_00_Basics_24 extends CommonTestRunner {

    public Test_00_Basics_24() {
        super(false);
    }

    // IMPORTANT: parameter "defaultValue" in map.getOrDefault is MODIFYING, there are no annotated APIs
    // 20220621: this has no bearing on the delay loop we're trying to fix. It is the "new X(a)" call
    // we focus on.
    @Test
    public void test() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("2".equals(d.statementId())) {
                    String expected = d.iteration() == 0 ? "<null-check>" : "null==map.getOrDefault(pos,a)";
                    assertEquals(expected, d.evaluationResult().value().toString());
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("x".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        // after breaking
                        assertEquals("new X(a)", d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        assertEquals("instance type X", d.currentValue().toString());
                    }
                }
                if (d.variable() instanceof FieldReference fr && "s".equals(fr.fieldInfo.name)) {
                    if ("1".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<m:getOrDefault>" : "map.getOrDefault(pos,a)";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("2".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<null-check>?y:<m:getOrDefault>"
                                : "null==map.getOrDefault(pos,a)?y:map.getOrDefault(pos,a)";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
                if (d.variable() instanceof FieldReference fr && "map".equals(fr.fieldInfo.name)) {
                    if (d.statementId().compareTo("1") >= 0) {
                        String expected = d.iteration() == 0 ? "<f:map>" : "instance type Map<Integer,String>";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("3".equals(d.statementId())) {
                        String linked = switch (d.iteration()) {
                            case 0 -> "a:-1,pos:-1,this:-1,x.s:-1,x:-1,y:-1";
                            default -> "";
                        };
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("3".equals(d.statementId())) {
                        String linked = switch (d.iteration()) {
                            case 0 -> "a:-1,pos:-1,this.map:-1,this:-1,x.s:0,x:-1,y:0";
                            default -> "x.s:0,x:2";
                        };
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                }
            }
            if ("X".equals(d.methodInfo().name)) {
                assertEquals("0", d.statementId());
                if (d.variable() instanceof ParameterInfo pi && "s".equals(pi.name)) {
                    assertDv(d, 2, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER_RESTRICTION);
                    assertDv(d, 2, MultiLevel.NULLABLE_DV, Property.EXTERNAL_NOT_NULL);
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("s".equals(d.fieldInfo().name)) {
                assertDv(d, 1, DV.FALSE_DV, Property.MODIFIED_OUTSIDE_METHOD);
                assertLinked(d, d.fieldAnalysis().getLinkedVariables(),
                        it0("a:-1,pos:-1,s:-1,this.map:-1,x:-1,y:-1"), it(1, "s:0"));
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("X".equals(d.methodInfo().name)) {
                assertTrue(d.methodInfo().isConstructor);
                assertEquals(d.iteration() == 3, d.allowBreakDelay());
                String delay = d.iteration() == 0 ? "cm@Parameter_s;mom@Parameter_s" : "mom@Parameter_s";
                assertDv(d.p(0), delay, 2, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d.p(0), 2, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER_RESTRICTION);
                assertDv(d.p(0), 2, MultiLevel.NULLABLE_DV, Property.EXTERNAL_NOT_NULL);
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("X".equals(d.typeInfo().simpleName)) {
                assertTrue(d.typeInfo().typeResolution.get().fieldsAccessedInRestOfPrimaryType());

                assertEquals(d.iteration() > 0, d.typeAnalysis().guardedForContainerPropertyDelays().isDone());
                assertEquals(d.iteration() > 0, d.typeAnalysis().guardedForInheritedContainerPropertyDelays().isDone());
                assertDv(d, 1, MultiLevel.CONTAINER_DV, Property.CONTAINER);
            }
            if ("Y".equals(d.typeInfo().simpleName)) {
                assertFalse(d.typeInfo().typeResolution.get().fieldsAccessedInRestOfPrimaryType());

                assertTrue(d.typeAnalysis().guardedForContainerPropertyDelays().isDone());
                assertTrue(d.typeAnalysis().guardedForInheritedContainerPropertyDelays().isDone());
                assertDv(d, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER);
                assertDv(d, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
            }
            if ("Basics_24".equals(d.typeInfo().simpleName)) {
                assertFalse(d.typeInfo().typeResolution.get().fieldsAccessedInRestOfPrimaryType());

                assertEquals(d.iteration() > 0, d.typeAnalysis().guardedForContainerPropertyDelays().isDone());
                assertEquals(d.iteration() > 0, d.typeAnalysis().guardedForInheritedContainerPropertyDelays().isDone());
                assertDv(d, 3, MultiLevel.CONTAINER_DV, Property.CONTAINER);
            }
        };

        BreakDelayVisitor breakDelayVisitor = d -> {
            assertEquals("----", d.delaySequence());
        };

        testClass("Basics_24", 0, 0, new DebugConfiguration.Builder()
                        .addEvaluationResultVisitor(evaluationResultVisitor)
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                        .addBreakDelayVisitor(breakDelayVisitor)
                        .build(),
                new AnalyserConfiguration.Builder()
                        .setComputeFieldAnalyserAcrossAllMethods(true).build());
    }

    @DisplayName("with static class rather than interface")
    @Test
    public void test1() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                ParameterAnalysis a = d.parameterAnalyses().get(1);
                assertTrue(a.assignedToFieldIsFrozen());
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("X".equals(d.typeInfo().simpleName)) {
                assertTrue(d.typeInfo().typeResolution.get().fieldsAccessedInRestOfPrimaryType());

                assertEquals(d.iteration() > 0, d.typeAnalysis().guardedForContainerPropertyDelays().isDone());
                assertEquals(d.iteration() > 0, d.typeAnalysis().guardedForInheritedContainerPropertyDelays().isDone());
                assertDv(d, 1, MultiLevel.CONTAINER_DV, Property.CONTAINER);
            }
            if ("Y".equals(d.typeInfo().simpleName)) {
                assertFalse(d.typeInfo().typeResolution.get().fieldsAccessedInRestOfPrimaryType());

                assertTrue(d.typeAnalysis().guardedForContainerPropertyDelays().isDone());
                assertTrue(d.typeAnalysis().guardedForInheritedContainerPropertyDelays().isDone());
                assertDv(d, MultiLevel.CONTAINER_DV, Property.CONTAINER);
                assertDv(d, MultiLevel.EFFECTIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
            }
            if ("Basics_24".equals(d.typeInfo().simpleName)) {
                assertFalse(d.typeInfo().typeResolution.get().fieldsAccessedInRestOfPrimaryType());

                assertEquals(d.iteration() > 0, d.typeAnalysis().guardedForContainerPropertyDelays().isDone());
                assertEquals(d.iteration() > 0, d.typeAnalysis().guardedForInheritedContainerPropertyDelays().isDone());
                assertDv(d, 3, MultiLevel.CONTAINER_DV, Property.CONTAINER);
            }
        };

        BreakDelayVisitor breakDelayVisitor = d -> {
            assertEquals("-----", d.delaySequence());
        };

        testClass("Basics_24_1", 0, 0, new DebugConfiguration.Builder()
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addBreakDelayVisitor(breakDelayVisitor)
                        .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder()
                        .setComputeFieldAnalyserAcrossAllMethods(true).build());
    }
}
