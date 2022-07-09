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
                    String value = d.iteration() <= 33 ? "<m:put>" : "nullable instance type Node<T>";
                    assertEquals(value, d.evaluationResult().value().toString());
                    assertEquals(d.iteration() >= 36, d.evaluationResult().causesOfDelay().isDone());
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
            if ("accept".equals(d.methodInfo().name) && "$4".equals(d.methodInfo().typeInfo.simpleName)) {
                if ("0.0.1".equals(d.statementId())) {
                    assertEquals(d.iteration() >= 36, d.statementAnalysis().methodLevelData().linksHaveBeenEstablished());
                }
                if ("0".equals(d.statementId())) {
                    assertEquals(d.iteration() >= 36, d.statementAnalysis().methodLevelData().linksHaveBeenEstablished());
                }
            }
            if ("removeAsManyAsPossible".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    String state = d.iteration() <= 1 ? "!<m:get>" : "!changed.get()";
                    assertEquals(state, d.state().toString());
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
                        assertDv(d, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if (d.variable() instanceof ParameterInfo pi && "accept".equals(pi.name)) {
                    if ("1".equals(d.statementId())) {
                        assertDv(d, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("3".equals(d.statementId())) {
                        assertDv(d, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }
            if ("accept".equals(d.methodInfo().name) && "$4".equals(d.methodInfo().typeInfo.simpleName)) {
                if (d.variable() instanceof FieldReference fr && "nodeMap".equals(fr.fieldInfo.name)) {
                    if ("copy".equals(fr.scope.toString())) {
                        if ("0.0.1".equals(d.statementId())) {
                            assertDv(d, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                            String lvs = d.iteration() <= 35 ? "accept:-1,copy:-1,newDependsOn:-1,node.dependsOn:-1,node:-1,t:-1"
                                    : "accept:3,copy:2,newDependsOn:3,node.dependsOn:3,node:3,t:3";
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
                        String lvs = d.iteration() <= 35 ? "accept:-1,copy.nodeMap:-1,newDependsOn:-1,node.dependsOn:-1,node:-1,t:-1"
                                : "accept:3,copy.nodeMap:2,newDependsOn:3,node.dependsOn:3,node:3,t:3";
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
                    }
                }
            }
            if ("removeAsManyAsPossible".equals(d.methodInfo().name)) {
                if ("changed".equals(d.variableName())) {
                    if ("1".equals(d.statementId())) {
                        assertTrue(d.variableInfoContainer().variableNature() instanceof VariableNature.VariableDefinedOutsideLoop);
                        assertCurrentValue(d, 35, "changed$1.get()?instance type AtomicBoolean:new AtomicBoolean(true)");
                        assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("1.0.1".equals(d.statementId())) {
                        String expected = d.iteration() <= 34 ? "<vl:changed>" : "instance type AtomicBoolean";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
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
                assertDv(d.p(0), 36, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                assertDv(d, 34, DV.TRUE_DV, Property.MODIFIED_METHOD);
            }
            if ("comparator".equals(d.methodInfo().name)) {
                String expected = d.iteration() <= 33 ? "<m:comparator>"
                        : "/*inline comparator*//*inline compare*/(e1.getValue()).dependsOn$0.size()==(e2.getValue()).dependsOn$0.size()?null==backupComparator?0:backupComparator.compare(e1.getKey(),e2.getKey()):(e1.getValue()).dependsOn$0.size()-(e2.getValue()).dependsOn$0.size()";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                assertDv(d, 34, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("copyRemove".equals(d.methodInfo().name)) {
                assertDv(d, 36, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("DGSimplified_0".equals(d.typeInfo().simpleName)) {
                assertDv(d, 35, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER); // TODO verify this
            }
        };
        // TODO improve on errors
        testClass("DGSimplified_0", 5, 1, new DebugConfiguration.Builder()
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
                        assertDv(d, 38, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if (d.variable() instanceof FieldReference fr && "dependsOn".equals(fr.fieldInfo.name)) {
                    if ("node".equals(fr.scope.toString())) {
                        if ("3".equals(d.statementId())) {
                            String expected = d.iteration() == 0
                                    ? "<f:dependsOn>" : d.iteration() <= 37
                                    ? "<null-check>&&null!=nullable instance type List<T>?<f:dependsOn>:nullable instance type List<T>"
                                    : "nullable instance type List<T>";
                            assertEquals(expected, d.currentValue().toString());
                        }
                        if ("3.0.0".equals(d.statementId())) { // forEach() call
                            String expected = d.iteration() <= 37 ? "<f:dependsOn>" : "nullable instance type List<T>";
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
                        String linked = d.iteration() == 0 ? "node.dependsOn:-1,node:-1,t:-1,this.nodeMap:-1" : "t:3";
                        assertEquals(linked, eval.getLinkedVariables().toString());
                    }
                }
            }
            if ("accept".equals(d.methodInfo().name) && "$1".equals(d.methodInfo().typeInfo.simpleName)) {
                if (d.variable() instanceof ParameterInfo pi && "result".equals(pi.name)) {
                    if ("0".equals(d.statementId())) {
                        assertTrue(d.variableInfoContainer().hasEvaluation());
                        VariableInfo eval = d.variableInfoContainer().best(Stage.EVALUATION);
                        String linked = d.iteration() == 0 ? "NOT_YET_SET" : "t:3";
                        assertEquals(linked, eval.getLinkedVariables().toString());
                        assertTrue(d.variableInfoContainer().hasMerge());
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("0.0.0".equals(d.statementId())) {
                        String linked = d.iteration() == 0 ? "NOT_YET_SET" : "t:3";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("recursivelyComputeDependencies".equals(d.methodInfo().name)) {
                assertDv(d, 38, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("accept".equals(d.methodInfo().name)) {
                if ("$1".equals(d.methodInfo().typeInfo.simpleName)) { // recursivelyComputeDependencies
                    assertDv(d, 39, DV.FALSE_DV, Property.MODIFIED_METHOD);
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
                    String expected = d.iteration() <= 17 ? "<m:addNode>" : "<no return value>";
                    assertEquals(expected, d.evaluationResult().value().toString());
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("reverse".equals(d.methodInfo().name)) {
                if ("1.0.0".equals(d.statementId())) {
                    String expected = d.iteration() <= 17 ? "<m:addNode>" : "<no return value>";
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
                        case 1, 2, 3, 4, 5, 6, 7, 8, 9 -> "null!=<f:node.dependsOn>";
                        default -> "null!=(nodeMap.get(t)).dependsOn";
                    };
                    assertEquals(expected, d.evaluationResult().value().toString());
                    String delays = switch (d.iteration()) {
                        case 0 -> "initial:this.nodeMap@Method_reverse_0.0.0-C";
                        case 1, 2, 3, 4, 5, 6, 7, 8, 9 -> "initial:node@Method_reverse_0.0.1.0.0-C;initial:this.nodeMap@Method_reverse_0.0.0-C";
                        default -> "";
                    };
                    assertEquals(delays, d.evaluationResult().causesOfDelay().toString());
                }
                if ("0.0.1.0.0.0.0".equals(d.statementId())) {
                    String expected = d.iteration() <= 9 ? "<m:contains>" : "set.contains(d)";
                    assertEquals(expected, d.evaluationResult().value().toString());
                    String delays = switch (d.iteration()) {
                        case 0 -> "initial:node@Method_reverse_0.0.1.0.0-C;initial:set@Method_reverse_0.0.0-E;initial:this.nodeMap@Method_reverse_0.0.0-C";
                        case 1, 2, 3, 4, 5, 6, 7, 8, 9 -> "initial:node@Method_reverse_0.0.1.0.0-C;initial:this.nodeMap@Method_reverse_0.0.0-C";
                        default -> "";
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
                        String expectedE = d.iteration() <= 9 ? "<f:dependsOn>" : "nullable instance type List<T>";
                        assertEquals(expectedE, eval.getValue().toString());

                        assertTrue(d.variableInfoContainer().hasMerge());
                        String expectedM = switch (d.iteration()) {
                            case 0 -> "<f:dependsOn>";
                            case 1, 2, 3, 4, 5, 6, 7, 8, 9 -> "null==nullable instance type List<T>?nullable instance type List<T>:<f:dependsOn>";
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
        };
        testClass("DGSimplified_3", 0, 2, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build(), new AnalyserConfiguration.Builder().setComputeFieldAnalyserAcrossAllMethods(true).build());
    }

    /*

    4: main 1 $2.test
    6: Type $2
    9: main 0 $1.compare
    12: Type $1 immutable
    14: main 1 getOrCreate
    16: param analyser addNode t independent
    19: main 0 addNode
    22: param analyser reverse set independent
    25: param analyser removeAsManyAsPossible set independent
    28: sorted: parameter, main 0...4 whole method
    30: main 0 sorted1
    32: field nodeMap, analyseModified
    34: field dependsOn, analyseModified
    36: Type Node, immutable
    43: method reverse, @Container
    45: main 1.0.1 subBlocks
    47: Type DGS4, approved preconditions E2, break INDEP inconclusive
    51: method reverse, @Immutable
    53: main 1.0.1 removeAsManyAsPossible
    55: main 0 sorted1 @Imm, @Cont
    57: fail.

     */
    @Test
    public void test_4() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("sorted".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "reportIndependent".equals(pi.name)) {
                    if ("3.0.1.0.3.0.2.0.0".equals(d.statementId())) {
                        assertDv(d, 36, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                    }
                }
                if ("result".equals(d.variableName())) {
                    if ("2".equals(d.statementId())) {
                        assertCurrentValue(d, 1, "new ArrayList<>(nodeMap.size())/*0==this.size()*/");
                    }
                    if ("3.0.0".equals(d.statementId()) || "3.0.1".equals(d.statementId())) {
                        assertCurrentValue(d, 29, "instance type List<T>");
                    }
                    if ("3.0.2.0.7".equals(d.statementId())) {
                        // priority 1... can only fail because of "key"
                        assertCurrentValue(d, 38, "instance type List<T>");
                    }
                    if ("3.0.2.1.1".equals(d.statementId())) {
                        assertCurrentValue(d, 36, "instance type List<T>");
                    }
                }
                if ("cycle".equals(d.variableName())) {
                    assertTrue(d.statementId().compareTo("3.0.2.0.0") >= 0);
                    if ("3.0.2.0.0".equals(d.statementId())) {
                        assertCurrentValue(d, 36, "new HashMap<>(toDo)/*this.size()==toDo$3.size()*/");
                    }
                    if ("3.0.2.0.1".equals(d.statementId())) {
                        // call to removeAsManyAsPossible  priority 0
                        // the modification should travel from the linked keySet to the object
                        assertCurrentValue(d, 38, "instance type HashMap<T,Node<T>>");
                    }
                }
                if ("sortedCycle".equals(d.variableName())) {
                    assertTrue(d.statementId().compareTo("3.0.2.0.3") >= 0);
                    assertCurrentValue(d, 38, "cycle.entrySet().stream().sorted(/*inline compare*/`e1.getValue().dependsOn`.size()==`e2.getValue().dependsOn`.size()?null==backupComparator?0:backupComparator.compare(e1.getKey(),e2.getKey()):`e1.getValue().dependsOn`.size()-`e2.getValue().dependsOn`.size()).map(Entry::getKey).toList()");
                }
                if ("key".equals(d.variableName())) {
                    assertTrue(d.statementId().compareTo("3.0.2.0.4") >= 0);
                    assertCurrentValue(d, 38, "cycle.entrySet().stream().sorted(/*inline compare*/`e1.getValue().dependsOn`.size()==`e2.getValue().dependsOn`.size()?null==backupComparator?0:backupComparator.compare(e1.getKey(),e2.getKey()):`e1.getValue().dependsOn`.size()-`e2.getValue().dependsOn`.size()).map(Entry::getKey).toList().get(0)");
                }
            }
            if ("dependencies".equals(d.variableName())) {
                if ("3.0.1.0.2.1.0".equals(d.statementId())) {
                    String expected = d.iteration() < 36 ? "<f:entry.getValue().dependsOn>"
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
            if ("reverse".equals(d.methodInfo().name)) {
                if ("dg".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("new DGSimplified_4<>()", d.currentValue().toString());
                    }
                    if ("1.0.2.0.0.0.0.0.0".equals(d.statementId())) {
                        assertDv(d, 24, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                        assertCurrentValue(d, 36, "instance type DGSimplified_4<T>");
                    }
                    if ("1.0.2".equals(d.statementId())) {
                        assertCurrentValue(d, 36, "instance type DGSimplified_4<T>");
                    }
                    if ("1".equals(d.statementId()) || "2".equals(d.statementId())) {
                        assertCurrentValue(d, 36,
                                "set.isEmpty()?new DGSimplified_4<>():instance type DGSimplified_4<T>");
                        assertDv(d, 24, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if (d.variable() instanceof ParameterInfo pi && "set".equals(pi.name)) {
                    if ("1.0.2.0.0.0.0".equals(d.statementId()) || "1.0.2.0.0".equals(d.statementId())
                            || "1".equals(d.statementId()) || "2".equals(d.statementId())) {
                        assertDv(d, 39, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("2".equals(d.statementId())) {
                        // priority 3: problem: independent, one of the methods
                        assertCurrentValue(d, 50,
                                "set.isEmpty()?new DGSimplified_4<>():instance type DGSimplified_4<T>");
                    }
                }
            }
            if ("removeAsManyAsPossible".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "set".equals(pi.name)) {
                    if ("1.0.0".equals(d.statementId())) {
                        assertDv(d, 37, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                        assertDv(d, 37, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                    }
                    if ("1.0.1".equals(d.statementId())) {
                        assertDv(d, 37, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                        assertDv(d, 44, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                    }
                    if ("1.0.2".equals(d.statementId())) {
                        assertDv(d, 37, DV.TRUE_DV, Property.CONTEXT_MODIFIED);

                        // priority 5
                        assertDv(d, 50, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                    }
                    if ("1".equals(d.statementId())) {
                        assertDv(d, 37, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                        assertDv(d, 50, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                    }
                }
            }
            if ("addNode".equals(d.methodInfo().name)) {
                if ("d".equals(d.variableName())) {
                    assertTrue(d.variableInfoContainer().variableNature() instanceof VariableNature.LoopVariable);
                    if ("1".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "dependsOn:-1" : "dependsOn:3";
                        assertEquals(expected, d.variableInfo().getLinkedVariables().toString());
                    }
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("sorted".equals(d.methodInfo().name)) {
                if ("3.0.1.0.3.0.2.0.0".equals(d.statementId())) {
                    String expected = d.iteration() == 0 ? "<null-check>" :
                            d.iteration() < 36 ? "<c:boolean>" : "null!=reportIndependent";
                    assertEquals(expected, d.condition().toString());
                }
                if ("3.0.1.0.2.1.0".equals(d.statementId())) {
                    String expected = d.iteration() <= 28 ? "!<null-check>&&!<m:isEmpty>"
                            : d.iteration() < 36 ? "!(entry.getValue()).dependsOn$0.isEmpty()&&!<null-check>"
                            : "!(entry.getValue()).dependsOn$0.isEmpty()&&null!=(entry.getValue()).dependsOn$0";
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
                assertDv(d, 36, DV.TRUE_DV, Property.MODIFIED_METHOD);
            }
            if ("reverse".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 40, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d.p(0), 40, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, Property.NOT_NULL_PARAMETER);
                assertDv(d, 50, MultiLevel.EFFECTIVELY_E1IMMUTABLE_DV, Property.IMMUTABLE);
                assertDv(d, 50, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
                // priority 4
                assertEquals(d.iteration() >= 50, d.methodAnalysis().getSingleReturnValue().isDone());
                if (d.iteration() >= 50) {
                    assertEquals("/*inline reverse*/set.isEmpty()?new DGSimplified_4<>():instance type DGSimplified_4<T>", d.methodAnalysis().getSingleReturnValue().toString());
                }
            }
            if ("singleRemoveStep".equals(d.methodInfo().name)) {
                assertDv(d, 36, DV.FALSE_DV, Property.MODIFIED_METHOD);
                assertDv(d.p(0), 37, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_PARAMETER);
                assertDv(d.p(0), 37, DV.TRUE_DV, Property.MODIFIED_VARIABLE);
                assertEquals(d.iteration() >= 37, d.methodAnalysis().getSingleReturnValue().isDone());
                if (d.iteration() >= 37) {
                    assertEquals("/*inline singleRemoveStep*/instance type boolean", d.methodAnalysis().getSingleReturnValue().toString());
                }
                assertDv(d, 0, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
            }
            if ("sorted".equals(d.methodInfo().name)) {
                // priority 2
                assertDv(d, 47, MultiLevel.INDEPENDENT_1_DV, Property.INDEPENDENT);
            }
            if ("comparator".equals(d.methodInfo().name)) {
                assertDv(d, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
            }
            if ("removeAsManyAsPossible".equals(d.methodInfo().name)) {
                assertDv(d, 50, DV.FALSE_DV, Property.MODIFIED_METHOD);
                assertDv(d.p(0), 38, DV.TRUE_DV, Property.MODIFIED_VARIABLE);
            }
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("DGSimplified_4".equals(d.typeInfo().simpleName)) {
                assertDv(d, 49, MultiLevel.EFFECTIVELY_E1IMMUTABLE_DV, Property.IMMUTABLE);
                // priority 3
                // IMPROVE we should find a better breaking point (but the value appears to be correct)
                assertDv(d, 49, MultiLevel.INDEPENDENT_1_INCONCLUSIVE, Property.INDEPENDENT);
            }
        };
        testClass("DGSimplified_4", 0, 3, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build(), new AnalyserConfiguration.Builder().setComputeFieldAnalyserAcrossAllMethods(true).build());
    }
}
