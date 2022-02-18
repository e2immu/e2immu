
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
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.e2immu.analyser.visitor.TypeAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
                assertDv(d, 2, MultiLevel.EFFECTIVELY_E1IMMUTABLE_DV, Property.IMMUTABLE);
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
            if ("combiner".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    String expected = switch (d.iteration()) {
                        case 0 -> "<vp:BiFunction<UpgradableBooleanMap,UpgradableBooleanMap<T>,UpgradableBooleanMap<T>>:cnn@Parameter_other;ext_not_null@Parameter_other>";
                        case 1 -> "<vp:BiFunction<UpgradableBooleanMap,UpgradableBooleanMap<T>,UpgradableBooleanMap<T>>:cnn@Parameter_other>";
                        default -> "UpgradableBooleanMap::putAll";
                    };
                    assertEquals(expected, d.currentValue().toString());
                    assertEquals("return combiner:0", d.variableInfo().getLinkedVariables().toString());
                }
            }
        };


        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("put".equals(d.methodInfo().name)) {
                assertDv(d, DV.TRUE_DV, Property.MODIFIED_METHOD);
            }
            if ("stream".equals(d.methodInfo().name)) {
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("putAll".equals(d.methodInfo().name)) {
                assertEquals("put,stream", d.methodInfo().methodResolution.get().methodsOfOwnClassReached()
                        .stream().map(m -> m.name).sorted().collect(Collectors.joining(",")));
                // assertDv(d, 1, DV.TRUE_DV, Property.MODIFIED_METHOD);
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
                    String expected = d.iteration() == 0
                            ? "org.e2immu.analyser.util.UpgradableBooleanMap.putAll(org.e2immu.analyser.util.UpgradableBooleanMap<T>):0:other=false:0,this=assign_to_field@Parameter_t;cm:this@Method_accept_0;link:e@Method_accept_0"
                            : "org.e2immu.analyser.util.UpgradableBooleanMap.putAll(org.e2immu.analyser.util.UpgradableBooleanMap<T>):0:other=false:0,this=true:1";
                    assertEquals(expected, d.statementAnalysis().variablesModifiedBySubAnalysers().map(Objects::toString)
                            .sorted().collect(Collectors.joining(",")));
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
