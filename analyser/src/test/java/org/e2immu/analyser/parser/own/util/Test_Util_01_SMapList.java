
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
import org.e2immu.analyser.analysis.FlowData;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.util.SMapList;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class Test_Util_01_SMapList extends CommonTestRunner {

    public Test_Util_01_SMapList() {
        super(true);
    }

    @Test
    public void test() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("addAll".equals(d.methodInfo().name)) {
                if ("1.0.0".equals(d.statementId())) {
                    assertEquals("destination.get(e.getKey())", d.evaluationResult().value().toString());
                }
                if ("1.0.1".equals(d.statementId())) {
                    assertEquals("null==destination.get(e.getKey())", d.evaluationResult().value().toString());
                }
                if ("1".equals(d.statementId())) {
                    assertEquals("src.entrySet()", d.evaluationResult().value().toString());
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("list".equals(d.methodInfo().name)) {
                final String RET_VAR = "org.e2immu.analyser.util.SMapList.list(java.util.Map<A,java.util.List<B>>,A)";
                if (d.variable() instanceof ReturnVariable retVar) {
                    assertEquals(RET_VAR, retVar.fqn);
                    if ("2".equals(d.statementId())) {
                        // note the absence of null!=a
                        String expected = "null==map.get(a)?List.of():<return value>";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                    }
                    if ("3".equals(d.statementId())) {
                        String expected = "null==map.get(a)?List.of():map.get(a)";
                        assertEquals(expected, d.currentValue().toString());
                        assertEquals(MultiLevel.NULLABLE_DV, d.getProperty(Property.CONTEXT_NOT_NULL));
                        assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                    }
                }

                if ("list".equals(d.variableName())) {
                    if ("1".equals(d.statementId())) {
                        assertEquals("map.get(a)", d.currentValue().toString());
                    }
                }
            }

            // IMPORTANT: we find ENN, not EffectivelyContentNotNull (we cannot force a List to have non-null content as a result
            // of ma.get)

            if ("add".equals(d.methodInfo().name) && d.variable() instanceof ParameterInfo bs && "bs".equals(bs.simpleName())) {
                if ("1".equals(d.statementId())) {
                    assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                }
                if ("3".equals(d.statementId())) {
                    assertDv(d, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                    assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                }
            }
            if ("add".equals(d.methodInfo().name) && d.variable() instanceof ParameterInfo bs && "a".equals(bs.simpleName())) {
                DV paramMod = d.context().getCurrentMethod()
                        .getParameterAnalyses().get(1).getProperty(Property.CONTEXT_MODIFIED);
                if (d.iteration() <= 1) assertTrue(paramMod.isDelayed());
                else assertEquals(DV.FALSE_DV, paramMod);
            }

            if ("add".equals(d.methodInfo().name) && "list".equals(d.variableName())) {
                String expected;
                if ("3".equals(d.statementId())) {
                    expected = d.iteration() == 0 ? "a:-1,bs:-1,map:-1" : "bs:4,map:4";
                } else {
                    expected = d.iteration() == 0 ? "a:-1,map:-1" : "map:4";
                }
                assertEquals(expected, d.variableInfo().getLinkedVariables().toString());
            }


            if ("addAll".equals(d.methodInfo().name)) {
                if ("e".equals(d.variableName()) && "1.0.0".equals(d.statementId())) {
                    assertEquals("instance type Entry<A,List<B>>", d.currentValue().toString());
                }
                if (d.variable() instanceof ParameterInfo dest && dest.name.equals("destination")) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("nullable instance type Map<A,List<B>>", d.currentValue().toString());
                        assertEquals("", d.variableInfo().getLinkedVariables().toString());
                    }
                }
                if ("inDestination".equals(d.variableName())) {
                    if ("1.0.0".equals(d.statementId())) {
                        assertEquals("destination.get(e.getKey())", d.currentValue().toString());
                    }
                }
                if ("change".equals(d.variableName())) {
                    if ("1.0.1.0.1".equals(d.statementId())) {
                        assertEquals("true", d.currentValue().toString());
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(Property.NOT_NULL_EXPRESSION));
                        assertEquals("", d.variableInfo().getLinkedVariables().toString());
                    }
                    // 2nd branch, merge of an if-statement
                    if ("1.0.1.1.0".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "instance type boolean||<vl:change>" : "instance type boolean||instance type boolean";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                        assertEquals("", d.variableInfo().getLinkedVariables().toString());
                    }
                    // merge of the two above
                    if ("1.0.1".equals(d.statementId())) {
                        String expected = d.iteration() == 0
                                ? "instance type boolean||<vl:change>||null==destination.get(e.getKey())"
                                : "instance type boolean||instance type boolean||null==destination.get(e.getKey())";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                        assertEquals("", d.variableInfo().getLinkedVariables().toString());
                    }
                }
                if ("change$1".equals(d.variableName())) {
                    fail("");
                    if ("1.0.1.0.1".equals(d.statementId())) {
                        assertEquals("true", d.currentValue().toString());
                        assertEquals("change$1:0", d.variableInfo().getLinkedVariables().toString());
                    }
                }
            }
            if ("immutable".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    if ("2".equals(d.statementId())) {
                        String expectValue = d.iteration() < 2 ? "<m:copyOf>"
                                : "Map.copyOf(map.entrySet().isEmpty()?new HashMap<>():instance type Map<A,List<B>>)";
                        assertEquals(expectValue, d.currentValue().toString());
                        assertDv(d, 2, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("list".equals(d.methodInfo().name)) {
                if (Set.of("0", "1", "2", "3").contains(d.statementId())) {
                    assertFalse(d.statementAnalysis().flowData().isUnreachable());
                    assertFalse(d.statementAnalysis().flowData().alwaysEscapesViaException(), "In " + d.statementId());
                }

                if ("0".equals(d.statementId()) || "1".equals(d.statementId())) {
                    assertEquals("true", d.state().toString());
                }
                if ("1".equals(d.statementId())) {
                    // a != null is in the property of parameter, not in precondition
                    assertTrue(d.localConditionManager().precondition().isEmpty());
                }
                if ("2.0.0".equals(d.statementId())) {
                    String expected = "null==map.get(a)";
                    assertEquals(expected, d.condition().toString());
                    assertEquals(expected, d.absoluteState().toString());
                }
                if ("3".equals(d.statementId())) {
                    assertEquals("null!=map.get(a)", d.state().toString());
                }
            }
            if ("addAll".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId()) || "1".equals(d.statementId())) {
                    assertEquals(FlowData.ALWAYS, d.statementAnalysis().flowData().getGuaranteedToBeReachedInMethod());
                }
                if ("1.0.0".equals(d.statementId()) || "1.0.1".equals(d.statementId())) {
                    assertEquals(FlowData.CONDITIONALLY, d.statementAnalysis().flowData().getGuaranteedToBeReachedInMethod());
                }
                if ("1.0.1.1.0".equals(d.statementId())) {
                    assertEquals("null!=destination.get(e.getKey())", d.condition().toString());
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            String name = d.methodInfo().name;

            if ("list".equals(name)) {
                String expected = "null==map.get(a)?List.of():map.get(a)";
                assertEquals(expected, d.getReturnAsVariable().getValue().toString());
                assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
            }
            if ("copy".equals(name)) {
                assertDv(d, 2, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
            }
            if ("add".equals(name) && d.methodInfo().methodInspection.get().getParameters().size() == 3) {
                ParameterInfo parameterInfo = d.methodInfo().methodInspection.get().getParameters().get(2);
                if ("bs".equals(parameterInfo.name)) {
                    assertDv(d.p(1), 2, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                    assertDv(d.p(2), 2, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                }
                if ("b".equals(parameterInfo.name)) {
                    assertDv(d.p(1), 2, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                }
            }
            if ("immutable".equals(d.methodInfo().name)) {
                String expect = d.iteration() < 2 ? "<m:immutable>"
                        : "Map.copyOf(map.entrySet().isEmpty()?new HashMap<>():instance type Map<A,List<B>>)";
                assertEquals(expect, d.methodAnalysis().getSingleReturnValue().toString());

                assertDv(d, 2, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
                assertDv(d, 2, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("SMapList".equals(d.typeInfo().simpleName)) {
                assertDv(d, 2, MultiLevel.EFFECTIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
            }
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo map = typeMap.get(Map.class);
            MethodInfo entrySet = map.findUniqueMethod("entrySet", 0);
            assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV,
                    entrySet.methodAnalysis.get().getProperty(Property.NOT_NULL_EXPRESSION));
            MethodInfo copyOf = map.findUniqueMethod("copyOf", 1);
            assertEquals(MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, copyOf.methodAnalysis.get().getProperty(Property.IMMUTABLE));
        };

        testSupportAndUtilClasses(List.of(SMapList.class), 0, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .build());
    }

}
