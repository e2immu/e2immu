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
import org.e2immu.analyser.model.variable.VariableNature;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.Message;
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
                    String value = d.iteration() < 4 ? "<m:put>" : "nullable instance type Node<T>";
                    assertEquals(value, d.evaluationResult().value().toString());
                    assertEquals(d.iteration() >= 4, d.evaluationResult().causesOfDelay().isDone());
                }
            }
            if ("copyRemove".equals(d.methodInfo().name)) {
                if ("2".equals(d.statementId())) {
                    String expected = d.iteration() < 4 ? "<m:freeze>" : "<no return value>";
                    assertEquals(expected, d.evaluationResult().value().toString());
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("accept".equals(d.methodInfo().name) && "$4".equals(d.methodInfo().typeInfo.simpleName)) {
                if ("0.0.1".equals(d.statementId())) {
                    assertEquals(d.iteration() >= 3, d.statementAnalysis().methodLevelData().linksHaveBeenEstablished());
                }
                if ("0".equals(d.statementId())) {
                    assertEquals(d.iteration() >= 3, d.statementAnalysis().methodLevelData().linksHaveBeenEstablished());
                }
            }
            if ("removeAsManyAsPossible".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    String state = d.iteration() < 2 ? "!<m:get>" : "!changed.get()";
                    assertEquals(state, d.state().toString());
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("comparator".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    String expected = d.iteration() < 4 ? "<m:compare>"
                            : "/*inline compare*/(e1.getValue()).dependsOn$0.size()==(e2.getValue()).dependsOn$0.size()?null==backupComparator?0:backupComparator.compare(e1.getKey(),e2.getKey()):(e1.getValue()).dependsOn$0.size()-(e2.getValue()).dependsOn$0.size()";
                    assertEquals(expected, d.currentValue().toString());
                }
            }
            if ("copyRemove".equals(d.methodInfo().name)) {
                if ("copy".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("new DGSimplified_0<>()", d.currentValue().toString());
                        /*
                         IMPORTANT: because it is self, it is mutable. As a consequence, it cannot be in a "BEFORE" state
                         because it is not eventual. The regular ComputeTypeImmutable.methodsOf will exclude it because the
                         field is not 'this'.
                         */
                        assertDv(d, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
                    }
                    if ("2".equals(d.statementId())) {
                        String expected = d.iteration() < 4 ? "<v:copy>" : "instance type DGSimplified_0<T>";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 4, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
                        assertDv(d, 3, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if (d.variable() instanceof ParameterInfo pi && "accept".equals(pi.name)) {
                    if ("1".equals(d.statementId())) {
                        assertDv(d, 3, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("3".equals(d.statementId())) {
                        assertDv(d, 3, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }
            if ("accept".equals(d.methodInfo().name) && "$4".equals(d.methodInfo().typeInfo.simpleName)) {
                if (d.variable() instanceof FieldReference fr && "nodeMap".equals(fr.fieldInfo.name)) {
                    if ("copy".equals(fr.scope.toString())) {
                        if ("0.0.1".equals(d.statementId())) {
                            assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                            String lvs = d.iteration() < 3 ? "accept:-1,copy:-1,newDependsOn:-1,node.dependsOn:-1,node:-1,t:-1"
                                    : "copy:2,newDependsOn:4,node.dependsOn:4,node:4";
                            assertEquals(lvs, d.variableInfo().getLinkedVariables().toString());
                        }
                        if ("0".equals(d.statementId())) {
                            assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                        }
                    } else fail("Scope " + fr.scope);
                }
                if ("copy".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        VariableInfo eval = d.variableInfoContainer().best(Stage.EVALUATION);
                        assertEquals("instance type DGSimplified_0<T>", eval.getValue().toString());
                    }
                    if ("0.0.1".equals(d.statementId())) {
                        assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                        String lvs = d.iteration() == 0 ? "NOT_YET_SET"
                                : d.iteration() < 3 ? "accept:-1,copy.nodeMap:-1,newDependsOn:-1,node.dependsOn:-1,node:-1,t:-1"
                                : "copy.nodeMap:4,newDependsOn:4,node.dependsOn:4,node:4";
                        assertEquals(lvs, d.variableInfo().getLinkedVariables().toString());
                    }
                }
                if (d.variable() instanceof ParameterInfo pi && "accept".equals(pi.name)) {
                    assertEquals("copyRemove", pi.owner.name);
                    if ("0.0.0".equals(d.statementId())) {
                        String linked = d.iteration() == 0 ? "NOT_YET_SET"
                                : d.iteration() == 1 ? "newDependsOn:-1,node.dependsOn:-1,node:-1"
                                : "";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("0.0.1".equals(d.statementId())) {
                        String linked = d.iteration() == 0 ? "NOT_YET_SET"
                                : d.iteration() == 1 ? "copy.nodeMap:-1,copy:-1,newDependsOn:-1,node.dependsOn:-1,node:-1,t:-1"
                                : "";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, 3, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("0".equals(d.statementId())) {
                        assertDv(d, 3, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }
            if ("removeAsManyAsPossible".equals(d.methodInfo().name)) {
                if ("changed".equals(d.variableName())) {
                    if ("1".equals(d.statementId())) {
                        assertTrue(d.variableInfoContainer().variableNature() instanceof VariableNature.VariableDefinedOutsideLoop);
                        assertCurrentValue(d, 5, "changed$1.get()?instance type AtomicBoolean:new AtomicBoolean(true)");
                        assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("1.0.1".equals(d.statementId())) {
                        String expected = d.iteration() < 5 ? "<vl:changed>" : "instance type AtomicBoolean";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("nodeMap".equals(d.fieldInfo().name)) {
                assertEquals("instance type HashMap<T,Node<T>>", d.fieldAnalysis().getValue().toString());
                assertDv(d, MultiLevel.MUTABLE_DV, Property.EXTERNAL_IMMUTABLE);
                assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.EXTERNAL_NOT_NULL);
                assertDv(d, 4, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER_RESTRICTION);
                assertDv(d, MultiLevel.CONTAINER_DV, Property.CONTAINER);
                assertDv(d, MultiLevel.NOT_IGNORE_MODS_DV, Property.EXTERNAL_IGNORE_MODIFICATIONS);

                String links = d.iteration() == 0 ? "link@Field_nodeMap" : "";
                assertEquals(links, ((FieldAnalysisImpl.Builder) d.fieldAnalysis()).allLinksHaveBeenEstablished().toString());
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("accept".equals(d.methodInfo().name) && "$4".equals(d.methodInfo().typeInfo.simpleName)) {
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("addNode".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 6, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                assertDv(d, 4, DV.TRUE_DV, Property.MODIFIED_METHOD);
                String pce = d.iteration() == 0
                        ? "Precondition[expression=<precondition>, causes=[]]"
                        : "Precondition[expression=!this.isFrozen(), causes=[companionMethod:ensureNotFrozen$Precondition, companionMethod:ensureNotFrozen$Precondition]]";
                assertEquals(pce, d.methodAnalysis().getPreconditionForEventual().toString());
            }
            if ("comparator".equals(d.methodInfo().name)) {
                String expected = d.iteration() < 4 ? "<m:comparator>"
                        : "/*inline comparator*//*inline compare*/(e1.getValue()).dependsOn$0.size()==(e2.getValue()).dependsOn$0.size()?null==backupComparator?0:backupComparator.compare(e1.getKey(),e2.getKey()):(e1.getValue()).dependsOn$0.size()-(e2.getValue()).dependsOn$0.size()";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                assertDv(d, 3, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("copyRemove".equals(d.methodInfo().name)) {
                assertDv(d, 3, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("DGSimplified_0".equals(d.typeInfo().simpleName)) {
                // removeAsManyAsPossible is public and has a modified parameter
                assertDv(d, 5, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER);
            }
        };

        testClass("DGSimplified_0", 0, 1, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
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
                        assertDv(d, 4, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if (d.variable() instanceof FieldReference fr && "dependsOn".equals(fr.fieldInfo.name)) {
                    if ("node".equals(fr.scope.toString())) {
                        if ("3".equals(d.statementId())) {
                            assertCurrentValue(d, 4, "nullable instance type List<T>");
                        }
                        if ("3.0.0".equals(d.statementId())) { // forEach() call
                            String expected = d.iteration() < 4 ? "<f:node.dependsOn>" : "nullable instance type List<T>";
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
                if (d.variable() instanceof ParameterInfo pi && "result".equals(pi.name)) {
                    if ("3.0.0".equals(d.statementId())) {
                        assertTrue(d.variableInfoContainer().hasEvaluation());
                        VariableInfo eval = d.variableInfoContainer().best(Stage.EVALUATION);
                        assertEquals("", eval.getLinkedVariables().toString());
                    }
                }
            }
            if ("accept".equals(d.methodInfo().name) && "$1".equals(d.methodInfo().typeInfo.simpleName)) {
                if (d.variable() instanceof ParameterInfo pi && "result".equals(pi.name)) {
                    if ("0".equals(d.statementId())) {
                        assertTrue(d.variableInfoContainer().hasEvaluation());
                        VariableInfo eval = d.variableInfoContainer().best(Stage.EVALUATION);
                        String linked = d.iteration() == 0 ? "NOT_YET_SET" : "";
                        assertEquals(linked, eval.getLinkedVariables().toString());
                        assertTrue(d.variableInfoContainer().hasMerge());
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("0.0.0".equals(d.statementId())) {
                        String linked = d.iteration() == 0 ? "NOT_YET_SET" : "";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("recursivelyComputeDependencies".equals(d.methodInfo().name)) {
                assertDv(d, 4, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("accept".equals(d.methodInfo().name)) {
                if ("$1".equals(d.methodInfo().typeInfo.simpleName)) { // recursivelyComputeDependencies
                    assertDv(d, 5, DV.FALSE_DV, Property.MODIFIED_METHOD);
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
        testClass("DGSimplified_1", 5, 1, new DebugConfiguration.Builder()
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
                    String expected = d.iteration() < 21 ? "<m:addNode>" : "<no return value>";
                    assertEquals(expected, d.evaluationResult().value().toString());
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("reverse".equals(d.methodInfo().name)) {
                if ("1.0.0".equals(d.statementId())) {
                    String expected = d.iteration() < 21 ? "<m:addNode>" : "<no return value>";
                    assertEquals(expected, d.statementAnalysis().stateData().valueOfExpression.get().toString());
                }
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("t".equals(d.fieldInfo().name)) {
                if (d.iteration() > 0) {
                    assertNotNull(d.haveError(Message.Label.PRIVATE_FIELD_NOT_READ_IN_PRIMARY_TYPE));
                }
            }
        };
        testClass("DGSimplified_2", 2, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
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
                        case 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 -> "null!=<f:node.dependsOn>";
                        default -> "null!=(nodeMap.get(t)).dependsOn";
                    };
                    assertEquals(expected, d.evaluationResult().value().toString());
                    String delays = switch (d.iteration()) {
                        case 0 -> "initial:this.nodeMap@Method_reverse_0.0.0-C";
                        case 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 -> "link@Field_nodeMap";
                        default -> "";
                    };
                    assertEquals(delays, d.evaluationResult().causesOfDelay().toString());
                }
                if ("0.0.1.0.0.0.0".equals(d.statementId())) {
                    String expected = d.iteration() < 11 ? "<m:contains>" : "set.contains(d)";
                    assertEquals(expected, d.evaluationResult().value().toString());
                    String delays = switch (d.iteration()) {
                        case 0 -> "initial:node@Method_reverse_0.0.1.0.0-C;initial:set@Method_reverse_0.0.0-E;initial:this.nodeMap@Method_reverse_0.0.0-C";
                        case 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 -> "initial:set@Method_reverse_0.0.0-E;initial:this.nodeMap@Method_reverse_0.0.0-C;link@Field_nodeMap";
                        default -> "";
                    };
                    assertEquals(delays, d.evaluationResult().causesOfDelay().toString());
                }
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("reverse".equals(d.methodInfo().name)) {

                if (d.variable() instanceof FieldReference fr && "nodeMap".equals(fr.fieldInfo.name)) {
                    assertTrue(fr.scopeIsThis());
                    if ("0.0.0".equals(d.statementId())) {
                        assertCurrentValue(d, 11, "instance type HashMap<T,Node<T>>");
                        String linked = d.iteration() < 11 ? "node:-1,set:-1,t:-1" : "";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, 11, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if ("node".equals(d.variableName())) {
                    if ("0.0.0".equals(d.statementId())) {
                        assertCurrentValue(d, 11, "nodeMap.get(t)");
                        String linked = d.iteration() < 11 ? "set:-1,t:-1,this.nodeMap:-1" : "this.nodeMap:3";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, 4, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                        if (d.iteration() >= 4) {
                            assertEquals(DV.TRUE_DV, d.variableInfoContainer().propertyOverrides().get(Property.CONTEXT_MODIFIED));
                        }
                    }
                }
                if (d.variable() instanceof FieldReference fr && "dependsOn".equals(fr.fieldInfo.name)) {
                    if ("0.0.1".equals(d.statementId())) {
                        assertNotNull(fr.scopeVariable);
                        assertEquals("node", fr.scopeVariable.toString());
                        assertEquals("node", fr.scope.toString());

                        assertTrue(d.variableInfoContainer().isInitial());
                        VariableInfo initial = d.variableInfoContainer().getPreviousOrInitial();
                        String expected = switch (d.iteration()) {
                            case 0 -> "<f:node.dependsOn>";
                            case 1, 2, 3, 4, 5, 7, 6, 8 -> "<vp:dependsOn:link@Field_dependsOn>";
                            default -> "nullable instance type List<T>";
                        };
                        assertEquals(expected, initial.getValue().toString());

                        assertTrue(d.variableInfoContainer().hasEvaluation());
                        VariableInfo eval = d.variableInfoContainer().best(Stage.EVALUATION);
                        String expectedE = switch (d.iteration()) {
                            case 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 -> "<f:node.dependsOn>";
                            default -> "nullable instance type List<T>";
                        };
                        assertEquals(expectedE, eval.getValue().toString());

                        assertTrue(d.variableInfoContainer().hasMerge());
                        String expectedM = switch (d.iteration()) {
                            case 0 -> "<f:node.dependsOn>";
                            case 1, 2, 3, 4, 5, 6, 7, 8 -> "<vp:dependsOn:link@Field_dependsOn>";
                            case 9, 10 -> "null==nullable instance type List<T>?nullable instance type List<T>:<f:node.dependsOn>";
                            default -> "nullable instance type List<T>";
                        };
                        assertEquals(expectedM, d.currentValue().toString());
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
            if ("t".equals(d.fieldInfo().name)) {
                if (d.iteration() > 0) {
                    assertNotNull(d.haveError(Message.Label.PRIVATE_FIELD_NOT_READ_IN_PRIMARY_TYPE));
                }
            }
        };

        testClass("DGSimplified_3", 1, 2, new DebugConfiguration.Builder()
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
                        assertDv(d, 21, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                    }
                }
                if ("result".equals(d.variableName())) {
                    if ("2".equals(d.statementId())) {
                        assertCurrentValue(d, 5, "new ArrayList<>(nodeMap.size())/*0==this.size()*/");
                    }
                    if ("3.0.0".equals(d.statementId()) || "3.0.1".equals(d.statementId())) {
                        assertCurrentValue(d, 21, "instance type List<T>");
                    }
                    if ("3.0.2.0.7".equals(d.statementId())) {
                        // priority 1... can only fail because of "key"
                        assertCurrentValue(d, 21, "instance type List<T>");
                    }
                    if ("3.0.2.1.1".equals(d.statementId())) {
                        assertCurrentValue(d, 21, "instance type List<T>");
                    }
                }
                if ("cycle".equals(d.variableName())) {
                    assertTrue(d.statementId().compareTo("3.0.2.0.0") >= 0);
                    if ("3.0.2.0.0".equals(d.statementId())) {
                        assertCurrentValue(d, 21, "new HashMap<>(toDo)/*this.size()==toDo$3.size()*/");
                    }
                    if ("3.0.2.0.1".equals(d.statementId())) {
                        // call to removeAsManyAsPossible  priority 0
                        // the modification should travel from the linked keySet to the object
                        assertCurrentValue(d, 21, "instance type HashMap<T,Node<T>>");
                    }
                }
                if ("sortedCycle".equals(d.variableName())) {
                    assertTrue(d.statementId().compareTo("3.0.2.0.3") >= 0);
                    assertCurrentValue(d, 21, "cycle.entrySet().stream().sorted(/*inline compare*/`e1.getValue().dependsOn`.size()==`e2.getValue().dependsOn`.size()?null==backupComparator?0:backupComparator.compare(e1.getKey(),e2.getKey()):`e1.getValue().dependsOn`.size()-`e2.getValue().dependsOn`.size()).map(Entry::getKey).toList()");
                }
                if ("key".equals(d.variableName())) {
                    assertTrue(d.statementId().compareTo("3.0.2.0.4") >= 0);
                    assertCurrentValue(d, 21, "cycle.entrySet().stream().sorted(/*inline compare*/`e1.getValue().dependsOn`.size()==`e2.getValue().dependsOn`.size()?null==backupComparator?0:backupComparator.compare(e1.getKey(),e2.getKey()):`e1.getValue().dependsOn`.size()-`e2.getValue().dependsOn`.size()).map(Entry::getKey).toList().get(0)");
                }
            }
            if ("dependencies".equals(d.variableName())) {
                if ("3.0.1.0.2.1.0".equals(d.statementId())) {
                    String expected = d.iteration() < 21 ? "<f:entry.getValue().dependsOn>"
                            : "(entry.getValue()).dependsOn$0";
                    assertEquals(expected, d.currentValue().toString());
                }
            }
            if ("compare".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "backupComparator".equals(pi.name)) {
                    assertEquals("$1", d.methodInfo().typeInfo.simpleName);
                    if ("1.0.0".equals(d.statementId())) {
                        String expected = d.iteration() < 4 ? "<p:backupComparator>"
                                : "nullable instance type Comparator<T>/*@Identity*/";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 4, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                    }
                }
            }
            if ("reverse".equals(d.methodInfo().name)) {
                if ("dg".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("new DGSimplified_4<>()", d.currentValue().toString());
                    }
                    if ("1.0.2.0.0.0.0.0.0".equals(d.statementId())) {
                        assertDv(d, 5, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                        assertCurrentValue(d, 6, "instance type DGSimplified_4<T>");
                    }
                    if ("1.0.2".equals(d.statementId())) {
                        assertCurrentValue(d, 6, "instance type DGSimplified_4<T>");
                    }
                    if ("1".equals(d.statementId()) || "2".equals(d.statementId())) {
                        assertCurrentValue(d, 6,
                                "set.isEmpty()?new DGSimplified_4<>():instance type DGSimplified_4<T>");
                        assertDv(d, 5, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if (d.variable() instanceof ParameterInfo pi && "set".equals(pi.name)) {
                    if ("1.0.2.0.0.0.0".equals(d.statementId()) || "1.0.2.0.0".equals(d.statementId())
                            || "1".equals(d.statementId()) || "2".equals(d.statementId())) {
                        assertDv(d, 8, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("2".equals(d.statementId())) {
                        // priority 3: problem: independent, one of the methods
                        assertCurrentValue(d, 28,
                                "set.isEmpty()?new DGSimplified_4<>():instance type DGSimplified_4<T>");
                    }
                }
            }
            if ("removeAsManyAsPossible".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "set".equals(pi.name)) {
                    if ("1.0.0".equals(d.statementId())) {
                        assertDv(d, 6, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                        assertDv(d, 6, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                    }
                    if ("1.0.1".equals(d.statementId())) {
                        assertDv(d, 6, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                        assertDv(d, 18, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                    }
                    if ("1.0.2".equals(d.statementId())) {
                        assertDv(d, 6, DV.TRUE_DV, Property.CONTEXT_MODIFIED);

                        // priority 5
                        assertDv(d, 18, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                    }
                    if ("1".equals(d.statementId())) {
                        assertDv(d, 6, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                        assertDv(d, 18, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                    }
                }
            }
            if ("addNode".equals(d.methodInfo().name)) {
                if ("d".equals(d.variableName())) {
                    assertTrue(d.variableInfoContainer().variableNature() instanceof VariableNature.LoopVariable);
                    if ("1".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "bidirectional:-1,dependsOn:-1" : "dependsOn:3";
                        assertEquals(expected, d.variableInfo().getLinkedVariables().toString());
                    }
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("sorted".equals(d.methodInfo().name)) {
                if ("3.0.1.0.2.1.0".equals(d.statementId())) {
                    String expected = d.iteration() < 21 ? "!<null-check>&&!<m:isEmpty>"
                            : "!(entry.getValue()).dependsOn$0.isEmpty()&&null!=(entry.getValue()).dependsOn$0";
                    assertEquals(expected, d.condition().toString());
                }
                if ("3.0.1.0.3.0.2.0.0".equals(d.statementId())) {
                    String expected =d.iteration() == 0 ? "<null-check>" :
                            d.iteration() < 21 ? "<c:boolean>" : "null!=reportIndependent";
                    assertEquals(expected, d.condition().toString());
                }
            }
            if ("reverse".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    assertFalse(d.conditionManagerForNextStatement().isDelayed());
                }
                if ("2".equals(d.statementId())) {
                    assertEquals("true", d.state().toString());
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("addNode".equals(d.methodInfo().name)) {
                assertDv(d, 5, DV.TRUE_DV, Property.MODIFIED_METHOD);
            }
            if ("reverse".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 9, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d.p(0), 9, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, Property.NOT_NULL_PARAMETER);
                assertDv(d, 28, MultiLevel.EFFECTIVELY_FINAL_FIELDS_DV, Property.IMMUTABLE);
                assertDv(d, 8, MultiLevel.INDEPENDENT_HC_DV, Property.INDEPENDENT);
                // priority 4
                assertEquals(d.iteration() >= 28, d.methodAnalysis().getSingleReturnValue().isDone());
                if (d.iteration() >= 28) {
                    assertEquals("/*inline reverse*/set.isEmpty()?new DGSimplified_4<>():instance type DGSimplified_4<T>", d.methodAnalysis().getSingleReturnValue().toString());
                }
            }
            if ("singleRemoveStep".equals(d.methodInfo().name)) {
                assertDv(d, 6, DV.FALSE_DV, Property.MODIFIED_METHOD);
                assertDv(d.p(0), 6, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_PARAMETER);
                assertDv(d.p(0), 6, DV.TRUE_DV, Property.MODIFIED_VARIABLE);
                assertEquals(d.iteration() >= 6, d.methodAnalysis().getSingleReturnValue().isDone());
                if (d.iteration() >= 6) {
                    assertEquals("/*inline singleRemoveStep*/instance type boolean", d.methodAnalysis().getSingleReturnValue().toString());
                }
                assertDv(d, 0, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
            }
            if ("sorted".equals(d.methodInfo().name)) {
                // priority 2
                assertDv(d, 21, MultiLevel.INDEPENDENT_HC_DV, Property.INDEPENDENT);
            }
            if ("comparator".equals(d.methodInfo().name)) {
                assertDv(d, 4, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
            }
            if ("removeAsManyAsPossible".equals(d.methodInfo().name)) {
                assertDv(d, 28, DV.FALSE_DV, Property.MODIFIED_METHOD);
                assertDv(d.p(0), 7, DV.TRUE_DV, Property.MODIFIED_VARIABLE);
            }
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("DGSimplified_4".equals(d.typeInfo().simpleName)) {
                assertDv(d, 27, MultiLevel.EFFECTIVELY_FINAL_FIELDS_DV, Property.IMMUTABLE);
                // priority 3
                // IMPROVE we should find a better breaking point (but the value appears to be correct)
                assertDv(d, 27, MultiLevel.INDEPENDENT_HC_INCONCLUSIVE, Property.INDEPENDENT);
            }
        };
        testClass("DGSimplified_4", 1, 3, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build(), new AnalyserConfiguration.Builder().setComputeFieldAnalyserAcrossAllMethods(true).build());
    }
}
