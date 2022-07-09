
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
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.expression.InlinedMethod;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.e2immu.analyser.visitor.TypeAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class Test_Util_02_UpgradableBooleanMap extends CommonTestRunner {

    public Test_Util_02_UpgradableBooleanMap() {
        super(true);
    }

    @Test
    public void test() throws IOException {
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("UpgradableBooleanMap".equals(d.typeInfo().simpleName)) {
                assertEquals("Type java.util.Map.Entry<T,java.lang.Boolean>, Type param T, Type param T, Type param T, Type param T, Type param T, Type param T",
                        d.typeAnalysis().getTransparentTypes().toString());
                assertDv(d, 1, MultiLevel.EFFECTIVELY_E1IMMUTABLE_DV, Property.IMMUTABLE);
                assertDv(d, 7, MultiLevel.INDEPENDENT_1_DV, Property.INDEPENDENT);
                assertDv(d, 9, MultiLevel.CONTAINER_DV, Property.CONTAINER);
            }

            if ("$1".equals(d.typeInfo().simpleName)) {
                assertEquals("org.e2immu.analyser.util.UpgradableBooleanMap.$1", d.typeInfo().fullyQualifiedName);
                assertDv(d, 3, MultiLevel.EFFECTIVELY_E1IMMUTABLE_DV, Property.IMMUTABLE);
            }

            if ("$2".equals(d.typeInfo().simpleName)) {
                assertEquals("org.e2immu.analyser.util.UpgradableBooleanMap.$1.$2", d.typeInfo().fullyQualifiedName);
                assertDv(d, 4, MultiLevel.EFFECTIVELY_E1IMMUTABLE_DV, Property.IMMUTABLE);
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            int n = d.methodInfo().methodInspection.get().getParameters().size();
            if ("of".equals(d.methodInfo().name) && n == 2) {
                if ("upgradableBooleanMap".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        String expected = d.iteration() <= 7 ? "<new:UpgradableBooleanMap<T>>" : "new UpgradableBooleanMap<>()";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        String expected = d.iteration() <= 7 ? "<new:UpgradableBooleanMap<T>>" : "instance type UpgradableBooleanMap<T>";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
                if (d.variable() instanceof ParameterInfo p && "t".equals(p.name)) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("1".equals(d.statementId())) {
                        assertDv(d, 8, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("2".equals(d.statementId())) {
                        assertDv(d, 8, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }
            if ("combiner".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    String expected = switch (d.iteration()) {
                        case 0 -> "<vp:BiFunction<UpgradableBooleanMap,UpgradableBooleanMap<T>,UpgradableBooleanMap<T>>:ext_not_null@Parameter_other>";
                        case 1, 2 -> "<vp:BiFunction<UpgradableBooleanMap,UpgradableBooleanMap<T>,UpgradableBooleanMap<T>>:cnn@Parameter_other>";
                        default -> "UpgradableBooleanMap::putAll";
                    };
                    assertEquals(expected, d.currentValue().toString());
                    assertEquals("", d.variableInfo().getLinkedVariables().toString());
                }
            }
            if ("accept".equals(d.methodInfo().name) && "$4".equals(d.methodInfo().typeInfo.simpleName)) {
                assertEquals("0", d.statementId());
                if (d.variable() instanceof This) {
                    assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                }
            }
        };


        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            int n = d.methodInfo().methodInspection.get().getParameters().size();
            if ("of".equals(d.methodInfo().name) && n == 2) {
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
                assertDv(d.p(0), 9, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
            }
            if ("of".equals(d.methodInfo().name) && n == 1) {
                String expected = d.iteration() <= 7 ? "<m:of>"
                        : "/*inline of*/null==maps||maps.length<=0?new UpgradableBooleanMap<>():instance type UpgradableBooleanMap<T>";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                if (d.iteration() >= 8) {
                    if (d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod inlinedMethod) {
                        assertEquals("maps", inlinedMethod.variablesOfExpressionSorted());
                    } else fail();
                }
            }
            if ("put".equals(d.methodInfo().name)) {
                assertDv(d, 1, DV.TRUE_DV, Property.MODIFIED_METHOD);
            }
            if ("stream".equals(d.methodInfo().name)) {
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("putAll".equals(d.methodInfo().name)) {
                assertEquals("accept,put,stream", d.methodInfo().methodResolution.get().methodsOfOwnClassReached()
                        .stream().map(m -> m.name).sorted().collect(Collectors.joining(",")));
                // ModifiedMethod must be TRUE, CM travels from accept in $4
                assertDv(d, 1, DV.TRUE_DV, Property.MODIFIED_METHOD);
            }

            // uses putAll as a method reference
            if ("combiner".equals(d.methodInfo().name)) {
                assertEquals(DV.FALSE_DV, d.methodAnalysis().getProperty(Property.MODIFIED_METHOD));
            }

            // accumulator
            if ("accept".equals(d.methodInfo().name) && "$2".equals(d.methodInfo().typeInfo.simpleName)) {
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }

            // finisher
            if ("apply".equals(d.methodInfo().name) && "$3".equals(d.methodInfo().typeInfo.simpleName)) {
                assertEquals(DV.FALSE_DV, d.methodAnalysis().getProperty(Property.MODIFIED_METHOD));
            }

            // putAll
            if ("accept".equals(d.methodInfo().name) && "$4".equals(d.methodInfo().typeInfo.simpleName)) {
                assertDv(d, 1, DV.TRUE_DV, Property.MODIFIED_METHOD);
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("putAll".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0 -> "other={modified in context=link@NOT_YET_SET, not null in context=nullable:1}, this={modified in context=constructor-to-instance@Method_accept_0-E;initial:b@Method_put_0-E;initial:t@Method_put_0-E;initial:this.map@Method_put_0-C;link@NOT_YET_SET, read=true:1}";
                        case 1 -> "other={modified in context=link:t@Method_put_0:M, not null in context=nullable:1}, this={modified in context=true:1, read=true:1}";
                        default -> "other={modified in context=false:0, not null in context=nullable:1}, this={modified in context=true:1, read=true:1}";
                    };
                    assertEquals(expected, d.statementAnalysis().propertiesFromSubAnalysersSortedToString());
                }
            }
        };

        testSupportAndUtilClasses(List.of(UpgradableBooleanMap.class),
                0, 0, new DebugConfiguration.Builder()
                        .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .build());
    }

}
