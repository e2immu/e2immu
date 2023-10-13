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

package org.e2immu.analyser.parser.own.util;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analyser.Stage;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.visitor.FieldAnalyserVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class Test_63_WGSimplified extends CommonTestRunner {

    public Test_63_WGSimplified() {
        super(true);
    }

    /*
    Example where a whole block, including a lambda, becomes unreachable.
     */
    @Test
    public void test_0() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("recursivelyComputeLinks".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "t".equals(pi.name)) {
                    if ("2".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<p:t>" : "nullable instance type T/*@Identity*/";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                    }
                    if ("3.0.0".equals(d.statementId())) {
                        assertEquals("<p:t>", d.currentValue().toString());
                        assertTrue(d.getProperty(Property.CONTEXT_NOT_NULL).isDelayed());
                    }
                }
                if ("node".equals(d.variableName())) {
                    if ("1".equals(d.statementId())) {
                        assertCurrentValue(d, 11, "nodeMap.get(t)");
                    }
                }
                if ("currentDistanceToT".equals(d.variableName())) {
                    if ("2".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<m:get>" : "distanceToStartingPoint.get(t)";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("3.0.0".equals(d.statementId())) {
                        assertCurrentValue(d, 11, "");
                        // IMPORTANT: CNN has to wait until we have a flow value for 3 to travel from 3.0.0 to 3
                        assertDv(d, 2, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                    }
                    if ("3".equals(d.statementId())) {
                        assertCurrentValue(d, 11, "distanceToStartingPoint.get(t)");
                        // this is the value that we cannot avoid: it cannot become NULLABLE because CNN works on the STATICALLY_ASSIGNED
                        // linking and does not wait until there are values!!
                        assertDv(d, 2, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                    }
                }
                if (d.variable() instanceof FieldReference fr && "dependsOn".equals(fr.fieldInfo().name)) {
                    if ("3.0.0".equals(d.statementId())) {
                        // eval
                        assertTrue(d.variableInfoContainer().hasEvaluation());
                        VariableInfo eval = d.variableInfoContainer().best(Stage.EVALUATION);
                        String linkedE = d.iteration() == 0
                                ? "currentDistanceToT:-1,distanceToStartingPoint:-1,node:-1,t:-1,this.nodeMap:-1"
                                : "node:-1,this.nodeMap:-1";
                        assertEquals(linkedE, eval.getLinkedVariables().toString());

                        // merge
                        assertEquals("node", fr.scope().toString());
                        String linked = d.iteration() == 0 ? "currentDistanceToT:-1,distanceToStartingPoint:-1,node:-1,t:-1,this.nodeMap:-1"
                                : "node:-1,this.nodeMap:-1";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                }
            }
            if ("accept".equals(d.methodInfo().name)) {
                assertEquals("$1", d.methodInfo().typeInfo.simpleName);
                assertEquals("0", d.statementId());
                if (d.variable() instanceof ParameterInfo pi && "t".equals(pi.name)) {
                    String expected = d.iteration() <= 1 ? "<m:get>" : "nullable instance type T/*@Identity*/";
                    assertEquals(expected, d.currentValue().toString());
                    assertTrue(d.variableInfoContainer().hasEvaluation());
                    assertDv(d, 2, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                    assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    String linked = d.iteration() == 0 ? "NOT_YET_SET" : "";
                    assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                }
                if ("currentDistanceToT".equals(d.variableName())) {
                    String expected = d.iteration() <= 1 ? "<m:get>" : "distanceToStartingPoint.get(t)";
                    assertEquals(expected, d.currentValue().toString());
                    assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    assertDv(d, 2, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                }
                if (d.variable() instanceof This thisVar && "$1".equals(thisVar.typeInfo.simpleName)) {
                    assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    String linked = d.iteration() == 0 ? "NOT_YET_SET" : "";
                    assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                }
                if (d.variable() instanceof This thisVar && "WGSimplified_0".equals(thisVar.typeInfo.simpleName)) {
                    assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    String linked = d.iteration() <= 1 ? "currentDistanceToT:-1,d:-1,distanceToN:-1,this.neutral:-1" : "";
                    assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                }
                if (d.variable() instanceof FieldReference fr && "dependsOn".equals(fr.fieldInfo().name)) {
                    assertEquals("node", fr.scope().toString());
                    String linked = d.iteration() == 0 ? "NOT_YET_SET" : "node:-1,this.nodeMap:-1";
                    assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    // THIS will get no value because as of iteration 11, the block is not reachable
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("recursivelyComputeLinks".equals(d.methodInfo().name)) {
                if ("3.0.0".equals(d.statementId())) {
                    // FIXME never reached??
                    assertEquals(d.iteration() >= BIG, d.statementAnalysis().flowData().getGuaranteedToBeReachedInMethod().isDone());
                }
            }
            if ("accept".equals(d.methodInfo().name)) {
                assertEquals("$1", d.methodInfo().typeInfo.simpleName);
                assertEquals("0", d.statementId());

                // FIXME never reached??
                assertEquals(d.iteration() >= BIG, d.statementAnalysis().methodLevelData().linksHaveBeenEstablished());
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("t".equals(d.fieldInfo().name)) {
                if (d.iteration() > 0) {
                    assertNotNull(d.haveError(Message.Label.PRIVATE_FIELD_NOT_READ_IN_PRIMARY_TYPE));
                }
            }
        };
        testClass("WGSimplified_0", 7, 1, new DebugConfiguration.Builder()
                //  .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build(), new AnalyserConfiguration.Builder().setComputeFieldAnalyserAcrossAllMethods(true).build());
    }

    /*
    The two errors here are indeed a logical problem in the example (e2immu) code. The current version of WeightedGraph
    does not have this problem anymore :-)
     */
    @Test
    public void test_1() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("recursivelyComputeLinks".equals(d.methodInfo().name)) {
                if ("node".equals(d.variableName())) {
                    if ("2".equals(d.statementId())) {
                        assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("3.0.0".equals(d.statementId())) {
                        assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if (d.variable() instanceof FieldReference fr && "dependsOn".equals(fr.fieldInfo().name)
                        && fr.scopeVariable() != null
                        && "node".equals(fr.scopeVariable().toString())) {
                    if ("3.0.0".equals(d.statementId())) {
                        assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if (d.variable() instanceof FieldReference fr && "nodeMap".equals(fr.fieldInfo().name)) {
                    if ("3".equals(d.statementId())) {
                        assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if (d.variable() instanceof This) {
                    if ("3".equals(d.statementId())) {
                        assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }
            if ("accept".equals(d.methodInfo().name) && "$1".equals(d.methodInfo().typeInfo.simpleName)) {
                if (d.variable() instanceof FieldReference fr && "nodeMap".equals(fr.fieldInfo().name)) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if (d.variable() instanceof FieldReference fr && "LINK_COMMON_HC".equals(fr.fieldInfo().name)) {
                    if ("0.0.2.0.0.0.2".equals(d.statementId())) {
                        assertDv(d, 2, MultiLevel.EFFECTIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
                        assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if (d.variable() instanceof This) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, 3, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("recursivelyComputeLinks".equals(d.methodInfo().name)) {
                assertDv(d, 2, DV.FALSE_DV, Property.TEMP_MODIFIED_METHOD);
            }
            if ("accept".equals(d.methodInfo().name) && "recursivelyComputeLinks".equals(d.enclosingMethod().name)) {
                assertEquals("$1", d.methodInfo().typeInfo.simpleName);
                assertDv(d, 3, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
        };

        testClass("WGSimplified_1", 2, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build(), new AnalyserConfiguration.Builder().setComputeFieldAnalyserAcrossAllMethods(true).build());
    }

    /*
    the test is here because without the recursive call, we are overwriting context modified
     */
    @Test
    public void test_2() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("accept".equals(d.methodInfo().name) && "recursivelyComputeLinks".equals(d.enclosingMethod().name)) {
                assertEquals("$1", d.methodInfo().typeInfo.simpleName);

                if (d.variable() instanceof FieldReference fr && "LINK_COMMON_HC".equals(fr.fieldInfo().name)) {
                    if ("0.0.0".equals(d.statementId()) || "0.0.2.0.0".equals(d.statementId())
                            || "0.0.2.1.1".equals(d.statementId())) {
                        fail();
                    }
                    if ("0.0.2.0.1".equals(d.statementId())) {
                        assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("0.0.2.1.2.0.0".equals(d.statementId())) {
                        assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("0.0.2.1.2.0.1".equals(d.statementId())) {
                        assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("recursivelyComputeLinks".equals(d.methodInfo().name)) {
                if ("3.0.0".equals(d.statementId())) {
                    // there are no properties, because the lambda is the first in the call chain... this is painful
                    //assertEquals("", d.statementAnalysis().propertiesFromSubAnalysersSortedToString());
                }
            }
            if ("accept".equals(d.methodInfo().name) && "$1".equals(d.methodInfo().typeInfo.simpleName)) {
                if ("0.0.0".equals(d.statementId())) {
                    String state = d.iteration() == 0
                            ? "(d.isDelayed()||d.le(maxValueIncl)||<null-check>)&&(!d.isDelayed()||followDelayed)&&(d.le(maxValueIncl)||<null-check>||followDelayed)"
                            : "(d.isDelayed()||d.le(maxValueIncl)||null==maxValueIncl)&&(!d.isDelayed()||followDelayed)&&(d.le(maxValueIncl)||null==maxValueIncl||followDelayed)";
                    assertEquals(state, d.localConditionManager().absoluteState(d.context()).toString());
                }
                if ("0.0.2.0.0".equals(d.statementId())) {
                    String state = switch (d.iteration()) {
                        case 0 ->
                                "<null-check>&&(d.isDelayed()||d.le(maxValueIncl)||<null-check>)&&(!d.isDelayed()||followDelayed)&&(d.le(maxValueIncl)||<null-check>||followDelayed)";
                        case 1 ->
                                "<null-check>&&(d.isDelayed()||d.le(maxValueIncl)||null==maxValueIncl)&&(!d.isDelayed()||followDelayed)&&(d.le(maxValueIncl)||null==maxValueIncl||followDelayed)";
                        default ->
                                "null==distanceToStartingPoint.get(n)&&(d.isDelayed()||d.le(maxValueIncl)||null==maxValueIncl)&&(!d.isDelayed()||followDelayed)&&(d.le(maxValueIncl)||null==maxValueIncl||followDelayed)";
                    };
                    assertEquals(state, d.localConditionManager().absoluteState(d.context()).toString());
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("recursivelyComputeLinks".equals(d.methodInfo().name)) {
                assertDv(d, 3, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
        };

        testClass("WGSimplified_2", 0, 4, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build(), new AnalyserConfiguration.Builder().setComputeFieldAnalyserAcrossAllMethods(true).build());
    }

    @Test
    public void test_3() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("accept".equals(d.methodInfo().name) && "recursivelyComputeLinks".equals(d.enclosingMethod().name)) {
                assertEquals("$1", d.methodInfo().typeInfo.simpleName);

                if (d.variable() instanceof FieldReference fr && "LINK_COMMON_HC".equals(fr.fieldInfo().name)) {
                    if ("0.0.0".equals(d.statementId()) || "0.0.2.0.0".equals(d.statementId())
                            || "0.0.2.1.1".equals(d.statementId())) {
                        fail();
                    }
                    if ("0.0.2.0.1".equals(d.statementId())) {
                        // because le is modifying, and at some point, maxValueIncl ~ LINK_COMMON_HC
                        assertDv(d, 3, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("0.0.2.1.2.0.0".equals(d.statementId())) {
                        assertDv(d, 3, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("0.0.2.1.2.0.1".equals(d.statementId())) {
                        assertDv(d, 3, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }
        };

        // unused variables, potential null pointer exceptions
        testClass("WGSimplified_3", 2, 4, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build(), new AnalyserConfiguration.Builder().setComputeFieldAnalyserAcrossAllMethods(true).build());
    }
}
