
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
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.inspector.MethodResolution;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.util.DependencyGraph;
import org.e2immu.analyser.visitor.*;
import org.e2immu.support.Freezable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class Test_Util_06_DependencyGraph extends CommonTestRunner {

    public Test_Util_06_DependencyGraph() {
        super(false); // important for this to be false, we'll be parsing Freezable
    }

    MethodAnalyserVisitor mavForFreezable = d -> {
        if ("ensureNotFrozen".equals(d.methodInfo().name)) {
            assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
            assertEquals("<no return value>", d.methodAnalysis().getSingleReturnValue().toString());
            String pc = d.iteration() == 0
                    ? "Precondition[expression=<precondition>, causes=[]]"
                    : "Precondition[expression=!frozen, causes=[escape]]";
            assertEquals(pc, d.methodAnalysis().getPreconditionForEventual().toString());
            String expected = switch (d.iteration()) {
                case 0 -> "[DelayedEventual:initial@Class_Freezable]";
                case 1 -> "[DelayedEventual:final@Field_frozen]";
                default -> "@Only before: [frozen]";
            };
            assertEquals(expected, d.methodAnalysis().getEventual().toString());
        }
        if ("ensureFrozen".equals(d.methodInfo().name)) {
            assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
            assertEquals("<no return value>", d.methodAnalysis().getSingleReturnValue().toString());
            String pc = d.iteration() == 0
                    ? "Precondition[expression=<precondition>, causes=[]]"
                    : "Precondition[expression=frozen, causes=[escape]]";
            assertEquals(pc, d.methodAnalysis().getPreconditionForEventual().toString());
            String expected = switch (d.iteration()) {
                case 0 -> "[DelayedEventual:initial@Class_Freezable]";
                case 1 -> "[DelayedEventual:final@Field_frozen]";
                default -> "@Only after: [frozen]";
            };
            assertEquals(expected, d.methodAnalysis().getEventual().toString());
        }
    };

    @Test
    public void test_0() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            int n = d.methodInfo().methodInspection.get().getParameters().size();
            if ("addNode".equals(d.methodInfo().name) && 3 == n) {
                if (d.variable() instanceof ParameterInfo pi && "t".equals(pi.name)) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if (d.variable() instanceof This) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }
            if ("addNode".equals(d.methodInfo().name) && 2 == n) {
                if (d.variable() instanceof ParameterInfo pi && "t".equals(pi.name)) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, 4, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if (d.variable() instanceof This) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, 4, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }
            if ("sorted".equals(d.methodInfo().name) && 3 == n) {
                if (d.variable() instanceof FieldReference fr) {
                    if ("nodeMap".equals(fr.fieldInfo.name)) {
                        assertEquals("this", fr.scope.toString());
                        if ("4".equals(d.statementId())) {
                            String expected = d.iteration() == 0 ? "<f:nodeMap>" : "instance type HashMap<T,Node<T>>";
                            assertEquals(expected, d.currentValue().toString());
                            assertEquals("", d.variableInfo().getAssignmentIds().toString());
                        }
                    } else if ("dependsOn".equals(fr.fieldInfo.name)) {
                        assertNotNull(fr.scopeVariable);
                        if ("entry.getValue()".equals(fr.scope.toString())) {
                            assertNotEquals("4", d.statementId());
                        } else if ("scope-scope-230:40:3.0.1".equals(fr.scope.toString())) {
                            if ("4".equals(d.statementId())) {
                                String expected = d.iteration() == 0 ? "<f:dependsOn>" : "nullable instance type List<T>";
                                assertEquals(expected, d.currentValue().toString());
                                assertEquals("", d.variableInfo().getAssignmentIds().toString());
                            }
                        } else fail("Scope " + fr.scope);
                    } else fail("Field " + fr);
                }
                if ("result".equals(d.variableName())) {
                    if ("4".equals(d.statementId())) {
                        String expected = d.iteration() <= 6 ? "<vl:result>"
                                : "toDo$3.isEmpty()?new ArrayList<>(nodeMap.size())/*0==this.size()*/:instance type List<T>";
                        assertEquals(expected, d.currentValue().toString());
                        String lvs = switch (d.iteration()) {
                            case 0, 1, 2, 3, 4 -> "done:-1,reportIndependent:-1,reportPartOfCycle:-1,result:-1,return sorted:0,scope-230:40:-1,this.nodeMap:-1,this:-1,toDo:-1";
                            case 5, 6 -> "done:3,reportIndependent:3,reportPartOfCycle:3,result:0,return sorted:0,scope-230:40:3,this.nodeMap:3,this:3,toDo:3";
                            default -> "done:3,reportIndependent:3,result:0,return sorted:0,scope-230:40:3,this.nodeMap:3,this:3,toDo:3";
                        };
                        assertEquals(lvs, d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, 5, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if ("cycle".equals(d.variableName())) {
                    if ("3.0.2.0.0".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<new:HashMap<T,Node<T>>>"
                                : "new HashMap<>(toDo)/*this.size()==nodeMap.size()*/";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
                if (d.variable() instanceof This) {
                    if ("3.0.1".equals(d.statementId())) {
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("3.0.2.0.03".equals(d.statementId())) {
                        assertDv(d, 4, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("3.0.2.0.04".equals(d.statementId())) {
                        assertDv(d, 4, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("3.0.2.1.1".equals(d.statementId())) {
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }
            if ("sorted".equals(d.methodInfo().name) && 0 == n) {
                if (d.variable() instanceof ReturnVariable) {
                    assertEquals("0", d.statementId());
                    String lvs = switch (d.iteration()) {
                        case 0, 1, 2, 3, 4, 5, 6 -> "return sorted:-1,this:-1";
                        default -> "return sorted:0";
                    };
                    assertEquals(lvs, d.variableInfo().getLinkedVariables().toString());
                    String expected = d.iteration() <= 6 ? "<m:sorted>"
                            : "`toDo`.isEmpty()?new ArrayList<>(`nodeMap`.size())/*0==this.size()*/:instance type List<T>";
                    assertEquals(expected, d.currentValue().toString());
                }
            }
            if ("reverse".equals(d.methodInfo().name)) {
                if ("dg".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("new DependencyGraph<>()", d.currentValue().toString());
                        assertDv(d, MultiLevel.MUTABLE_DV, Property.IMMUTABLE); // myself
                        assertDv(d, MultiLevel.DEPENDENT_DV, Property.INDEPENDENT); // myself
                    }
                    if ("1.0.0".equals(d.statementId())) {
                        String expected = d.iteration() <= 6 ? "<mmc:dg>" : "instance type DependencyGraph<T>";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 1, MultiLevel.MUTABLE_DV, Property.IMMUTABLE); // myself
                        assertDv(d, 1, MultiLevel.DEPENDENT_DV, Property.INDEPENDENT); // myself
                    }
                    if ("2".equals(d.statementId())) {
                        String expected = d.iteration() <= 6 ? "<vl:dg>"
                                : "set.isEmpty()?new DependencyGraph<>():instance type DependencyGraph<T>";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 7, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
                        assertDv(d, 7, MultiLevel.DEPENDENT_DV, Property.INDEPENDENT);
                    }
                }
            }
            if ("dependenciesWithoutStartingPoint".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "t".equals(pi.name)) {
                    if ("1".equals(d.statementId())) {
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                    }
                }
            }
            if ("removeAsManyAsPossible".equals(d.methodInfo().name)) {
                if ("reverse".equals(d.variableName())) {
                    if ("2.0.3".equals(d.statementId())) {
                        String expected = d.iteration() <= 3 ? "<m:reverse>" : "nullable instance type DependencyGraph<T>";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
                if (d.variable() instanceof This thisVar && "DependencyGraph".equals(thisVar.typeInfo.simpleName)) {
                    if ("2.0.1".equals(d.statementId()) || "2.0.2".equals(d.statementId())) {
                        assertDv(d, 3, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("2.0.3".equals(d.statementId())) {
                        assertDv(d, 4, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }
            if ("accept".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "result".equals(pi.name)
                        && pi.index == 1 && "$3".equals(d.methodInfo().typeInfo.simpleName)) {
                    if ("0".equals(d.statementId())) {
                        // these values should travel upwards (StatementAnalyserImpl.transferFromClosureToResult)
                        assertDv(d, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                        assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                    }
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("removeAsManyAsPossible".equals(d.methodInfo().name)) {
                if ("1.0.1".equals(d.statementId())) {
                    // call to reverse(), which gets a precondition in iteration 6
                    String expected = d.iteration() <= 5 ? "Precondition[expression=<precondition>, causes=[]]"
                            : "Precondition[expression=!`dg`.frozen, causes=[methodCall:reverse]]";
                    assertEquals(expected,
                            d.statementAnalysis().stateData().getPrecondition().toString());
                }
            }
            if ("reverse".equals(d.methodInfo().name)) {
                if ("1.0.0".equals(d.statementId())) {
                    String expected = d.iteration() == 0 ? "Precondition[expression=<precondition>, causes=[]]"
                            : "Precondition[expression=!dg$1.frozen, causes=[methodCall:addNode]]";
                    assertEquals(expected, d.statementAnalysis().stateData().getPrecondition().toString());
                }
                if ("2".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0 -> "Precondition[expression=<precondition>&&<precondition>, causes=[]]";
                        case 1, 2, 3, 4, 5, 6 -> "Precondition[expression=!dg$1.frozen&&<precondition>, causes=[methodCall:addNode]]";
                        default -> "Precondition[expression=!dg$1.frozen&&!dg$1.0.2.0.0.frozen, causes=[methodCall:addNode, methodCall:addNode]]";
                    };
                    assertEquals(expected, d.statementAnalysis().methodLevelData().combinedPreconditionGet().toString());
                }
            }
            if ("recursivelyComputeDependenciesWithoutStartingPoint".equals(d.methodInfo().name)) {
                if ("2.0.0".equals(d.statementId())) {
                    assertEquals("dependsOn={modified in context=false:0, not null in context=nullable:1}, node={modified in context=false:0, not null in context=nullable:1}, nodeMap={modified in context=false:0, not null in context=nullable:1}, result={modified in context=true:1, not null in context=not_null:5, read=true:1}, t={modified in context=false:0, not null in context=nullable:1}, this={modified in context=false:0, not null in context=not_null:5, read=true:1}",
                            d.statementAnalysis().propertiesFromSubAnalysersSortedToString());
                }
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("nodeMap".equals(d.fieldInfo().name)) {
                assertEquals("instance type HashMap<T,Node<T>>", d.fieldAnalysis().getValue().toString());
            }
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("DependencyGraph".equals(d.typeInfo().simpleName)) {
                assertDv(d, 6, MultiLevel.EVENTUALLY_E2IMMUTABLE_DV, Property.IMMUTABLE);
                assertDv(d, 7, MultiLevel.INDEPENDENT_1_DV, Property.INDEPENDENT);
            }
            if ("$4".equals(d.typeInfo().simpleName)) {
                assertDv(d, 7, MultiLevel.EVENTUALLY_E2IMMUTABLE_DV, Property.IMMUTABLE);
                assertDv(d, 8, MultiLevel.INDEPENDENT_1_DV, Property.INDEPENDENT);
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            int n = d.methodInfo().methodInspection.get().getParameters().size();
            String methodName = d.methodInfo().name;
            if ("sorted".equals(methodName) && 3 == n) {
                assertDv(d, 7, MultiLevel.INDEPENDENT_1_DV, Property.INDEPENDENT);
                assertDv(d, 5, DV.FALSE_DV, Property.MODIFIED_METHOD);
                String srv = d.iteration() <= 6 ? "<m:sorted>"
                        : "/*inline sorted*/toDo$3.isEmpty()?new ArrayList<>(nodeMap.size())/*0==this.size()*/:instance type List<T>";
                assertEquals(srv, d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("sorted".equals(methodName) && 0 == n) {
                assertDv(d, 5, DV.FALSE_DV, Property.MODIFIED_METHOD);
                assertDv(d, 7, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT); // FIXME wrong!
                String srv = d.iteration() <= 6 ? "<m:sorted>"
                        : "/*inline sorted*/`toDo`.isEmpty()?new ArrayList<>(`nodeMap`.size())/*0==this.size()*/:instance type List<T>";
                assertEquals(srv, d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("reverse".equals(methodName)) {
                assertDv(d, 4, DV.FALSE_DV, Property.FLUENT);
                assertDv(d, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
                assertDv(d, 2, DV.FALSE_DV, Property.MODIFIED_METHOD);
                String pre = switch (d.iteration()) {
                    case 0 -> "Precondition[expression=<precondition>&&<precondition>, causes=[]]";
                    case 1, 2, 3, 4, 5, 6 -> "Precondition[expression=!dg$1.frozen&&<precondition>, causes=[methodCall:addNode]]";
                    default -> "Precondition[expression=!dg$1.frozen&&!dg$1.0.2.0.0.frozen, causes=[methodCall:addNode, methodCall:addNode]]";
                };
                assertEquals(pre, d.methodAnalysis().getPrecondition().toString());
            }
            if ("singleRemoveStep".equals(methodName)) {
                assertDv(d, 2, DV.FALSE_DV, Property.MODIFIED_METHOD);
                assertEquals("Precondition[expression=true, causes=[]]",
                        d.methodAnalysis().getPrecondition().toString());
            }
            if ("copyRemove".equals(methodName)) {
                assertDv(d, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
                assertDv(d, 2, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("addNode".equals(methodName) && 3 == n) {
                assertDv(d, 3, DV.TRUE_DV, Property.MODIFIED_METHOD);
                String addNodePCE = d.iteration() <= 1 ? "Precondition[expression=<precondition>, causes=[]]"
                        : "Precondition[expression=!frozen, causes=[methodCall:getOrCreate, methodCall:getOrCreate, methodCall:ensureNotFrozen]]";
                assertEquals(addNodePCE, d.methodAnalysis().getPreconditionForEventual().toString());
                assertDv(d.p(0), 4, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_PARAMETER);
            }
            if ("addNode".equals(methodName) && 2 == n) {
                String addNodePCE = d.iteration() <= 1 ? "Precondition[expression=<precondition>, causes=[]]"
                        : "Precondition[expression=!frozen, causes=[methodCall:addNode]]";
                assertEquals(addNodePCE, d.methodAnalysis().getPreconditionForEventual().toString());
                assertDv(d, 4, DV.TRUE_DV, Property.MODIFIED_METHOD);
                assertDv(d.p(0), 5, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_PARAMETER);
            }
            if ("getOrCreate".equals(methodName)) {
                assertDv(d, 2, DV.TRUE_DV, Property.MODIFIED_METHOD);
                String pre = d.iteration() <= 1 ? "Precondition[expression=<precondition>, causes=[]]"
                        : "Precondition[expression=!frozen, causes=[methodCall:ensureNotFrozen]]";
                assertEquals(pre, d.methodAnalysis().getPreconditionForEventual().toString());
                assertDv(d, 2, MultiLevel.INDEPENDENT_1_DV, Property.INDEPENDENT);
            }
            if ("removeAsManyAsPossible".equals(methodName)) {
                String pre = switch (d.iteration()) {
                    case 0, 1, 2, 3, 4, 5, 6 -> "Precondition[expression=<precondition>, causes=[]]";
                    default -> "Precondition[expression=!`dg`.frozen, causes=[methodCall:reverse]]";
                };
                assertEquals(pre, d.methodAnalysis().getPrecondition().toString());
                assertDv(d, 4, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("recursivelyComputeDependenciesWithoutStartingPoint".equals(methodName)) {
                assertDv(d.p(0), 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_PARAMETER);
                MethodResolution methodResolution = d.methodInfo().methodResolution.get();
                assertFalse(methodResolution.ignoreMeBecauseOfPartOfCallCycle());
                assertEquals("accept, recursivelyComputeDependenciesWithoutStartingPoint", methodResolution.callCycleSorted());

                // CM on result should travel from $xx.accept(...)
                // FIXME wrong!
//                assertDv(d.p(1), 10, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_PARAMETER);
            }
            if ("dependenciesWithoutStartingPoint".equals(methodName)) {
                MethodResolution methodResolution = d.methodInfo().methodResolution.get();
                assertFalse(methodResolution.ignoreMeBecauseOfPartOfCallCycle());
                assertEquals("", methodResolution.callCycleSorted());
                assertDv(d.p(0), 2, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_PARAMETER);
            }
            if ("recursivelyAddToSubGraph".equals(methodName)) {
                assertDv(d, 2, DV.FALSE_DV, Property.MODIFIED_METHOD);
                MethodResolution methodResolution = d.methodInfo().methodResolution.get();
                assertFalse(methodResolution.ignoreMeBecauseOfPartOfCallCycle());
                assertEquals("", methodResolution.callCycleSorted());
                assertEquals("recursivelyAddToSubGraph", methodResolution.methodsOfOwnClassReachedSorted());
            }
            if ("startArbitrarily".equals(methodName)) {
                assertDv(d, 3, DV.FALSE_DV, Property.MODIFIED_METHOD);
                MethodResolution methodResolution = d.methodInfo().methodResolution.get();
                assertFalse(methodResolution.ignoreMeBecauseOfPartOfCallCycle());
                assertEquals("", methodResolution.callCycleSorted());
                assertEquals("recursivelyAddToSubGraph", methodResolution.methodsOfOwnClassReachedSorted());
            }
            if ("comparator".equals(methodName)) {
                assertTrue(d.methodInfo().methodInspection.get().isStatic());
                assertDv(d, 2, DV.FALSE_DV, Property.MODIFIED_METHOD);
                MethodResolution methodResolution = d.methodInfo().methodResolution.get();
                assertFalse(methodResolution.ignoreMeBecauseOfPartOfCallCycle());
                assertEquals("", methodResolution.callCycleSorted());
                assertEquals("compare", methodResolution.methodsOfOwnClassReachedSorted());
            }
        };
        testSupportAndUtilClasses(List.of(DependencyGraph.class, Freezable.class), 2, 10,
                new DebugConfiguration.Builder()
                        .addAfterMethodAnalyserVisitor(mavForFreezable)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                        .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().setComputeFieldAnalyserAcrossAllMethods(false).build());
    }

    @Test
    public void test_1() throws IOException {
        testSupportAndUtilClasses(List.of(DependencyGraph.class, Freezable.class), 3, 10,
                new DebugConfiguration.Builder()
                        .addAfterMethodAnalyserVisitor(mavForFreezable)
                        .build(),
                new AnalyserConfiguration.Builder().setComputeFieldAnalyserAcrossAllMethods(true).build());
    }
}