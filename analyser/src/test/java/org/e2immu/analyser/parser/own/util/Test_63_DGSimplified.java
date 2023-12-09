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

import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.it;
import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.it0;
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
                    String value = d.iteration() < 33 ? "<m:put>" : "nullable instance type Node<T>";
                    assertEquals(value, d.evaluationResult().value().toString());
                    assertEquals(d.iteration() >= 32, d.evaluationResult().causesOfDelay().isDone());
                }
            }
            if ("copyRemove".equals(d.methodInfo().name)) {
                if ("2".equals(d.statementId())) {
                    String expected = d.iteration() < 32 ? "<m:freeze>" : "<no return value>";
                    assertEquals(expected, d.evaluationResult().value().toString());
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("accept".equals(d.methodInfo().name) && "$4".equals(d.methodInfo().typeInfo.simpleName)) {
                if ("0.0.1".equals(d.statementId())) {
                    assertEquals(d.iteration() >= 32, d.statementAnalysis().methodLevelData().linksHaveBeenEstablished());
                }
                if ("0".equals(d.statementId())) {
                    assertEquals(d.iteration() >= 32, d.statementAnalysis().methodLevelData().linksHaveBeenEstablished());
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
                    String expected = d.iteration() < 28 ? "<m:compare>" : "instance type $3";
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
                        String expected = d.iteration() < 33 ? "<v:copy>" : "instance type DGSimplified_0<T>";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 33, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
                        assertDv(d, 33, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if (d.variable() instanceof ParameterInfo pi && "accept".equals(pi.name)) {
                    if ("1".equals(d.statementId())) {
                        assertDv(d, 33, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("3".equals(d.statementId())) {
                        assertDv(d, 33, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }
            if ("accept".equals(d.methodInfo().name) && "$4".equals(d.methodInfo().typeInfo.simpleName)) {
                if (d.variable() instanceof FieldReference fr && "nodeMap".equals(fr.fieldInfo().name)) {
                    if ("copy".equals(fr.scope().toString())) {
                        if ("0.0.1".equals(d.statementId())) {
                            assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                            assertLinked(d, it(0, 26, "accept:-1,copy:-1,newDependsOn:-1,node.dependsOn:-1,node:-1,t:-1"),
                                    it(27, 32, "accept:-1,copy:-1,newDependsOn:-1,node.dependsOn:-1,node:-1"),
                                    it(33, "accept:4,copy:2,newDependsOn:4,node.dependsOn:4,node:4"));
                        }
                        if ("0".equals(d.statementId())) {
                            assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                        }
                    } else fail("Scope " + fr.scope());
                }
                if ("copy".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        VariableInfo eval = d.variableInfoContainer().best(Stage.EVALUATION);
                        assertEquals("instance type DGSimplified_0<T>", eval.getValue().toString());
                    }
                    if ("0.0.1".equals(d.statementId())) {
                        assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                        assertLinked(d, it(0, 26, "accept:-1,copy.nodeMap:-1,newDependsOn:-1,node.dependsOn:-1,node:-1,t:-1"),
                                it(27, 32, "accept:-1,copy.nodeMap:-1,newDependsOn:-1,node.dependsOn:-1,node:-1"),
                                it(33, "accept:4,copy.nodeMap:4,newDependsOn:4,node.dependsOn:4,node:4"));
                    }
                }
                if (d.variable() instanceof ParameterInfo pi && "accept".equals(pi.name)) {
                    assertEquals("copyRemove", pi.owner.name);
                    if ("0.0.0".equals(d.statementId())) {
                        assertLinked(d, it0("copy:-1,newDependsOn:-1,node.dependsOn:-1,node:-1"),
                                it(1, 26, "newDependsOn:-1,node.dependsOn:-1,node:-1"),
                                it(27, ""));
                        assertDv(d, 27, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("0.0.1".equals(d.statementId())) {
                        assertLinked(d,
                                it(0, 26, "copy.nodeMap:-1,copy:-1,newDependsOn:-1,node.dependsOn:-1,node:-1,t:-1"),
                                it(27, 32, "copy.nodeMap:-1,copy:-1,newDependsOn:-1,node.dependsOn:-1,node:-1"),
                                it(33, "copy.nodeMap:4,copy:4,newDependsOn:4,node.dependsOn:4,node:4"));
                        assertDv(d, 33, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("0".equals(d.statementId())) {
                        assertDv(d, 33, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }
            if ("removeAsManyAsPossible".equals(d.methodInfo().name)) {
                if ("changed".equals(d.variableName())) {
                    if ("1".equals(d.statementId())) {
                        assertTrue(d.variableInfoContainer().variableNature() instanceof VariableNature.VariableDefinedOutsideLoop);
                        assertCurrentValue(d, 28, "changed$1.get()?instance type AtomicBoolean:new AtomicBoolean(true)");
                        assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("1.0.1".equals(d.statementId())) {
                        String expected = d.iteration() < 28 ? "<vl:changed>" : "instance type AtomicBoolean";
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
                assertDv(d, 26, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER_RESTRICTION);
                assertDv(d, MultiLevel.CONTAINER_DV, Property.CONTAINER);
                assertDv(d, MultiLevel.NOT_IGNORE_MODS_DV, Property.EXTERNAL_IGNORE_MODIFICATIONS);

                // FIELD BREAK
                String links = d.iteration() < 12 ? "link@Field_nodeMap" : "";
                assertEquals(links, ((FieldAnalysisImpl.Builder) d.fieldAnalysis()).allLinksHaveBeenEstablished().toString());
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("accept".equals(d.methodInfo().name) && "$4".equals(d.methodInfo().typeInfo.simpleName)) {
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("addNode".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 29, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                assertDv(d, 26, DV.TRUE_DV, Property.MODIFIED_METHOD);
                String pce = d.iteration() == 0
                        ? "Precondition[expression=<precondition>, causes=[]]"
                        : "Precondition[expression=!this.isFrozen(), causes=[companionMethod:ensureNotFrozen$Precondition, companionMethod:ensureNotFrozen$Precondition]]";
                assertEquals(pce, d.methodAnalysis().getPreconditionForEventual().toString());
            }
            if ("comparator".equals(d.methodInfo().name)) {
                String expected = d.iteration() < 28 ? "<m:comparator>" : "instance type $3";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                assertDv(d, 26, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("copyRemove".equals(d.methodInfo().name)) {
                assertDv(d, 33, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("DGSimplified_0".equals(d.typeInfo().simpleName)) {
                // removeAsManyAsPossible is public and has a modified parameter
                assertDv(d, 28, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER);
            }
        };

        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("----S-S--S-SF--SFM-SFMT----SF---", d.delaySequence());

        testClass("DGSimplified_0", 0, 1, new DebugConfiguration.Builder()
                //  .addEvaluationResultVisitor(evaluationResultVisitor)
                //    .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                //  .addStatementAnalyserVisitor(statementAnalyserVisitor)
                //  .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                //  .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                //   .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .addBreakDelayVisitor(breakDelayVisitor)
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
                if (d.variable() instanceof FieldReference fr && "nodeMap".equals(fr.fieldInfo().name)) {
                    if ("3".equals(d.statementId())) {
                        assertDv(d, 26, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if (d.variable() instanceof FieldReference fr && "dependsOn".equals(fr.fieldInfo().name)) {
                    if ("node".equals(fr.scope().toString())) {
                        if ("3".equals(d.statementId())) {
                            assertCurrentValue(d, 26, "nullable instance type List<T>");
                        }
                        if ("3.0.0".equals(d.statementId())) { // forEach() call
                            String expected = d.iteration() < 26 ? "<f:node.dependsOn>" : "nullable instance type List<T>";
                            assertEquals(expected, d.currentValue().toString());
                        }
                    } else if ("nodeMap.get(t)".equals(fr.scope().toString())) {
                        if ("3".equals(d.statementId())) {
                            String expected = switch (d.iteration()) {
                                case 0, 1 -> "<f:nodeMap.get(t).dependsOn>";
                                default -> "nullable instance type List<T>";
                            };
                            assertEquals(expected, d.currentValue().toString());
                        }
                    } else fail("Scope " + fr.scope());
                }
                if (d.variable() instanceof ParameterInfo pi && "result".equals(pi.name)) {
                    if ("3.0.0".equals(d.statementId())) {
                        assertTrue(d.variableInfoContainer().hasEvaluation());
                        VariableInfo eval = d.variableInfoContainer().best(Stage.EVALUATION);
                        String linked = d.iteration() == 0 ? "node:-1,t:-1,this.nodeMap:-1,this:-1" : "";
                        assertEquals(linked, eval.getLinkedVariables().toString());
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
                assertDv(d, 26, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("accept".equals(d.methodInfo().name)) {
                if ("$1".equals(d.methodInfo().typeInfo.simpleName)) { // recursivelyComputeDependencies
                    assertDv(d, 27, DV.FALSE_DV, Property.MODIFIED_METHOD);
                } else if ("$3".equals(d.methodInfo().typeInfo.simpleName)) {// visit
                    assertDv(d, 1, DV.FALSE_DV, Property.MODIFIED_METHOD);
                }
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("dependsOn".equals(d.fieldInfo().name)) {
                String expected = "dependsOn,null";
                assertEquals(expected, ((FieldAnalysisImpl.Builder) d.fieldAnalysis()).sortedValuesString());
            }
        };
        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("----S-S--S-S--SF--SFM-SFMT------", d.delaySequence());
        testClass("DGSimplified_1", 4, 1, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addBreakDelayVisitor(breakDelayVisitor)
                .build(), new AnalyserConfiguration.Builder().setComputeFieldAnalyserAcrossAllMethods(true).build());
    }

    @Test
    public void test_2() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("reverse".equals(d.methodInfo().name)) {
                if ("1.0.0".equals(d.statementId())) {
                    String expected = d.iteration() < 23 ? "<m:addNode>" : "<no return value>";
                    assertEquals(expected, d.evaluationResult().value().toString());
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("reverse".equals(d.methodInfo().name)) {
                if ("1.0.0".equals(d.statementId())) {
                    String expected = d.iteration() < 23 ? "<m:addNode>" : "<no return value>";
                    assertEquals(expected, d.statementAnalysis().stateData().valueOfExpressionGet().toString());
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
        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("----S--S--S-SF--SFMT------", d.delaySequence());

        testClass("DGSimplified_2", 2, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addBreakDelayVisitor(breakDelayVisitor)
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
                        case 0 -> "!<null-check>";
                        case 1, 2, 3, 4, 5, 6, 7, 8 -> "null!=<f:node.dependsOn>";
                        default -> "null!=(nodeMap.get(t$0)).dependsOn";
                    };
                    assertEquals(expected, d.evaluationResult().value().toString());
                    String delays = switch (d.iteration()) {
                        case 0 -> "initial:this.nodeMap@Method_reverse_0.0.0-C";
                        case 1, 2, 3, 4, 5, 6, 7 ->
                                "initial:node@Method_reverse_0.0.1.0.0-C;initial:this.nodeMap@Method_reverse_0.0.0-C";
                        case 8 -> "assign_to_field@Parameter_dependsOn";
                        default -> "";
                    };
                    assertEquals(delays, d.evaluationResult().causesOfDelay().toString());
                }
                if ("0.0.1.0.0.0.0".equals(d.statementId())) {
                    String expected = d.iteration() < 9 ? "<m:contains>" : "set.contains(d$0.0.1.0.0)";
                    assertEquals(expected, d.evaluationResult().value().toString());
                    String delays = d.iteration() < 9
                            ? "initial:node@Method_reverse_0.0.1.0.0-C;initial:set@Method_reverse_0.0.0-E;initial:this.nodeMap@Method_reverse_0.0.0-C"
                            : "";
                    assertEquals(delays, d.evaluationResult().causesOfDelay().toString());
                }
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("reverse".equals(d.methodInfo().name)) {

                if (d.variable() instanceof FieldReference fr && "nodeMap".equals(fr.fieldInfo().name)) {
                    assertTrue(fr.scopeIsThis());
                    if ("0.0.0".equals(d.statementId())) {
                        assertCurrentValue(d, 9, "instance type HashMap<T,Node<T>>");
                        String linked = d.iteration() == 0 ? "node:-1,set:-1,t:-1,this:-1"
                                : d.iteration() < 9 ? "node:-1,this:-1" : "";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, 9, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if ("node".equals(d.variableName())) {
                    if ("0.0.0".equals(d.statementId())) {
                        assertCurrentValue(d, 9, "nodeMap.get(t$0)");
                        String linked = d.iteration() == 0 ? "set:-1,t:-1,this.nodeMap:-1,this:-1"
                                : d.iteration() < 9 ? "this.nodeMap:-1,this:-1"
                                : "this.nodeMap:3,this:4";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, 4, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                        if (d.iteration() >= 4) {
                            assertEquals(DV.TRUE_DV, d.variableInfoContainer().propertyOverrides().get(Property.CONTEXT_MODIFIED));
                        }
                    }
                }
                if (d.variable() instanceof FieldReference fr && "dependsOn".equals(fr.fieldInfo().name)) {
                    if ("0.0.1".equals(d.statementId())) {
                        assertNotNull(fr.scopeVariable());
                        assertEquals("node", fr.scopeVariable().toString());
                        assertEquals("node", fr.scope().toString());

                        assertTrue(d.variableInfoContainer().isInitial());
                        VariableInfo initial = d.variableInfoContainer().getPreviousOrInitial();
                        String expected = switch (d.iteration()) {
                            case 0 -> "<f:node.dependsOn>";
                            default -> "nullable instance type List<T>";
                        };
                        assertEquals(expected, initial.getValue().toString());

                        assertTrue(d.variableInfoContainer().hasEvaluation());
                        VariableInfo eval = d.variableInfoContainer().best(Stage.EVALUATION);
                        String expectedE = d.iteration() < 9 ? "<f:node.dependsOn>" : "nullable instance type List<T>";
                        assertEquals(expectedE, eval.getValue().toString());

                        assertTrue(d.variableInfoContainer().hasMerge());
                        String expectedM = switch (d.iteration()) {
                            case 0 -> "<f:node.dependsOn>";
                            case 1, 2, 3, 4, 5, 6, 7, 8 ->
                                    "null==nullable instance type List<T>?nullable instance type List<T>:<f:node.dependsOn>";
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

        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("---S--SF----", d.delaySequence());

        testClass("DGSimplified_3", 1, 2, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addBreakDelayVisitor(breakDelayVisitor)
                .build(), new AnalyserConfiguration.Builder().setComputeFieldAnalyserAcrossAllMethods(true).build());
    }

    @Test
    public void test_4() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("reverse".equals(d.methodInfo().name)) {
                if ("1.0.2".equals(d.statementId())) {
                    String value = d.iteration() == 0 ? "!<null-check>"
                            : d.iteration() < 30 ? "null!=<f:node.dependsOn>"
                            : "null!=(nodeMap.get(t$1)).dependsOn$2";
                    assertEquals(value, d.evaluationResult().value().toString());
                }
            }
            if ("sorted".equals(d.methodInfo().name)) {
                if ("3.0.2.0.2".equals(d.statementId())) {
                    String value = d.iteration() < 29 ? "!<m:isEmpty>" : "!cycle$3.0.2.0.1.isEmpty()";
                    assertEquals(value, d.evaluationResult().value().toString());
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("reverse".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "dependsOn".equals(fr.fieldInfo().name)) {
                    if ("1.0.2".equals(d.statementId())) {
                        assertEquals("node", fr.scope().toString());
                        assertCurrentValue(d, 28, "nullable instance type List<T>");
                    }
                }
                if ("node".equals(d.variableName())) {
                    if ("1.0.1".equals(d.statementId())) {
                        assertCurrentValue(d, 28, "nodeMap.get(t)");
                    }
                }
            }
            if ("sorted".equals(d.methodInfo().name)) {
                if ("cycle".equals(d.variableName())) {
                    if ("3.0.2.0.0".equals(d.statementId())) {
                        // removeAsManyAsPossible modifies cycle.keySet(), which should be linked to cycle
                        assertDv(d, 25, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                        assertCurrentValue(d, 25, "new HashMap<>(toDo)");
                        assertLinked(d,
                                it0("backupComparator:-1,done:-1,keys:-1,reportIndependent:-1,reportPartOfCycle:-1,result:-1,scope-96:40:-1,scope-scope-96:40:3.0.1.dependsOn:-1,scope-scope-96:40:3.0.1:-1,this.nodeMap:-1,this:-1,toDo:-1"),
                                it(1, 24, "done:-1,keys:-1,reportIndependent:-1,scope-96:40:-1,scope-scope-96:40:3.0.1.dependsOn:-1,scope-scope-96:40:3.0.1:-1,this.nodeMap:-1,this:-1,toDo:-1"),
                                it(25, "done:4,keys:4,reportIndependent:4,scope-96:40:4,this.nodeMap:4,this:4,toDo:4"));
                    }
                    if ("3.0.2.0.1".equals(d.statementId())) {
                        // removeAsManyAsPossible modifies cycle.keySet(), which should be linked to cycle
                        assertLinked(d,
                                it0("backupComparator:-1,done:-1,keys:-1,reportIndependent:-1,reportPartOfCycle:-1,result:-1,scope-96:40:-1,scope-scope-96:40:3.0.1.dependsOn:-1,scope-scope-96:40:3.0.1:-1,this.nodeMap:-1,this:-1,toDo:-1"),
                                it(1, 24, "done:-1,keys:-1,reportIndependent:-1,scope-96:40:-1,scope-scope-96:40:3.0.1.dependsOn:-1,scope-scope-96:40:3.0.1:-1,this.nodeMap:-1,this:-1,toDo:-1"),
                                it(25, 43, "done:-1,keys:-1,reportIndependent:-1,scope-96:40:-1,this.nodeMap:-1,this:-1,toDo:-1"),
                                it(44, "done:4,keys:4,reportIndependent:4,scope-96:40:4,this.nodeMap:4,this:4,toDo:4"));
                        assertDv(d, 27, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("reverse".equals(d.methodInfo().name)) {
                if ("1.0.2".equals(d.statementId())) {
                    assertEquals(2, d.statementAnalysis().flowData().getTimeAfterEvaluation());
                    String value = d.iteration() == 0 ? "!<null-check>"
                            : d.iteration() < 28 ? "null!=<f:node.dependsOn>"
                            : "null!=(nodeMap.get(t)).dependsOn$2";
                    assertEquals(value, d.statementAnalysis().stateData().valueOfExpressionGet().toString());
                }
            }
            if ("sorted".equals(d.methodInfo().name)) {
                if ("3.0.2.0.2".equals(d.statementId())) {
                    String post = d.iteration() < 27 ? "PostCondition[expression=!<m:isEmpty>, index=3.0.2.0.2]"
                            : d.iteration() < 28 ? "PostCondition[expression=<precondition>, index=3.0.2.0.2]"
                            : "PostCondition[expression=true, index=-]";
                    assertEquals(post, d.statementAnalysis().stateData().getPostCondition().toString());
                    String pre = d.iteration() < 27 ? "Precondition[expression=!<m:isEmpty>, causes=[escape]]" :
                            d.iteration() < 28 ? "Precondition[expression=<precondition>, causes=[]]"
                                    : "Precondition[expression=true, causes=[]]";
                    assertEquals(pre, d.statementAnalysis().stateData().getPrecondition().toString());
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("removeAsManyAsPossible".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 29, DV.TRUE_DV, Property.MODIFIED_VARIABLE);
            }
        };

        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("----S--S--S--S-S-S-SF--SFMT------", d.delaySequence());

        testClass("DGSimplified_4", 1, 3, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                //   .addStatementAnalyserVisitor(statementAnalyserVisitor)
                //    .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addBreakDelayVisitor(breakDelayVisitor)
                .build(), new AnalyserConfiguration.Builder().setComputeFieldAnalyserAcrossAllMethods(true).build());
    }

    /*
    Goal of test:
    - in the loop at 4.0.2.1.0, at iteration 2, 'result' already has a value because of the modification in the loop.
    - it even has this value in the E section of the loop statement, because it may be seen before the modification.
    - later, in iteration 5, a value comes along from the outside; it happens to be a value from a loop as well,
      but that should be immaterial.
    - FIXME this value should not take priority, but somehow it does
     */
    @Test
    public void test_5() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("sortedSequenceOfParallel".equals(d.methodInfo().name)) {
                if ("result".equals(d.variableName())) {
                    if ("4.0.1".equals(d.statementId())) {
                        assertTrue(d.variableInfoContainer().hasMerge());
                        String merge = d.iteration() < 5 ? "<vl:result>" : "instance 4.0.1 type List<SortResult<T>>";
                        if (d.iteration() >= 5) {
                            assertEquals("PositionalIdentifier[line=60, pos=13, endLine=74, endPos=13]",
                                    d.currentValue().getIdentifier().toString());
                        }
                        assertEquals(merge, d.currentValue().toString());
                    }
                    if ("4.0.2.1.0".equals(d.statementId())) {
                        assertTrue(d.variableInfoContainer().hasEvaluation());
                        VariableInfo vi = d.variableInfoContainer().best(Stage.EVALUATION);
                        String eval = d.iteration() < 2 ? "<vl:result>" : "instance 4.0.2.1.0 type List<SortResult<T>>";
                        assertEquals(eval, vi.getValue().toString());
                        if (d.iteration() >= 2) {
                            assertEquals("PositionalIdentifier[line=78, pos=17, endLine=78, endPos=80]",
                                    vi.getValue().getIdentifier().toString());
                        }
                        assertTrue(d.variableInfoContainer().hasMerge());
                        String merge = d.iteration() < 5
                                ? "<vl:result>"
                                : "(toDo$4.0.1.entrySet().isEmpty()?new LinkedList<>()/*0==this.size()*/:instance 4.0.1.0.3.0.0 type List<T>).isEmpty()?instance 4.0.1 type List<SortResult<T>>:instance 4.0.2.1.0.0.0 type List<SortResult<T>>";
                        assertEquals(merge, d.currentValue().toString());
                    }
                }
            }
        };
        testClass("DGSimplified_5", 5, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build(), new AnalyserConfiguration.Builder().build());
    }
}
