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
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                if (d.variable() instanceof FieldReference fr && "dependsOn".equals(fr.fieldInfo.name)) {
                    if ("3.0.0".equals(d.statementId())) {
                        // eval
                        assertTrue(d.variableInfoContainer().hasEvaluation());
                        VariableInfo eval = d.variableInfoContainer().best(Stage.EVALUATION);
                        String linkedE = d.iteration() == 0
                                ? "currentDistanceToT:-1,distanceToStartingPoint:-1,node:-1,t:-1,this.nodeMap:-1"
                                : "node:-1,this.nodeMap:-1";
                        assertEquals(linkedE, eval.getLinkedVariables().toString());

                        // merge
                        assertEquals("node", fr.scope.toString());
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
                if (d.variable() instanceof FieldReference fr && "dependsOn".equals(fr.fieldInfo.name)) {
                    assertEquals("node", fr.scope.toString());
                    String linked = d.iteration() == 0 ? "NOT_YET_SET" : "node:-1,this.nodeMap:-1";
                    assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    // THIS will get no value because as of iteration 11, the block is not reachable
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("recursivelyComputeLinks".equals(d.methodInfo().name)) {
                if ("3.0.0".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0 -> "currentDistanceToT={modified in context=cm@Parameter_w1;cm@Parameter_w2;initial:t@Method_recursivelyComputeLinks_1-E;initial:this.nodeMap@Method_recursivelyComputeLinks_1-C;link@NOT_YET_SET, not null in context=initial:t@Method_recursivelyComputeLinks_1-E;initial:this.nodeMap@Method_recursivelyComputeLinks_1-C;link@NOT_YET_SET, read=true:1}, dependsOn={modified in context=de:currentDistanceToT@Method_accept_0-E;initial:t@Method_recursivelyComputeLinks_1-E;initial:this.nodeMap@Method_recursivelyComputeLinks_1-C;link@NOT_YET_SET, not null in context=nullable:1}, distanceToStartingPoint={modified in context=de:currentDistanceToT@Method_accept_0-E;initial:t@Method_recursivelyComputeLinks_1-E;initial:this.nodeMap@Method_recursivelyComputeLinks_1-C;link@NOT_YET_SET, not null in context=de:currentDistanceToT@Method_accept_0-E;initial:t@Method_recursivelyComputeLinks_1-E;initial:this.nodeMap@Method_recursivelyComputeLinks_1-C}, neutral={modified in context=cm@Parameter_w1;cm@Parameter_w2;initial:t@Method_recursivelyComputeLinks_1-E;initial:this.nodeMap@Method_recursivelyComputeLinks_1-C;link@NOT_YET_SET, not null in context=not_null:5, read=true:1}, node={modified in context=de:currentDistanceToT@Method_accept_0-E;initial:t@Method_recursivelyComputeLinks_1-E;initial:this.nodeMap@Method_recursivelyComputeLinks_1-C;link@NOT_YET_SET, not null in context=nullable:1}, nodeMap={modified in context=de:currentDistanceToT@Method_accept_0-E;initial:t@Method_recursivelyComputeLinks_1-E;initial:this.nodeMap@Method_recursivelyComputeLinks_1-C;link@NOT_YET_SET, not null in context=nullable:1}, t={modified in context=de:currentDistanceToT@Method_accept_0-E;initial:t@Method_recursivelyComputeLinks_1-E;initial:this.nodeMap@Method_recursivelyComputeLinks_1-C;link@NOT_YET_SET, not null in context=de:currentDistanceToT@Method_accept_0-E;initial:t@Method_recursivelyComputeLinks_1-E;initial:this.nodeMap@Method_recursivelyComputeLinks_1-C}, this={modified in context=cm@Parameter_w1;cm@Parameter_w2;initial:t@Method_recursivelyComputeLinks_1-E;initial:this.nodeMap@Method_recursivelyComputeLinks_1-C;link@NOT_YET_SET, read=true:1}";
                        case 1 -> "currentDistanceToT={modified in context=initial:this.neutral@Method_accept_0-C, not null in context=initial:this.neutral@Method_accept_0-C, read=true:1}, dependsOn={modified in context=initial:this.neutral@Method_accept_0-C, not null in context=nullable:1}, distanceToStartingPoint={modified in context=initial:this.neutral@Method_accept_0-C, not null in context=initial:this.neutral@Method_accept_0-C}, neutral={modified in context=initial:this.neutral@Method_accept_0-C, not null in context=not_null:5, read=true:1}, node={modified in context=initial:this.neutral@Method_accept_0-C, not null in context=nullable:1}, nodeMap={modified in context=initial:this.neutral@Method_accept_0-C, not null in context=nullable:1}, t={modified in context=initial:this.neutral@Method_accept_0-C, not null in context=initial:this.neutral@Method_accept_0-C}, this={modified in context=initial:this.neutral@Method_accept_0-C, read=true:1}";
                        default -> "currentDistanceToT={modified in context=initial:t@Method_recursivelyComputeLinks_1-E;initial:this.nodeMap@Method_recursivelyComputeLinks_1-C, not null in context=content_not_null:13, read=true:1}, dependsOn={modified in context=initial:t@Method_recursivelyComputeLinks_1-E;initial:this.nodeMap@Method_recursivelyComputeLinks_1-C, not null in context=nullable:1}, distanceToStartingPoint={modified in context=initial:t@Method_recursivelyComputeLinks_1-E;initial:this.nodeMap@Method_recursivelyComputeLinks_1-C, not null in context=not_null:5}, neutral={modified in context=initial:t@Method_recursivelyComputeLinks_1-E;initial:this.nodeMap@Method_recursivelyComputeLinks_1-C, not null in context=not_null:5, read=true:1}, node={modified in context=initial:t@Method_recursivelyComputeLinks_1-E;initial:this.nodeMap@Method_recursivelyComputeLinks_1-C, not null in context=nullable:1}, nodeMap={modified in context=initial:t@Method_recursivelyComputeLinks_1-E;initial:this.nodeMap@Method_recursivelyComputeLinks_1-C, not null in context=nullable:1}, t={modified in context=initial:t@Method_recursivelyComputeLinks_1-E;initial:this.nodeMap@Method_recursivelyComputeLinks_1-C, not null in context=not_null:5}, this={modified in context=initial:t@Method_recursivelyComputeLinks_1-E;initial:this.nodeMap@Method_recursivelyComputeLinks_1-C, read=true:1}";
                    };
                    assertEquals(expected, d.statementAnalysis().propertiesFromSubAnalysersSortedToString());

                    assertEquals(d.iteration() >= 11, d.statementAnalysis().flowData().getGuaranteedToBeReachedInMethod().isDone());
                }
            }
            if ("accept".equals(d.methodInfo().name)) {
                assertEquals("$1", d.methodInfo().typeInfo.simpleName);
                assertEquals("0", d.statementId());
                assertEquals(d.iteration() >= 11, d.statementAnalysis().methodLevelData().linksHaveBeenEstablished());
            }
        };
        testClass("WGSimplified_0", 6, 2, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build(), new AnalyserConfiguration.Builder().setComputeFieldAnalyserAcrossAllMethods(true).build());
    }

}
