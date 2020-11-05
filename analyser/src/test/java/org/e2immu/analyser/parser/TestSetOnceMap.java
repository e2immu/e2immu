
/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analyser.VariableInfoImpl;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.e2immu.analyser.config.TypeAnalyserVisitor;
import org.e2immu.analyser.config.TypeContextVisitor;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.InlineValue;
import org.e2immu.analyser.model.abstractvalue.PropertyWrapper;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class TestSetOnceMap extends CommonTestRunner {

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        String name = d.methodInfo().name;
        int iteration = d.iteration();

        if ("get".equals(name) && iteration > 0) {
            Value srv = d.methodAnalysis().getSingleReturnValue();
            Assert.assertEquals("inline get on this.map.get(k),@NotNull", srv.toString());
            InlineValue inlineValue = (InlineValue) srv;
            Assert.assertEquals(InlineValue.Applicability.TYPE, inlineValue.applicability);
            VariableInfoImpl tv = d.getReturnAsVariable();

            Assert.assertNotNull(tv);
            Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, tv.properties.get(VariableProperty.NOT_NULL));
            Assert.assertTrue(tv.getValue() instanceof PropertyWrapper);
            Assert.assertEquals(MultiLevel.EFFECTIVE, MultiLevel.value(
                    d.methodAnalysis().getProperty(VariableProperty.NOT_NULL), MultiLevel.NOT_NULL));

            // independent, because does not return a support data type
            int independent = d.methodAnalysis().getProperty(VariableProperty.INDEPENDENT);
            Assert.assertEquals(MultiLevel.EFFECTIVE, independent);
        }
        if ("getOtherwiseNull".equals(name)) {
            if (iteration > 0) {
                Set<Variable> linkedVariables = d.getReturnAsVariable().linkedVariables.get();
                Assert.assertEquals("0:k,map", linkedVariables.stream().map(Object::toString).collect(Collectors.joining(",")));
                // independent, because does not return a support data type
                int independent = d.methodAnalysis().getProperty(VariableProperty.INDEPENDENT);
                Assert.assertEquals(MultiLevel.EFFECTIVE, independent);
            }
        }
        if ("isEmpty".equals(name)) {
            VariableInfo tv = d.getReturnAsVariable();
            Assert.assertEquals("0 == this.map.size(),?>=0", tv.getValue().toString());

            // there is no reason to have a @Size annotation on this expression
            Assert.assertEquals(Level.DELAY, tv.getProperty(VariableProperty.SIZE));
            if (iteration > 0) {
                Value srv = d.methodAnalysis().getSingleReturnValue();
                Assert.assertEquals("inline isEmpty on 0 == this.map.size(),?>=0", srv.toString());
                // @Size(equals = 0)
                Assert.assertEquals(Level.SIZE_EMPTY, d.methodAnalysis().getProperty(VariableProperty.SIZE));
            }
        }
        if ("stream".equals(name)) {
            Value stream = d.getReturnAsVariable().getValue();
            Assert.assertEquals("this.map.entrySet().stream()", stream.toString());
            Assert.assertEquals(Level.SIZE_COPY_TRUE, d.getProperty(stream, VariableProperty.SIZE_COPY));
        }
        if ("put".equals(name)) {
            if (iteration > 0) {
                Assert.assertEquals("(not (this.map.containsKey(k)) and not (this.frozen))",
                        d.methodAnalysis().getPrecondition().toString());
            }
            if (iteration > 1) {
                Assert.assertEquals("[not (this.frozen), not (this.map.containsKey(k))]",
                        d.methodAnalysis().getPreconditionForMarkAndOnly().toString());
            }
        }
    };

    TypeAnalyserVisitor typeAnalyserVisitor = d -> {
        if ("SetOnceMap".equals(d.typeInfo().simpleName)) {
            Assert.assertEquals("K;V;java.util.Map.Entry<K, V>",
                    d.typeAnalysis().getImplicitlyImmutableDataTypes().stream().map(ParameterizedType::detailedString).sorted().collect(Collectors.joining(";")));

            if (d.iteration() > 1) {
                Assert.assertEquals(2, d.typeAnalysis().getApprovedPreconditions().size());
                Assert.assertEquals("frozen,map", d.typeAnalysis().getApprovedPreconditions().keySet().stream()
                        .sorted()
                        .collect(Collectors.joining(",")));
            }
        }
    };

    // TODO: Accepting one error now: we have not inferred @Size(min = 1) for put (modifying method)
    // This is work for later

    TypeContextVisitor typeContextVisitor = typeContext -> {
        TypeInfo map = typeContext.getFullyQualified(Map.class);
        MethodInfo put = map.findUniqueMethod("put", 2);
        for (ParameterInfo parameterInfo : put.methodInspection.get().parameters) {
            Assert.assertEquals(Level.FALSE, parameterInfo.parameterAnalysis.get().getProperty(VariableProperty.MODIFIED));
        }
        TypeInfo objects = typeContext.getFullyQualified(Objects.class);
        MethodInfo rnn = objects.findUniqueMethod("requireNonNull", 1);
        ParameterInfo p = rnn.methodInspection.get().parameters.get(0);
        Assert.assertEquals(Level.FALSE, p.parameterAnalysis.get().getProperty(VariableProperty.MODIFIED));
    };

    @Test
    public void test() throws IOException {
        testUtilClass(List.of("Freezable", "SetOnceMap"), 1, 1, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .addTypeContextVisitor(typeContextVisitor)
                .build());
    }

}
