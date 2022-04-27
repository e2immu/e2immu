
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
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.util.DependencyGraph;
import org.e2immu.analyser.visitor.FieldAnalyserVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.TypeAnalyserVisitor;
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
        int BIG = 20;
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
                        } else if ("scope-scope-215:40:3.0.1".equals(fr.scope.toString())) {
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
                        String expected = d.iteration() <= 5 ? "<vl:result>"
                                : "toDo$3.isEmpty()?new ArrayList<>(nodeMap.size())/*0==this.size()*/:instance type List<T>";
                        assertEquals(expected, d.currentValue().toString());
                        String lvs = d.iteration() <= 1
                                ? "done:-1,reportIndependent:-1,reportPartOfCycle:-1,result:0,return sorted:0,scope-215:40:-1,this.nodeMap:-1,this:-1,toDo:-1"
                                : "done:3,reportIndependent:3,reportPartOfCycle:3,result:0,return sorted:0,scope-215:40:3,this.nodeMap:3,toDo:3";
                        assertEquals(lvs, d.variableInfo().getLinkedVariables().toString());
                    }
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
                assertDv(d, 4, MultiLevel.EVENTUALLY_E2IMMUTABLE_DV, Property.IMMUTABLE);
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            int n = d.methodInfo().methodInspection.get().getParameters().size();
            if ("sorted".equals(d.methodInfo().name) && 3 == n) {
                assertDv(d, 3, MultiLevel.INDEPENDENT_1_DV, Property.INDEPENDENT);
            }
        };
        testSupportAndUtilClasses(List.of(DependencyGraph.class, Freezable.class), 7, 2,
                new DebugConfiguration.Builder()
                        .addAfterMethodAnalyserVisitor(mavForFreezable)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                        .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().setComputeFieldAnalyserAcrossAllMethods(false).build());
    }

    @Test
    public void test_1() throws IOException {
        testSupportAndUtilClasses(List.of(DependencyGraph.class, Freezable.class), 7, 2,
                new DebugConfiguration.Builder()
                        .addAfterMethodAnalyserVisitor(mavForFreezable)
                        .build(),
                new AnalyserConfiguration.Builder().setComputeFieldAnalyserAcrossAllMethods(true).build());
    }
}