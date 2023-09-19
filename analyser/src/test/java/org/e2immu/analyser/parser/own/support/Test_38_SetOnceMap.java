
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

package org.e2immu.analyser.parser.own.support;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analyser.VariableInfo;
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

import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.it;
import static org.junit.jupiter.api.Assertions.*;

public class Test_38_SetOnceMap extends CommonTestRunner {

    public Test_38_SetOnceMap() {
        super(true);
    }

    @Test
    public void test() throws IOException {

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("stream".equals(d.methodInfo().name)) {
                String value = d.iteration() < 2 ? "<m:map>" : "map.entrySet().stream().map(instance type $1)";
                assertEquals(value, d.evaluationResult().value().toString());
                if (d.iteration() >= 2) {
                    //      assertTrue(d.evaluationResult().changeData().values().stream().noneMatch(cd -> cd.linkedVariables().isDelayed()));
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("stream".equals(d.methodInfo().name)) {
                String expected = switch (d.iteration()) {
                    case 0 -> "this={context-modified=immutable@Class_Entry}";
                    case 1 -> "this={context-modified=assign_to_field@Parameter_k}";
                    default -> "this={context-modified=false:0}";
                };
                assertEquals(expected, d.statementAnalysis().propertiesFromSubAnalysersSortedToString());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("accept".equals(d.methodInfo().name) && "$1".equals(d.methodInfo().typeInfo.simpleName)) {
                if (d.variable() instanceof ParameterInfo p && "e".equals(p.name)) {
                    String expect = switch (d.iteration()) {
                        case 0, 1 -> "<mod:V>";
                        case 2 -> "<mod:K>";
                        default -> "nullable instance type Entry<K,V>/*@Identity*/";
                    };
                    assertEquals(expect, d.currentValue().toString());
                }
            }
            if ("stream".equals(d.methodInfo().name)) {
                assertEquals("0", d.statementId());
                if (d.variable() instanceof FieldReference fr && "map".equals(fr.fieldInfo.name)) {
                    assertTrue(d.variableInfoContainer().isInitial());
                    VariableInfo vi1 = d.variableInfoContainer().getPreviousOrInitial();
                    String value1 = d.iteration() == 0 ? "<f:map>" : "instance type HashMap<K,V>";
                    assertEquals(value1, vi1.getValue().toString());
                    assertEquals("", vi1.getLinkedVariables().toString());

                    assertTrue(d.variableInfoContainer().hasEvaluation());
                    String value = d.iteration() < 2 ? "<f:map>" : "instance type HashMap<K,V>";
                    assertEquals(value, d.currentValue().toString());
                    assertFalse(d.variableInfoContainer().hasMerge());
                }
                if (d.variable() instanceof ReturnVariable) {
                    assertCurrentValue(d, 2, "map.entrySet().stream().map(instance type $1)");
                    String linked = d.iteration() < 2 ? "this.map:-1,this:-1" : "this.map:4,this:4";
                    assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("stream".equals(d.methodInfo().name)) {
                assertEquals("apply", d.methodInfo().methodResolution.get().methodsOfOwnClassReached()
                        .stream().map(m -> m.name).sorted().collect(Collectors.joining(",")));

                // contracted, not computed!
                assertDv(d, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                assertDv(d, 2, DV.FALSE_DV, Property.MODIFIED_METHOD);
                assertDv(d, 2, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
                assertDv(d, 2, MultiLevel.INDEPENDENT_HC_DV, Property.INDEPENDENT);
            }
            if ("apply".equals(d.methodInfo().name)) {
                assertEquals("$1", d.methodInfo().typeInfo.simpleName);
                assertDv(d, 2, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
                assertDv(d, 2, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
            }
            if ("Entry".equals(d.methodInfo().name)) {
                for (int i = 0; i < 2; i++) {
                    assertDv(d.p(i), 1, MultiLevel.INDEPENDENT_HC_DV, Property.INDEPENDENT);
                    assertDv(d.p(i), 1, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
                    assertDv(d.p(i), 1, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                }
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("SetOnceMap_0".equals(d.typeInfo().simpleName)) {
                assertHc(d, 0, "K, V");
                assertDv(d, MultiLevel.CONTAINER_DV, Property.CONTAINER);
                assertDv(d, 2, MultiLevel.EVENTUALLY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
            }
            if ("Entry".equals(d.typeInfo().simpleName)) {
                assertDv(d, 1, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("map".equals(d.fieldInfo().name)) {
                assertEquals(DV.TRUE_DV, d.fieldAnalysis().getProperty(Property.FINAL));
                assertEquals("instance type HashMap<K,V>", d.fieldAnalysis().getValue().toString());
                assertLinked(d, d.fieldAnalysis().getLinkedVariables(), it(0, ""));
            }
        };

        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("------", d.delaySequence());

        // 1 potential null pointer warning accepted
        testClass("SetOnceMap_0", 0, 0,
                new DebugConfiguration.Builder()
                  //      .addEvaluationResultVisitor(evaluationResultVisitor)
                  //      .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                  //      .addStatementAnalyserVisitor(statementAnalyserVisitor)
                  //      .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                   //     .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                   //     .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                        .addBreakDelayVisitor(breakDelayVisitor)
                        .build());
    }

}
