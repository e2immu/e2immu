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

import static org.junit.jupiter.api.Assertions.*;

public class Test_63_DGSimplified extends CommonTestRunner {

    public Test_63_DGSimplified() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("accept".equals(d.methodInfo().name) && "$4".equals(d.methodInfo().typeInfo.simpleName)) {
                if ("0.0.1".equals(d.statementId())) {
                    String value = d.iteration() <= 1 ? "<m:put>" : "nullable instance type Node<T>";
                    assertEquals(value, d.evaluationResult().value().toString());
                    String expected = switch (d.iteration()) {
                        case 0 -> "initial:copy.nodeMap@Method_accept_0.0.1-C;initial:node.dependsOn@Method_accept_0.0.0-C";
                        case 1 -> "initial@Field_dependsOn;initial@Field_t";
                        default -> "";
                    };
                    assertEquals(expected, d.evaluationResult().causesOfDelay().toString());
                }
            }
            if ("copyRemove".equals(d.methodInfo().name)) {
                if ("2".equals(d.statementId())) {
                    String expected = d.iteration() == 0 ? "<m:freeze>" : "<no return value>";
                    assertEquals(expected, d.evaluationResult().value().toString());
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("copyRemove".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0 -> "accept={modified in context=initial:node.dependsOn@Method_accept_0.0.0-C, not null in context=initial:node.dependsOn@Method_accept_0.0.0-C, read=true:1}, copy={modified in context=initial:node.dependsOn@Method_accept_0.0.0-C, not null in context=not_null:5, read=true:1}, nodeMap={modified in context=initial:node.dependsOn@Method_accept_0.0.0-C, not null in context=not_null:5, read=true:1}";
                        case 1 -> "accept={modified in context=initial@Field_dependsOn;initial@Field_t, not null in context=content_not_null:13, read=true:1}, copy={modified in context=initial@Field_dependsOn;initial@Field_t, not null in context=not_null:5, read=true:1}, nodeMap={modified in context=initial@Field_dependsOn;initial@Field_t, not null in context=not_null:5, read=true:1}";
                        default -> "accept={modified in context=true:1, not null in context=content_not_null:13, read=true:1}, copy={modified in context=true:1, not null in context=not_null:5, read=true:1}, nodeMap={modified in context=true:1, not null in context=not_null:5, read=true:1}";
                    };
                    assertEquals(expected, d.statementAnalysis().propertiesFromSubAnalysersSortedToString());
                }
            }
            if ("accept".equals(d.methodInfo().name) && "$4".equals(d.methodInfo().typeInfo.simpleName)) {
                if ("0.0.1".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0 -> "cm@Parameter_dependsOn;cm@Parameter_t;initial:node.dependsOn@Method_accept_0.0.0-C;mom@Parameter_dependsOn;mom@Parameter_t";
                        case 1 -> "initial@Field_dependsOn;initial@Field_t";
                        default -> "";
                    };
                    assertEquals(expected, d.statementAnalysis().methodLevelData().linksHaveNotYetBeenEstablished().toString());
                }
                if ("0".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0 -> "initial:node.dependsOn@Method_accept_0.0.0-C";
                        case 1 -> "initial@Field_dependsOn;initial@Field_t";
                        default -> "";
                    };
                    assertEquals(expected, d.statementAnalysis().methodLevelData().linksHaveNotYetBeenEstablished().toString());
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("comparator".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    String expected = d.iteration() <= 1 ? "<m:compare>"
                            : "/*inline compare*/(e1.getValue()).dependsOn$0.size()==(e2.getValue()).dependsOn$0.size()?null==backupComparator?0:backupComparator.compare(e1.getKey(),e2.getKey()):(e1.getValue()).dependsOn$0.size()-(e2.getValue()).dependsOn$0.size()";
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
                        String expected = d.iteration() == 0 ? "<v:copy>" : "instance type DGSimplified_0<T>";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 1, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
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
                            assertDv(d, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                            String lvs = switch (d.iteration()) {
                                case 0 -> "accept:-1,copy:-1,newDependsOn:-1,node.dependsOn:-1,node:-1,t:-1";
                                case 1 -> "accept:-1,copy:2,newDependsOn:-1,node.dependsOn:-1,node:-1,t:-1";
                                default -> "accept:3,copy:2,newDependsOn:3,node.dependsOn:3,node:3,t:3";
                            };
                            assertEquals(lvs, d.variableInfo().getLinkedVariables().toString());
                        }
                        if ("0".equals(d.statementId())) {
                            assertDv(d, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                        }
                    } else fail("Scope " + fr.scope);
                }
                if ("copy".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        VariableInfo eval = d.variableInfoContainer().best(Stage.EVALUATION);
                        assertEquals("instance type DGSimplified_0<T>", eval.getValue().toString());
                    }
                    if ("0.0.1".equals(d.statementId())) {
                        assertDv(d, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                        String lvs = switch (d.iteration()) {
                            case 0 -> "accept:-1,copy.nodeMap:-1,newDependsOn:-1,node.dependsOn:-1,node:-1,t:-1";
                            case 1 -> "accept:-1,copy.nodeMap:2,newDependsOn:-1,node.dependsOn:-1,node:-1,t:-1";
                            default -> "accept:3,copy.nodeMap:2,newDependsOn:3,node.dependsOn:3,node:3,t:3";
                        };
                        assertEquals(lvs, d.variableInfo().getLinkedVariables().toString());
                    }
                }
                if (d.variable() instanceof ParameterInfo pi && "accept".equals(pi.name)) {
                    assertEquals("copyRemove", pi.owner.name);
                    if ("0.0.1".equals(d.statementId())) {
                        assertDv(d, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("0".equals(d.statementId())) {
                        assertDv(d, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
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
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("addNode".equals(d.methodInfo().name)) {
                // ... or not, mm@Method_addNode
                assertDv(d.p(0), 3, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                assertDv(d, 2, DV.TRUE_DV, Property.MODIFIED_METHOD);
            }
            if ("comparator".equals(d.methodInfo().name)) {
                String expected = d.iteration() <= 1 ? "<m:comparator>"
                        : "/*inline comparator*//*inline compare*/(e1.getValue()).dependsOn$0.size()==(e2.getValue()).dependsOn$0.size()?null==backupComparator?0:backupComparator.compare(e1.getKey(),e2.getKey()):(e1.getValue()).dependsOn$0.size()-(e2.getValue()).dependsOn$0.size()";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("copyRemove".equals(d.methodInfo().name)) {
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("DGSimplified_0".equals(d.typeInfo().simpleName)) {
                assertDv(d, 3, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER); // TODO verify this
            }
        };
        // TODO improve on errors
        testClass("DGSimplified_0", 6, 1, new DebugConfiguration.Builder()
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
                    assertDv(d, 3, DV.FALSE_DV, Property.MODIFIED_METHOD);
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
        testClass("DGSimplified_1", 6, 1, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build(), new AnalyserConfiguration.Builder().setComputeFieldAnalyserAcrossAllMethods(true).build());
    }

    @Test
    public void test_2() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("reverse".equals(d.methodInfo().name)) {
                if ("1.0.0".equals(d.statementId())) {
                    String expected = d.iteration() <= 1 ? "<m:addNode>" : "<no return value>";
                    assertEquals(expected, d.evaluationResult().value().toString());
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("reverse".equals(d.methodInfo().name)) {
                if ("1.0.0".equals(d.statementId())) {
                    String expected = d.iteration() <= 1 ? "<m:addNode>" : "<no return value>";
                    assertEquals(expected, d.statementAnalysis().stateData().valueOfExpression.get().toString());
                }
            }
        };
        testClass("DGSimplified_2", 1, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build(), new AnalyserConfiguration.Builder().setComputeFieldAnalyserAcrossAllMethods(true).build());
    }

    @Test
    public void test_3() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("reverse".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertEquals("set", d.evaluationResult().value().toString());
                }
                if ("0.0.1".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0 -> "<null-check>";
                        case 1, 2, 3, 4, 5, 6 -> "null!=<f:node.dependsOn>";
                        default -> "null!=(nodeMap.get(t)).dependsOn";
                    };
                    assertEquals(expected, d.evaluationResult().value().toString());
                    String delays = switch (d.iteration()) {
                        case 0 -> "initial:this.nodeMap@Method_reverse_0.0.0-C";
                        case 1 -> "initial@Field_dependsOn;initial@Field_t";
                        case 2, 3, 4, 5, 6 -> "initial:this.nodeMap@Method_reverse_0.0.0-C;initial@Field_dependsOn;initial@Field_t";
                            default -> "";
                    };
                    assertEquals(delays, d.evaluationResult().causesOfDelay().toString());
                }
                if ("0.0.1.0.0.0.0".equals(d.statementId())) {
                    String expected = d.iteration() <= BIG ? "<m:contains>" : "set.contains(d)";
                    assertEquals(expected, d.evaluationResult().value().toString());
                    String delays = switch (d.iteration()) {
                        case 0 -> "initial:node@Method_reverse_0.0.1.0.0-C;initial:set@Method_reverse_0.0.0-E;initial:this.nodeMap@Method_reverse_0.0.0-C";
                        default -> "initial:this.nodeMap@Method_reverse_0.0.0-C;initial@Field_dependsOn;initial@Field_t";
                        // default -> "";
                    };
                    assertEquals(delays, d.evaluationResult().causesOfDelay().toString());
                }
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("reverse".equals(d.methodInfo().name)) {

                if (d.variable() instanceof FieldReference fr && "dependsOn".equals(fr.fieldInfo.name)) {
                    if ("0.0.1".equals(d.statementId())) {
                        assertNotNull(fr.scopeVariable);
                        assertEquals("node", fr.scopeVariable.toString());
                        assertEquals("node", fr.scope.toString());

                        assertTrue(d.variableInfoContainer().isInitial());
                        VariableInfo initial = d.variableInfoContainer().getPreviousOrInitial();
                        String expected = d.iteration() == 0 ? "<f:dependsOn>" : "nullable instance type List<T>";
                        assertEquals(expected, initial.getValue().toString());

                        assertTrue(d.variableInfoContainer().hasEvaluation());
                        VariableInfo eval = d.variableInfoContainer().best(Stage.EVALUATION);
                        String expectedEM = d.iteration() <= BIG ? "<f:dependsOn>" : "nullable instance type List<T>";
                        assertEquals(expectedEM, eval.getValue().toString());

                        assertTrue(d.variableInfoContainer().hasMerge());
                        assertEquals(expectedEM, d.currentValue().toString());
                    }
                }
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("dependsOn".equals(d.fieldInfo().name)) {
                assertDv(d, DV.TRUE_DV, Property.FINAL);
                // value of the parameter
                assertEquals("dependsOn", d.fieldAnalysis().getValue().toString());
            }
        };
        testClass("DGSimplified_3", 0, 2, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build(), new AnalyserConfiguration.Builder().setComputeFieldAnalyserAcrossAllMethods(true).build());
    }

    @Test
    public void test_4() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("sorted".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "reportIndependent".equals(pi.name)) {
                    if ("3.0.1.0.3.0.2.0.0".equals(d.statementId())) {
                        assertDv(d, 1, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                    }
                }
            }
            if ("dependencies".equals(d.variableName())) {
                if ("3.0.1.0.2.1.0".equals(d.statementId())) {
                    String expected = d.iteration() == 0 ? "<f:entry.getValue().dependsOn>"
                            : "(entry.getValue()).dependsOn$0";
                    assertEquals(expected, d.currentValue().toString());
                }
            }
            if ("compare".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "backupComparator".equals(pi.name)) {
                    assertEquals("$1", d.methodInfo().typeInfo.simpleName);
                    if ("1.0.0".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<p:backupComparator>"
                                : "nullable instance type Comparator<T>/*@Identity*/";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 1, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                    }
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("sorted".equals(d.methodInfo().name)) {
                if ("3.0.1.0.3.0.2.0.0".equals(d.statementId())) {
                    String expected = d.iteration() == 0 ? "<c:boolean>" : "null!=reportIndependent";
                    assertEquals(expected, d.condition().toString());
                }
                if ("3.0.1.0.2.1.0".equals(d.statementId())) {
                    String expected = d.iteration() == 0 ? "!<null-check>&&!<m:isEmpty>"
                            : "!(entry.getValue()).dependsOn$0.isEmpty()&&null!=(entry.getValue()).dependsOn$0";
                    assertEquals(expected, d.condition().toString());
                }
            }
        };
        testClass("DGSimplified_4", 0, 4, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build(), new AnalyserConfiguration.Builder().setComputeFieldAnalyserAcrossAllMethods(true).build());
    }
}
