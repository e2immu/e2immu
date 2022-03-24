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
import org.e2immu.analyser.analysis.impl.FieldAnalysisImpl;
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
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class Test_63_DGSimplified extends CommonTestRunner {

    public Test_63_DGSimplified() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("comparator".equals(d.methodInfo().name)) {
                assertEquals("0", d.statementId());
                String expected = switch (d.iteration()) {
                    case 0, 1, 3, 4, 5 -> "ext_imm:this@Method_comparator_0-E";
                    case 2 -> "initial@Field_nodeMap";
                    default -> "";
                };
                assertEquals(expected, d.externalStatus().toString());
            }
            if ("test".equals(d.methodInfo().name) && "$1".equals(d.methodInfo().typeInfo.simpleName)) {
                if ("0".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0, 1, 4, 5, 6 -> "ext_imm:this@Method_test_0-E";
                        case 2, 3 -> "initial@Field_nodeMap";
                        default -> "";
                    };
                    assertEquals(expected, d.externalStatus().toString());
                }
            }
            if ("accept".equals(d.methodInfo().name) && "$4".equals(d.methodInfo().typeInfo.simpleName)) {
                if ("0.0.1".equals(d.statementId())) {
                    String value = d.iteration() <= 1 ? "<m:put>" : "nullable instance type Node<T>";
                    assertEquals(value, d.evaluationResult().value().toString());
                    String expected = switch (d.iteration()) {
                        case 0 -> "cm@Parameter_dependsOn;cm@Parameter_t;initial:copy.nodeMap@Method_accept_0.0.1-C;initial:node.dependsOn@Method_accept_0.0.0-C;mom@Parameter_dependsOn;mom@Parameter_t";
                        case 1 -> "initial@Field_dependsOn;initial@Field_t;mom@Parameter_t";
                        case 2 -> "mom@Parameter_t";
                        default -> "";
                    };
                    assertEquals(expected, d.evaluationResult().causesOfDelay().toString());
                }
            }
            if ("copyRemove".equals(d.methodInfo().name)) {
                if ("2".equals(d.statementId())) {
                    String expected = d.iteration() <= 5 ? "<m:freeze>" : "<no return value>";
                    assertEquals(expected, d.evaluationResult().value().toString());
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("copyRemove".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0 -> "copy.nodeMap=cm:copy.nodeMap@Method_accept_0:M;initial:node.dependsOn@Method_accept_0.0.0-C, copy=cm:copy@Method_accept_0:M;initial:node.dependsOn@Method_accept_0.0.0-C, org.e2immu.analyser.parser.own.util.testexample.DGSimplified_0.copyRemove(java.util.function.Predicate<T>):0:accept=cm:accept@Method_accept_0:M;initial:node.dependsOn@Method_accept_0.0.0-C";
                        case 1 -> "copy.nodeMap=cm:copy.nodeMap@Method_accept_0:M;cm:copy@Method_accept_0:M;initial@Field_dependsOn;initial@Field_t, copy=cm:copy.nodeMap@Method_accept_0:M;cm:copy@Method_accept_0:M;initial@Field_dependsOn;initial@Field_t, org.e2immu.analyser.parser.own.util.testexample.DGSimplified_0.copyRemove(java.util.function.Predicate<T>):0:accept=cm:accept@Method_accept_0:M;initial@Field_dependsOn;initial@Field_t";
                        default -> "copy.nodeMap=true:1, copy=true:1, org.e2immu.analyser.parser.own.util.testexample.DGSimplified_0.copyRemove(java.util.function.Predicate<T>):0:accept=true:1";
                    };
                    assertEquals(expected, d.statementAnalysis().variablesModifiedBySubAnalysers().map(Object::toString).sorted().collect(Collectors.joining(", ")));
                }
            }
            if ("accept".equals(d.methodInfo().name) && "$4".equals(d.methodInfo().typeInfo.simpleName)) {
                if ("0.0.1".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0 -> "cm:accept@Method_accept_0.0.1-E;cm:copy.nodeMap@Method_accept_0.0.1-E;cm:copy@Method_accept_0.0.1-E;cm:newDependsOn@Method_accept_0.0.1-E;cm:node.dependsOn@Method_accept_0.0.1-E;cm:node@Method_accept_0.0.1-E;cm:t@Method_accept_0.0.1-E;initial:node.dependsOn@Method_accept_0.0.0-C";
                        case 1 -> "cm:accept@Method_accept_0.0.1-E;cm:copy.nodeMap@Method_accept_0.0.1-E;cm:copy@Method_accept_0.0.1-E;cm:newDependsOn@Method_accept_0.0.1-E;cm:node.dependsOn@Method_accept_0.0.1-E;cm:node@Method_accept_0.0.1-E;cm:t@Method_accept_0.0.1-E;initial@Field_dependsOn;initial@Field_t";
                        case 2 -> "mom@Parameter_t";
                        default -> "";
                    };
                    assertEquals(expected, d.statementAnalysis().methodLevelData().linksHaveNotYetBeenEstablished().toString());
                }
                if ("0".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0 -> "cm:accept@Method_accept_0:M;cm:copy.nodeMap@Method_accept_0:M;cm:node.dependsOn@Method_accept_0:M;cm:node@Method_accept_0:M;cm:t@Method_accept_0:M;initial:node.dependsOn@Method_accept_0.0.0-C";
                        case 1 -> "cm:accept@Method_accept_0:M;cm:copy.nodeMap@Method_accept_0:M;cm:copy@Method_accept_0:M;cm:node.dependsOn@Method_accept_0:M;cm:node@Method_accept_0:M;cm:t@Method_accept_0:M;initial@Field_dependsOn;initial@Field_t";
                        default -> "";
                    };
                    assertEquals(expected, d.statementAnalysis().methodLevelData().linksHaveNotYetBeenEstablished().toString());
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("comparator".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    String expected = d.iteration() == 0 ? "<m:compare>"
                            : "e1.getValue().dependsOn$0.size()==e2.getValue().dependsOn$0.size()?null==backupComparator?0:backupComparator.compare(e1.getKey(),e2.getKey()):e1.getValue().dependsOn$0.size()-e2.getValue().dependsOn$0.size()";
                    assertEquals(expected, d.currentValue().toString());
                }
            }
            if ("copyRemove".equals(d.methodInfo().name)) {
                if ("copy".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("new DGSimplified_0<>()", d.currentValue().toString());
                        assertDv(d, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
                    }
                    if ("2".equals(d.statementId())) {
                        String expected = d.iteration() <= 5 ? "<mmc:copy>" : "instance type DGSimplified_0<T>";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 2, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
                        assertDv(d, 2, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if (d.variable() instanceof ParameterInfo pi && "accept".equals(pi.name)) {
                    if ("1".equals(d.statementId())) {
                        assertDv(d, 2, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("3".equals(d.statementId())) {
                        assertDv(d, 2, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }
            if ("accept".equals(d.methodInfo().name) && "$4".equals(d.methodInfo().typeInfo.simpleName)) {
                if (d.variable() instanceof FieldReference fr && "nodeMap".equals(fr.fieldInfo.name)) {
                    if ("copy".equals(fr.scope.toString())) {
                        if ("0.0.1".equals(d.statementId())) {
                            assertDv(d, 2, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                            assertEquals("copy.nodeMap:0,copy:2", d.variableInfo().getLinkedVariables().toString());
                            assertTrue(d.variableInfo().linkedVariablesIsSet());
                        }
                        if ("0".equals(d.statementId())) {
                            assertDv(d, 2, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                        }
                    } else fail("Scope " + fr.scope);
                }
                if ("copy".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        VariableInfo eval = d.variableInfoContainer().best(Stage.EVALUATION);
                        assertEquals("nullable instance type DGSimplified_0<T>", eval.getValue().toString());
                    }
                    if ("0.0.1".equals(d.statementId())) {
                        assertDv(d, 2, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                        String linked = d.iteration() == 0 ? "copy.nodeMap:-1,copy:0" : "copy.nodeMap:2,copy:0";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                        String expected = d.iteration() == 0 ? "initial:node.dependsOn@Method_accept_0.0.0-C" : "";
                        assertEquals(expected, d.variableInfo().getLinkedVariables().causesOfDelay().toString());
                    }
                }
                if (d.variable() instanceof ParameterInfo pi && "accept".equals(pi.name)) {
                    assertEquals("copyRemove", pi.owner.name);
                    if ("0.0.1".equals(d.statementId())) {
                        assertDv(d, 2, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("0".equals(d.statementId())) {
                        assertDv(d, 2, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                        String links = switch (d.iteration()) {
                            case 0 -> "initial:node.dependsOn@Method_accept_0.0.0-C";
                            case 1 -> "initial@Field_dependsOn;initial@Field_t";
                            default -> "";
                        };
                        assertEquals(links, d.variableInfo().getLinkedVariables().causesOfDelay().toString());
                    }
                }
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("nodeMap".equals(d.fieldInfo().name)) {
                assertEquals("instance type HashMap<T,Node<T>>", d.fieldAnalysis().getValue().toString());
                assertDv(d, MultiLevel.MUTABLE_DV, Property.EXTERNAL_IMMUTABLE);
                assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.EXTERNAL_NOT_NULL);
                assertDv(d, MultiLevel.CONTAINER_DV, Property.EXTERNAL_CONTAINER);
                assertDv(d, MultiLevel.NOT_IGNORE_MODS_DV, Property.EXTERNAL_IGNORE_MODIFICATIONS);

                assertEquals("", ((FieldAnalysisImpl.Builder) d.fieldAnalysis()).allLinksHaveBeenEstablished().toString());
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("accept".equals(d.methodInfo().name) && "$4".equals(d.methodInfo().typeInfo.simpleName)) {
                assertDv(d, 2, DV.TRUE_DV, Property.MODIFIED_METHOD);
            }
            if ("addNode".equals(d.methodInfo().name)) {
                // ... or not, mm@Method_addNode
                assertDv(d.p(0), 5, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                assertDv(d, 4, DV.TRUE_DV, Property.MODIFIED_METHOD);
            }
            if ("comparator".equals(d.methodInfo().name)) {
                String expected = d.iteration() == 0 ? "<m:comparator>"
                        : "e1.getValue().dependsOn$0.size()==e2.getValue().dependsOn$0.size()?null==backupComparator?0:backupComparator.compare(e1.getKey(),e2.getKey()):e1.getValue().dependsOn$0.size()-e2.getValue().dependsOn$0.size()";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("copyRemove".equals(d.methodInfo().name)) {
                String delay = switch (d.iteration()) {
                    case 0 -> "cm:copy.nodeMap@Method_accept_0:M;cm:copy@Method_accept_0:M;initial:node.dependsOn@Method_accept_0.0.0-C";
                    case 1 -> "cm:copy.nodeMap@Method_accept_0:M;cm:copy@Method_accept_0:M;initial@Field_dependsOn;initial@Field_t";
                    default -> "true:1";
                };
                assertDv(d, delay, 2, DV.TRUE_DV, Property.MODIFIED_METHOD);
            }
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("DGSimplified_0".equals(d.typeInfo().simpleName)) {
                String delay = switch (d.iteration()) {
                    case 0 -> "final@Field_nodeMap";
                    case 1 -> "initial@Field_nodeMap";
                    case 2, 3 -> "mm@Method_addNode";
                    case 4 -> "assign_to_field@Parameter_t";
                    default -> "";
                };
                assertDv(d, delay, 5, MultiLevel.EVENTUALLY_E1IMMUTABLE_DV, Property.IMMUTABLE);
                assertDv(d, 3, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER); // TODO verify this
            }
        };
        // TODO improve on errors
        testClass("DGSimplified_0", 7, 2, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build(), new AnalyserConfiguration.Builder()
                .setComputeFieldAnalyserAcrossAllMethods(true).build());
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
                                case 0, 1 -> "<f:dependsOn>";
                                default -> "nullable instance type List<T>";
                            };
                            assertEquals(expected, d.currentValue().toString());
                        }
                        if ("3.0.0".equals(d.statementId())) { // forEach() call
                            String expected = d.iteration() <= 1 ? "<f:dependsOn>" : "nullable instance type List<T>";
                            assertEquals(expected, d.currentValue().toString());
                            String delays = switch (d.iteration()) {
                                case 0 -> "initial:node.dependsOn@Method_recursivelyComputeDependencies_3-C;initial:this.nodeMap@Method_recursivelyComputeDependencies_1-C;initial@Field_dependsOn";
                                case 1 -> "initial:node.dependsOn@Method_recursivelyComputeDependencies_3-C;initial:this.nodeMap@Method_recursivelyComputeDependencies_1-C;initial@Field_dependsOn;initial@Field_t";
                                default -> "";
                            };
                            assertEquals(delays, d.currentValue().causesOfDelay().toString());
                        }
                    } else if ("nodeMap.get(t)".equals(fr.scope.toString())) {
                        if ("3".equals(d.statementId())) {
                            String expected = switch (d.iteration()) {
                                case 0, 1 -> "<f:nodeMap.get(t).dependsOn>";
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
                    assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
                }
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("dependsOn".equals(d.fieldInfo().name)) {
                String expected = "dependsOn,null";
                assertEquals(expected, ((FieldAnalysisImpl.Builder) d.fieldAnalysis()).sortedValuesString());
            }
        };
        // TODO too many errors
        testClass("DGSimplified_1", 8, 2, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build(), new AnalyserConfiguration.Builder().setComputeFieldAnalyserAcrossAllMethods(true).build());
    }

}
