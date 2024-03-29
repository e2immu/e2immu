
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
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
import org.e2immu.support.Freezable;
import org.e2immu.support.SetOnceMap;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Test_Support_08_SetOnceMap extends CommonTestRunner {

    public Test_Support_08_SetOnceMap() {
        super(false);
    }

    @Test
    public void test() throws IOException {
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("SetOnceMap".equals(d.typeInfo().simpleName)) {
                assertEquals("Type param K, Type param V",
                        d.typeAnalysis().getTransparentTypes().toString());
                assertDv(d, 4, MultiLevel.CONTAINER_DV, Property.CONTAINER);
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("put".equals(d.methodInfo().name)) {
                if ("3.0.0".equals(d.statementId())) {
                    if (d.iteration() == 0) {
                        assertTrue(d.statementAnalysis().stateData().getPrecondition().isDelayed());
                    } else {
                        String expect = d.iteration() == 1 ? "<precondition>" : "!map.containsKey(k)";
                        assertEquals(expect, d.statementAnalysis().stateData().getPrecondition().expression().toString());
                    }
                    assertEquals(d.iteration() >= 2,
                            d.statementAnalysis().methodLevelData().linksHaveBeenEstablished());
                }
                if ("3".equals(d.statementId())) {
                    assertEquals(d.iteration() >= 2,
                            d.statementAnalysis().methodLevelData().linksHaveBeenEstablished());
                }
                if ("4".equals(d.statementId())) {
                    assertEquals(d.iteration() >= 2,
                            d.statementAnalysis().methodLevelData().linksHaveBeenEstablished());
                }
            }
            if ("putAll".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    if (d.iteration() > 2) {
                        String expected = "setOnceMap={modified in context=false:0, not null in context=nullable:1}, this={modified in context=true:1, read=true:1}";
                        assertEquals(expected, d.statementAnalysis().propertiesFromSubAnalysersSortedToString());
                    }
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("accept".equals(d.methodInfo().name) && "$1".equals(d.methodInfo().typeInfo.simpleName)) {
                if (d.variable() instanceof ParameterInfo p && "e".equals(p.name)) {
                    String expect =switch( d.iteration()) {
                        case 0, 1 -> "<mod:V>";
                        case 2 -> "<mod:K>";
                        default -> "nullable instance type Entry<K,V>/*@Identity*/";
                    };
                    assertEquals(expect, d.currentValue().toString());
                }
            }
            if ("get".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo k && "k".equals(k.name)) {
                    if("0.0.0".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<mod:K>" : "nullable instance type K/*@Identity*/";
                        assertEquals(expectValue, d.currentValue().toString());
                        String linked = d.iteration() == 0 ? "this:-1" : "";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }
            if ("put".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "map".equals(fr.fieldInfo.name)) {
                    if ("4".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<f:map>" : "instance type HashMap<K,V>";
                        assertEquals(expectValue, d.currentValue().toString());
                        assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if (d.variable() instanceof ParameterInfo k && "k".equals(k.name)) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("nullable instance type K/*@Identity*/", d.currentValue().toString());
                        assertEquals("", d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("3.0.0".equals(d.statementId())) {
                        String expectValue = d.iteration() <= 1 ? "<mod:K>" : "nullable instance type K/*@Identity*/";
                        assertEquals(expectValue, d.currentValue().toString());
                        String linked = d.iteration() <= 1 ? "this:-1" : "";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if (d.variable() instanceof ParameterInfo v && "v".equals(v.name)) {
                    if ("1".equals(d.statementId())) {
                        assertEquals("nullable instance type V", d.currentValue().toString());
                        assertEquals("", d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("3.0.0".equals(d.statementId())) {
                        String expectValue = d.iteration() <= 1 ? "<p:v>" : "nullable instance type V";
                        assertEquals(expectValue, d.currentValue().toString());
                        assertEquals("", d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("3".equals(d.statementId())) {
                        assertEquals("nullable instance type V", d.currentValue().toString());
                        assertEquals("", d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("4".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<p:v>" : "nullable instance type V";
                        assertEquals(expectValue, d.currentValue().toString());
                        String lvs = d.iteration() <= 1 ? "k:-1,this.map:-1,this:-1" : "k:4,this.map:4";
                        assertEquals(lvs, d.variableInfo().getLinkedVariables().toString());
                    }
                }
            }
            if ("isSet".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "map".equals(fr.fieldInfo.name)) {
                    assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("putAll".equals(d.methodInfo().name)) {
                assertEquals("accept,get,isSet,put,stream", d.methodInfo().methodResolution.get().methodsOfOwnClassReached()
                        .stream().map(m -> m.name).sorted().collect(Collectors.joining(",")));
            }
            if ("put".equals(d.methodInfo().name) && "SetOnceMap".equals(d.methodInfo().typeInfo.simpleName)) {
                assertEquals("get,isSet", d.methodInfo().methodResolution.get().methodsOfOwnClassReached()
                        .stream().map(m -> m.name).sorted().collect(Collectors.joining(",")));

                assertDv(d, 1, DV.TRUE_DV, Property.MODIFIED_METHOD);
            }
            if ("isSet".equals(d.methodInfo().name)) {
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);

                assertDv(d.p(0), 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                assertDv(d.p(0), 1, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
            }
            if ("get".equals(d.methodInfo().name)) {
                assertEquals("isSet", d.methodInfo().methodResolution.get().methodsOfOwnClassReached()
                        .stream().map(m -> m.name).sorted().collect(Collectors.joining(",")));

                assertDv(d, 1, DV.FALSE_DV, Property.MODIFIED_METHOD);
                assertDv(d, 1, DV.FALSE_DV, Property.IDENTITY);

                assertDv(d.p(0), 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                assertDv(d.p(0), 2, DV.FALSE_DV, Property.MODIFIED_VARIABLE);

                String expected = d.iteration() == 0 ? "<m:get>" : "/*inline get*/map.get(k)/*@NotNull*/";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("stream".equals(d.methodInfo().name)) {
                assertEquals("", d.methodInfo().methodResolution.get().methodsOfOwnClassReached()
                        .stream().map(m -> m.name).sorted().collect(Collectors.joining(",")));

                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo map = typeMap.get(Map.class);
            MethodInfo put = map.findUniqueMethod("put", 2);
            assertEquals(DV.TRUE_DV, put.methodAnalysis.get().getProperty(Property.MODIFIED_METHOD));
        };


        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("map".equals(d.fieldInfo().name)) {
                assertEquals(DV.TRUE_DV, d.fieldAnalysis().getProperty(Property.FINAL));
                assertEquals("instance type HashMap<K,V>", d.fieldAnalysis().getValue().toString());
            }
        };

        // 1 potential null pointer warning accepted
        testSupportAndUtilClasses(List.of(SetOnceMap.class, Freezable.class), 0, 2,
                new DebugConfiguration.Builder()
                        .addTypeMapVisitor(typeMapVisitor)
                        .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                        .build());
    }

}
