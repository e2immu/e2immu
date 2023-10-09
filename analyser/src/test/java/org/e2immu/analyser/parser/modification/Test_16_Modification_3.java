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

package org.e2immu.analyser.parser.modification;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analysis.impl.FieldAnalysisImpl;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.it;
import static org.junit.jupiter.api.Assertions.*;

public class Test_16_Modification_3 extends CommonTestRunner {

    public static final String LINKS_LOCAL3_SET3 = "local3:0,this.set3:0";

    public Test_16_Modification_3() {
        super(true);
    }


    @Test
    public void test3() throws IOException {

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("add3".equals(d.methodInfo().name) && "1".equals(d.statementId())) {
                if (d.iteration() == 0) {
                    assertTrue(d.evaluationResult().causesOfDelay().isDelayed());
                } else {
                    assertEquals("instance type boolean", d.evaluationResult().value().toString());
                    DV v = d.evaluationResult().changeData().entrySet().stream()
                            .filter(e -> e.getKey().fullyQualifiedName().equals("local3"))
                            .map(Map.Entry::getValue)
                            .map(ecd -> ecd.properties().get(Property.CONTEXT_MODIFIED))
                            .findFirst().orElseThrow();
                    assertEquals(DV.TRUE_DV, v);
                }
            }
        };
        final String INSTANCE_TYPE_HASH_SET = "instance type HashSet<String>";
        final String SET3_DELAYED = "<f:set3>";

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("add3".equals(d.methodInfo().name)) {
                if ("local3".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertTrue(d.variableInfo().isAssigned());
                        assertFalse(d.variableInfo().isRead());

                        if (d.iteration() == 0) {
                            assertTrue(d.currentValue().isDelayed());
                        } else {
                            assertTrue(d.variableInfo().getValue() instanceof VariableExpression);
                            VariableExpression variableValue = (VariableExpression) d.currentValue();
                            assertTrue(variableValue.variable() instanceof FieldReference);
                            assertEquals("set3", d.currentValue().toString());
                        }
                        assertLinked(d, it(0, "this.set3:0,this:3"));
                    }
                    if ("1".equals(d.statementId())) {
                        //  the READ is written at level 1
                        assertTrue(d.variableInfo().isAssigned());
                        assertTrue(d.variableInfo().isRead());

                        assertTrue(d.variableInfo().getReadId()
                                .compareTo(d.variableInfo().getAssignmentIds().getLatestAssignment()) > 0);
                        if (d.iteration() == 0) {
                            // there is a variable info at levels 0 and 3
                            assertTrue(d.currentValue().isDelayed());
                            assertFalse(d.variableInfoContainer().isInitial());
                        } else {
                            // there is a variable info in level 1, copied from level 1 in statement 0
                            // problem is that there is one in level 3 already, with a NO_VALUE
                            VariableInfo vi1 = d.variableInfoContainer().current();
                            assertEquals("set3", vi1.getValue().toString());
                            assertEquals(DV.TRUE_DV, d.getProperty(Property.CONTEXT_MODIFIED));
                        }
                        assertLinked(d, it(0, "this.set3:0,this:3"));
                    }
                }
                if (d.variable() instanceof FieldReference fr && "set3".equals(fr.fieldInfo.name)) {
                    assertEquals("org.e2immu.analyser.parser.modification.testexample.Modification_3.set3",
                            d.variableName());
                    if ("0".equals(d.statementId())) {
                        assertLinked(d, it(0, "local3:0,this:3"));
                        String expectValue = d.iteration() == 0 ? SET3_DELAYED : INSTANCE_TYPE_HASH_SET;
                        assertEquals(expectValue, d.variableInfo().getValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        assertTrue(d.variableInfo().isRead());
                        assertLinked(d, it(0, "local3:0,this:3"));
                        String expectValue = d.iteration() == 0 ? SET3_DELAYED
                                : "instance type HashSet<String>/*this.size()>=1&&this.contains(v)*/";
                        assertEquals(expectValue, d.variableInfo().getValue().toString());
                        assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if (d.variable() instanceof ParameterInfo pi && "v".equals(pi.name)) {
                    String delay = "ext_imm@Parameter_v";
                    if ("0".equals(d.statementId())) {
                        assertFalse(d.variableInfoContainer().hasEvaluation());
                        assertDv(d, delay, 1, MultiLevel.EFFECTIVELY_IMMUTABLE_DV, Property.EXTERNAL_IMMUTABLE);
                    }
                    if ("1".equals(d.statementId())) {
                        assertDv(d, delay, 1, MultiLevel.EFFECTIVELY_IMMUTABLE_DV, Property.EXTERNAL_IMMUTABLE);
                        assertTrue(d.variableInfo().getLinkedVariables().isEmpty());
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("add3".equals(d.methodInfo().name) && "1".equals(d.statementId())) {
                assertEquals(d.iteration() > 0, d.statementAnalysis().methodLevelData().linksHaveBeenEstablished());
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if (d.fieldInfo().name.equals("set3")) {
                assertEquals(DV.TRUE_DV, d.fieldAnalysis().getProperty(Property.FINAL));
                assertEquals(1, ((FieldAnalysisImpl.Builder) d.fieldAnalysis()).getValues().size());
                assertEquals(INSTANCE_TYPE_HASH_SET, d.fieldAnalysis().getValue().toString());
                assertDv(d, 1, DV.TRUE_DV, Property.MODIFIED_OUTSIDE_METHOD);
                assertTrue(d.fieldAnalysis().getLinkedVariables().isEmpty());
            }
        };

        TypeMapVisitor typeMapVisitor = d -> {
            TypeInfo set = d.typeMap().get(Set.class);
            MethodInfo addInSet = set.findUniqueMethod("add", 1);
            assertEquals(DV.TRUE_DV, d.getMethodAnalysis(addInSet).getProperty(Property.MODIFIED_METHOD));

            TypeInfo hashSet = d.typeMap().get(HashSet.class);
            MethodInfo addInHashSet = hashSet.findUniqueMethod("add", 1);
            assertEquals(DV.TRUE_DV, d.getMethodAnalysis(addInHashSet).getProperty(Property.MODIFIED_METHOD));
        };

        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("---", d.delaySequence());

        testClass("Modification_3", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .addBreakDelayVisitor(breakDelayVisitor)
                .build());
    }
}
