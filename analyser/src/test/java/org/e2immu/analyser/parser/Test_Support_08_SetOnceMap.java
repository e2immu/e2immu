
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

package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.LinkedVariables;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Test_Support_08_SetOnceMap extends CommonTestRunner {

    public Test_Support_08_SetOnceMap() {
        super(true);
    }

    @Test
    public void test() throws IOException {
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("SetOnceMap".equals(d.typeInfo().simpleName)) {
                assertEquals("Type java.util.function.Function<K,V>, Type param K, Type param V",
                        d.typeAnalysis().getTransparentTypes().toString());
                int expectContainer = d.iteration() <= 3 ? Level.DELAY : Level.TRUE;
                assertEquals(expectContainer, d.typeAnalysis().getProperty(VariableProperty.CONTAINER));
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("put".equals(d.methodInfo().name)) {
                if ("3.0.0".equals(d.statementId())) {
                    String expect = switch (d.iteration()) {
                        case 0 -> "<precondition>";
                        case 1 -> "!<m:containsKey>";
                        default -> "!map.containsKey(k)";
                    };
                    assertEquals(expect, d.statementAnalysis().stateData.getPrecondition().expression().toString());
                    assertEquals(d.iteration() >= 3,
                            d.statementAnalysis().methodLevelData.linksHaveBeenEstablished.isSet());
                }
                if ("3".equals(d.statementId())) {
                    assertEquals(d.iteration() >= 3,
                            d.statementAnalysis().methodLevelData.linksHaveBeenEstablished.isSet());
                }
                if ("4".equals(d.statementId())) {
                    assertEquals(d.iteration() >= 3,
                            d.statementAnalysis().methodLevelData.linksHaveBeenEstablished.isSet());
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("accept".equals(d.methodInfo().name) && "$1".equals(d.methodInfo().typeInfo.simpleName)) {
                if (d.variable() instanceof FieldReference fr && "map".equals(fr.fieldInfo.name)) {
                    assertTrue(d.iteration() >= 2);
                    assertEquals("instance type HashMap<K,V>", d.currentValue().toString());
                }
            }
            if ("get".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo k && "k".equals(k.name)) {
                    String expectValue = d.iteration() <= 1 ? "<p:k>" : "nullable instance type K/*@Identity*/";
                    assertEquals(expectValue, d.currentValue().toString());
                    String expectDelay = d.iteration() <= 1 ? LinkedVariables.DELAY_STRING : "";
                    assertEquals(expectDelay, d.variableInfo().getLinkedVariables().toString());
                    int expectCm = d.iteration() <= 1 ? Level.DELAY : Level.FALSE;
                    assertEquals(expectCm, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                }
            }
            if ("put".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "map".equals(fr.fieldInfo.name)) {
                    if ("4".equals(d.statementId())) {
                        String expectValue = d.iteration() <= 1 ? "<f:map>" : "instance type HashMap<K,V>";
                        assertEquals(expectValue, d.currentValue().toString());
                        int expectCm = d.iteration() <= 1 ? Level.DELAY : Level.TRUE;
                        assertEquals(expectCm, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                    }
                }
                if (d.variable() instanceof ParameterInfo k && "k".equals(k.name)) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("nullable instance type K/*@Identity*/", d.currentValue().toString());
                        assertEquals("", d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("3.0.0".equals(d.statementId())) {
                        String expectValue = d.iteration() <= 1 ? "<p:k>" : "nullable instance type K/*@Identity*/";
                        assertEquals(expectValue, d.currentValue().toString());
                        String expectDelay = d.iteration() <= 1 ? LinkedVariables.DELAY_STRING : "";
                        assertEquals(expectDelay, d.variableInfo().getLinkedVariables().toString());
                        int expectCm = d.iteration() <= 2 ? Level.DELAY : Level.FALSE;
                        assertEquals(expectCm, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
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
                        String expectDelay = d.iteration() <= 1 ? LinkedVariables.DELAY_STRING : "";
                        assertEquals(expectDelay, d.variableInfo().getLinkedVariables().toString());
                        int expectCm = d.iteration() <= 1 ? Level.DELAY : Level.FALSE;
                        assertEquals(expectCm, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                    }
                    if ("3".equals(d.statementId())) {
                        assertEquals("nullable instance type V", d.currentValue().toString());
                        String expectDelay = d.iteration() <= 1 ? LinkedVariables.DELAY_STRING : "";
                        assertEquals(expectDelay, d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("4".equals(d.statementId())) {
                        String expectValue = d.iteration() <= 1 ? "<p:v>" : "nullable instance type V";
                        assertEquals(expectValue, d.currentValue().toString());
                        String expectDelay = d.iteration() <= 1 ? LinkedVariables.DELAY_STRING : "";
                        assertEquals(expectDelay, d.variableInfo().getLinkedVariables().toString());
                    }
                }
            }
            if ("isSet".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "map".equals(fr.fieldInfo.name)) {
                    int expectCm = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                    assertEquals(expectCm, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("putAll".equals(d.methodInfo().name)) {
                assertEquals("get,isSet,put,stream", d.methodInfo().methodResolution.get().methodsOfOwnClassReached()
                        .stream().map(m -> m.name).sorted().collect(Collectors.joining(",")));
            }
            if ("put".equals(d.methodInfo().name) && "SetOnceMap".equals(d.methodInfo().typeInfo.simpleName)) {

                assertEquals("get,isSet", d.methodInfo().methodResolution.get().methodsOfOwnClassReached()
                        .stream().map(m -> m.name).sorted().collect(Collectors.joining(",")));

                int expectMm = d.iteration() <= 1 ? Level.DELAY : Level.TRUE;
                assertEquals(expectMm, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
            }
            if ("isSet".equals(d.methodInfo().name)) {
                int expectMm = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                assertEquals(expectMm, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
                ParameterAnalysis k = d.parameterAnalyses().get(0);
                int expectCm = d.iteration() <= 1 ? Level.DELAY : Level.FALSE;
                assertEquals(expectCm, k.getProperty(VariableProperty.CONTEXT_MODIFIED));
                int expectMv = d.iteration() <= 1 ? Level.DELAY : Level.FALSE;
                assertEquals(expectMv, k.getProperty(VariableProperty.MODIFIED_VARIABLE));
            }
            if ("get".equals(d.methodInfo().name)) {
                assertEquals("isSet", d.methodInfo().methodResolution.get().methodsOfOwnClassReached()
                        .stream().map(m -> m.name).sorted().collect(Collectors.joining(",")));

                int expectMm = d.iteration() <= 1 ? Level.DELAY : Level.FALSE;
                assertEquals(expectMm, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
                int expectIdentity = d.iteration() <= 1 ? Level.DELAY : Level.FALSE;
                assertEquals(expectIdentity, d.methodAnalysis().getProperty(VariableProperty.IDENTITY));

                ParameterAnalysis k = d.parameterAnalyses().get(0);
                int expectCm = d.iteration() <= 2 ? Level.DELAY : Level.FALSE;
                assertEquals(expectCm, k.getProperty(VariableProperty.CONTEXT_MODIFIED));
                int expectMv = d.iteration() <= 2 ? Level.DELAY : Level.FALSE;
                assertEquals(expectMv, k.getProperty(VariableProperty.MODIFIED_VARIABLE));
            }
            if ("stream".equals(d.methodInfo().name)) {
                assertEquals("", d.methodInfo().methodResolution.get().methodsOfOwnClassReached()
                        .stream().map(m -> m.name).sorted().collect(Collectors.joining(",")));

                int expectMm = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                assertEquals(expectMm, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
            }
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo map = typeMap.get(Map.class);
            MethodInfo put = map.findUniqueMethod("put", 2);
            assertEquals(Level.TRUE, put.methodAnalysis.get().getProperty(VariableProperty.MODIFIED_METHOD));
        };


        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("map".equals(d.fieldInfo().name)) {
                assertEquals(Level.TRUE, d.fieldAnalysis().getProperty(VariableProperty.FINAL));
                assertEquals("instance type HashMap<K,V>", d.fieldAnalysis().getEffectivelyFinalValue().toString());
            }
        };

        testSupportClass(List.of("SetOnceMap", "Freezable"), 0, 2,
                new DebugConfiguration.Builder()
                        .addTypeMapVisitor(typeMapVisitor)
                        .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                        .build());
    }

}
