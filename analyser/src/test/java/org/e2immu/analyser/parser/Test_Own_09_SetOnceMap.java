
/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.LinkedVariables;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.variable.FieldReference;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Test_Own_09_SetOnceMap extends CommonTestRunner {

    public Test_Own_09_SetOnceMap() {
        super(true);
    }

    @Test
    public void test() throws IOException {
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("SetOnceMap".equals(d.typeInfo().simpleName)) {
                Assert.assertEquals("Type param K, Type param V", d.typeAnalysis().getImplicitlyImmutableDataTypes()
                        .stream().map(ParameterizedType::toString).sorted().collect(Collectors.joining(", ")));
                int expectContainer = d.iteration() <= 3 ? Level.DELAY : Level.TRUE;
                Assert.assertEquals(expectContainer, d.typeAnalysis().getProperty(VariableProperty.CONTAINER));
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("put".equals(d.methodInfo().name)) {
                if ("3.0.0".equals(d.statementId())) {
                    String expect = d.iteration() <= 1 ? "<precondition>" : "!map.containsKey(k)";
                    Assert.assertEquals(expect, d.statementAnalysis().stateData.getPrecondition().toString());
                    Assert.assertEquals(d.iteration() >= 3,
                            d.statementAnalysis().methodLevelData.linksHaveBeenEstablished.isSet());
                }
                if ("3".equals(d.statementId())) {
                    Assert.assertEquals(d.iteration() >= 3,
                            d.statementAnalysis().methodLevelData.linksHaveBeenEstablished.isSet());
                }
                if ("4".equals(d.statementId())) {
                    Assert.assertEquals(d.iteration() >= 3,
                            d.statementAnalysis().methodLevelData.linksHaveBeenEstablished.isSet());
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("accept".equals(d.methodInfo().name) && "$1".equals(d.methodInfo().typeInfo.simpleName)) {
                if (d.variable() instanceof FieldReference fr && "map".equals(fr.fieldInfo.name)) {
                    Assert.assertTrue(d.iteration() >= 2);
                    Assert.assertEquals("instance type HashMap<K,V>", d.currentValue().toString());
                }
            }
            if ("get".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo k && "k".equals(k.name)) {
                    String expectValue = d.iteration() <= 1 ? "<p:k>" : "nullable instance type K";
                    Assert.assertEquals(expectValue, d.currentValue().toString());
                    String expectDelay = d.iteration() <= 1 ? LinkedVariables.DELAY_STRING : "";
                    Assert.assertEquals(expectDelay, d.variableInfo().getLinkedVariables().toString());
                    int expectCm = d.iteration() <= 1 ? Level.DELAY : Level.FALSE;
                    Assert.assertEquals(expectCm, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                }
            }
            if ("put".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "map".equals(fr.fieldInfo.name)) {
                    if ("4".equals(d.statementId())) {
                        String expectValue = d.iteration() <= 1 ? "<f:map>" : "instance type HashMap<K,V>";
                        Assert.assertEquals(expectValue, d.currentValue().toString());
                        int expectCm = d.iteration() <= 1 ? Level.DELAY : Level.TRUE;
                        Assert.assertEquals(expectCm, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                    }
                }
                if (d.variable() instanceof ParameterInfo k && "k".equals(k.name)) {
                    if ("0".equals(d.statementId())) {
                        Assert.assertEquals("nullable instance type K", d.currentValue().toString());
                        Assert.assertEquals("", d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("3.0.0".equals(d.statementId())) {
                        String expectValue = d.iteration() <= 1 ? "<p:k>" : "nullable instance type K";
                        Assert.assertEquals(expectValue, d.currentValue().toString());
                        String expectDelay = d.iteration() <= 1 ? LinkedVariables.DELAY_STRING : "";
                        Assert.assertEquals(expectDelay, d.variableInfo().getLinkedVariables().toString());
                        int expectCm = d.iteration() <= 2 ? Level.DELAY : Level.FALSE;
                        Assert.assertEquals(expectCm, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                    }
                }
                if (d.variable() instanceof ParameterInfo v && "v".equals(v.name)) {
                    if ("1".equals(d.statementId())) {
                        Assert.assertEquals("nullable instance type V", d.currentValue().toString());
                        Assert.assertEquals("", d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("3.0.0".equals(d.statementId())) {
                        String expectValue = d.iteration() <= 1 ? "<p:v>" : "nullable instance type V";
                        Assert.assertEquals(expectValue, d.currentValue().toString());
                        String expectDelay = d.iteration() <= 1 ? LinkedVariables.DELAY_STRING : "";
                        Assert.assertEquals(expectDelay, d.variableInfo().getLinkedVariables().toString());
                        int expectCm = d.iteration() <= 1 ? Level.DELAY : Level.FALSE;
                        Assert.assertEquals(expectCm, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                    }
                    if ("3".equals(d.statementId())) {
                        Assert.assertEquals("nullable instance type V", d.currentValue().toString());
                        String expectDelay = d.iteration() <= 1 ? LinkedVariables.DELAY_STRING : "";
                        Assert.assertEquals(expectDelay, d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("4".equals(d.statementId())) {
                        String expectValue = d.iteration() <= 1 ? "<p:v>" : "nullable instance type V";
                        Assert.assertEquals(expectValue, d.currentValue().toString());
                        String expectDelay = d.iteration() <= 1 ? LinkedVariables.DELAY_STRING : "";
                        Assert.assertEquals(expectDelay, d.variableInfo().getLinkedVariables().toString());
                    }
                }
            }
            if ("isSet".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "map".equals(fr.fieldInfo.name)) {
                    int expectCm = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                    Assert.assertEquals(expectCm, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("putAll".equals(d.methodInfo().name)) {
                Assert.assertEquals("get,isSet,put,stream", d.methodInfo().methodResolution.get().methodsOfOwnClassReached()
                        .stream().map(m -> m.name).sorted().collect(Collectors.joining(",")));
            }
            if ("put".equals(d.methodInfo().name) && "SetOnceMap".equals(d.methodInfo().typeInfo.simpleName)) {

                Assert.assertEquals("get,isSet", d.methodInfo().methodResolution.get().methodsOfOwnClassReached()
                        .stream().map(m -> m.name).sorted().collect(Collectors.joining(",")));

                int expectMm = d.iteration() <= 2 ? Level.DELAY : Level.TRUE;
                Assert.assertEquals(expectMm, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
            }
            if ("isSet".equals(d.methodInfo().name)) {
                int expectMm = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                Assert.assertEquals(expectMm, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
                ParameterAnalysis k = d.parameterAnalyses().get(0);
                int expectCm = d.iteration() <= 1 ? Level.DELAY : Level.FALSE;
                Assert.assertEquals(expectCm, k.getProperty(VariableProperty.CONTEXT_MODIFIED));
                int expectMv = d.iteration() <= 1 ? Level.DELAY : Level.FALSE;
                Assert.assertEquals(expectMv, k.getProperty(VariableProperty.MODIFIED_VARIABLE));
            }
            if ("get".equals(d.methodInfo().name)) {
                Assert.assertEquals("isSet", d.methodInfo().methodResolution.get().methodsOfOwnClassReached()
                        .stream().map(m -> m.name).sorted().collect(Collectors.joining(",")));

                int expectMm = d.iteration() <= 1 ? Level.DELAY : Level.FALSE;
                Assert.assertEquals(expectMm, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
                int expectIdentity = d.iteration() <= 1 ? Level.DELAY : Level.FALSE;
                Assert.assertEquals(expectIdentity, d.methodAnalysis().getProperty(VariableProperty.IDENTITY));

                ParameterAnalysis k = d.parameterAnalyses().get(0);
                int expectCm = d.iteration() <= 2 ? Level.DELAY : Level.FALSE;
                Assert.assertEquals(expectCm, k.getProperty(VariableProperty.CONTEXT_MODIFIED));
                int expectMv = d.iteration() <= 2 ? Level.DELAY : Level.FALSE;
                Assert.assertEquals(expectMv, k.getProperty(VariableProperty.MODIFIED_VARIABLE));
            }
            if ("stream".equals(d.methodInfo().name)) {
                Assert.assertEquals("", d.methodInfo().methodResolution.get().methodsOfOwnClassReached()
                        .stream().map(m -> m.name).sorted().collect(Collectors.joining(",")));

                int expectMm = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                Assert.assertEquals(expectMm, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
            }
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo map = typeMap.get(Map.class);
            MethodInfo put = map.findUniqueMethod("put", 2);
            Assert.assertEquals(Level.TRUE, put.methodAnalysis.get().getProperty(VariableProperty.MODIFIED_METHOD));
        };


        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("map".equals(d.fieldInfo().name)) {
                Assert.assertEquals(Level.TRUE, d.fieldAnalysis().getProperty(VariableProperty.FINAL));
                Assert.assertEquals("instance type HashMap<K,V>", d.fieldAnalysis().getEffectivelyFinalValue().toString());
            }
        };

        testUtilClass(List.of("SetOnceMap", "Freezable"), 0, 0, new DebugConfiguration.Builder()
                .addTypeMapVisitor(typeMapVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }

}