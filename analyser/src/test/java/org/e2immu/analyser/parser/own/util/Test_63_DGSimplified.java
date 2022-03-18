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
import org.e2immu.analyser.analysis.impl.FieldAnalysisImpl;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.EvaluationResultVisitor;
import org.e2immu.analyser.visitor.FieldAnalyserVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class Test_63_DGSimplified extends CommonTestRunner {

    public Test_63_DGSimplified() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        testClass("DGSimplified_0", 5, 0, new DebugConfiguration.Builder()
                .build(), new AnalyserConfiguration.Builder().setComputeFieldAnalyserAcrossAllMethods(true).build());
    }

    @Test
    public void test_1() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("accept".equals(d.methodInfo().name) && "$1".equals(d.methodInfo().typeInfo.simpleName)) {
                if ("0".equals(d.statementId())) {
                    assertEquals("!result.contains(d)", d.evaluationResult().value().toString());
                }
                if ("0.0.0".equals(d.statementId())) {
                    assertEquals("<no return value>", d.evaluationResult().value().toString());
                    assertEquals("", d.evaluationResult().causesOfDelay().toString());
                }
            }
            if ("recursivelyComputeDependencies".equals(d.methodInfo().name)) {
                if ("3.0.0".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0 -> "initial:node.dependsOn@Method_recursivelyComputeDependencies_3-C;initial:this.nodeMap@Method_recursivelyComputeDependencies_1-C";
                        case 1 -> "initial:node.dependsOn@Method_recursivelyComputeDependencies_3-C;initial:this.nodeMap@Method_recursivelyComputeDependencies_1-C;initial@Field_dependsOn;initial@Field_t";
                        case 2 -> "cnn:this.nodeMap.get(t).dependsOn@Method_recursivelyComputeDependencies_3-C";
                        default -> "";
                    };
                    assertEquals(expected, d.evaluationResult().causesOfDelay().toString());
                }
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("recursivelyComputeDependencies".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "nodeMap".equals(fr.fieldInfo.name)) {
                    if ("3".equals(d.statementId())) {
                        assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if (d.variable() instanceof FieldReference fr && "dependsOn".equals(fr.fieldInfo.name)) {
                    if ("node".equals(fr.scope.toString())) {
                        if ("3".equals(d.statementId())) {
                            String expected = switch (d.iteration()) {
                                case 0, 1, 2 -> "<f:dependsOn>";
                                default -> "nullable instance type List<T>";
                            };
                            assertEquals(expected, d.currentValue().toString());
                        }
                        if ("3.0.0".equals(d.statementId())) { // forEach() call
                            String expected = d.iteration() <= 2 ? "<f:dependsOn>" : "nullable instance type List<T>";
                            assertEquals(expected, d.currentValue().toString());
                            String delays = switch (d.iteration()) {
                                case 0 -> "initial:node.dependsOn@Method_recursivelyComputeDependencies_3-C;initial:this.nodeMap@Method_recursivelyComputeDependencies_1-C;initial@Field_dependsOn";
                                case 1 -> "initial:node.dependsOn@Method_recursivelyComputeDependencies_3-C;initial:this.nodeMap@Method_recursivelyComputeDependencies_1-C;initial@Field_dependsOn;initial@Field_t";
                                case 2 -> "cnn:this.nodeMap.get(t).dependsOn@Method_recursivelyComputeDependencies_3-C";
                                default -> "";
                            };
                            assertEquals(delays, d.currentValue().causesOfDelay().toString());
                        }
                    } else if ("nodeMap.get(t)".equals(fr.scope.toString())) {
                        if ("3".equals(d.statementId())) {
                            String expected = switch (d.iteration()) {
                                case 0, 1, 2 -> "<f:nodeMap.get(t).dependsOn>";
                                default -> "nullable instance type List<T>";
                            };
                            assertEquals(expected, d.currentValue().toString());
                        }
                    } else fail("Scope " + fr.scope);
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("recursivelyComputeDependencies".equals(d.methodInfo().name)) {
                assertDv(d, 2, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("accept".equals(d.methodInfo().name)) {
                if ("$1".equals(d.methodInfo().typeInfo.simpleName)) { // recursivelyComputeDependencies
                    assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
                } else if ("$3".equals(d.methodInfo().typeInfo.simpleName)) {// visit
                    assertDv(d, 2, DV.TRUE_DV, Property.MODIFIED_METHOD); // TODO ensure this is correct
                }
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("dependsOn".equals(d.fieldInfo().name)) {
                String expected = "dependsOn,null";
                assertEquals(expected, ((FieldAnalysisImpl.Builder) d.fieldAnalysis()).sortedValuesString());
            }
        };
        // FIXME too many errors
        testClass("DGSimplified_1", 8, 3, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build(), new AnalyserConfiguration.Builder().setComputeFieldAnalyserAcrossAllMethods(true).build());
    }

}
