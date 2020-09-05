
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

import org.e2immu.analyser.analyser.TransferValue;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.e2immu.analyser.config.TypeAnalyserVisitor;
import org.e2immu.analyser.config.TypeContextVisitor;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.InlineValue;
import org.e2immu.analyser.model.abstractvalue.PropertyWrapper;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class TestSetOnceMap extends CommonTestRunner {

    MethodAnalyserVisitor methodAnalyserVisitor = (iteration, methodInfo) -> {
        if ("get".equals(methodInfo.name) && iteration > 0) {
            Value srv = methodInfo.methodAnalysis.get().singleReturnValue.get();
            Assert.assertEquals("inline get on this.map.get(k),@NotNull", srv.toString());
            InlineValue inlineValue = (InlineValue) srv;
            Assert.assertEquals(InlineValue.Applicability.TYPE, inlineValue.applicability);
            TransferValue tv = methodInfo.methodAnalysis.get().returnStatementSummaries.get("1");

            Assert.assertNotNull(tv);
            Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, tv.properties.get(VariableProperty.NOT_NULL));
            Assert.assertTrue(tv.value.get() instanceof PropertyWrapper);
            Assert.assertEquals(MultiLevel.EFFECTIVE, MultiLevel.value(
                    methodInfo.methodAnalysis.get().getProperty(VariableProperty.NOT_NULL), MultiLevel.NOT_NULL));

            // independent, because does not return a support data type
            int independent = methodInfo.methodAnalysis.get().getProperty(VariableProperty.INDEPENDENT);
            Assert.assertEquals(Level.TRUE, independent);
        }
        if ("getOtherwiseNull".equals(methodInfo.name)) {
            if (iteration > 0) {
                Set<Variable> linkedVariables = methodInfo.methodAnalysis.get().returnStatementSummaries.get("0").linkedVariables.get();
                Assert.assertEquals("0:k,map", linkedVariables.stream().map(Object::toString).collect(Collectors.joining(",")));
                // independent, because does not return a support data type
                int independent = methodInfo.methodAnalysis.get().getProperty(VariableProperty.INDEPENDENT);
                Assert.assertEquals(Level.TRUE, independent);
            }
        }
        if ("isEmpty".equals(methodInfo.name)) {
            TransferValue tv = methodInfo.methodAnalysis.get().returnStatementSummaries.get("0");
            Assert.assertNotNull(tv);
            Assert.assertEquals("0 == this.map.size(),?>=0", tv.value.get().toString());

            // there is no reason to have a @Size annotation on this expression
            Assert.assertEquals(Level.DELAY, tv.getProperty(VariableProperty.SIZE));
            if (iteration > 0) {
                Value srv = methodInfo.methodAnalysis.get().singleReturnValue.get();
                Assert.assertEquals("inline isEmpty on 0 == this.map.size(),?>=0", srv.toString());
                // @Size(equals = 0)
                Assert.assertEquals(Level.SIZE_EMPTY, methodInfo.methodAnalysis.get().getProperty(VariableProperty.SIZE));
            }
        }
        if ("stream".equals(methodInfo.name)) {
            TransferValue tv = methodInfo.methodAnalysis.get().returnStatementSummaries.get("0");
            Assert.assertNotNull(tv);
            Value stream = tv.value.get();
            Assert.assertEquals("this.map.entrySet().stream()", stream.toString());
            Assert.assertEquals(Level.SIZE_COPY_TRUE, stream.getPropertyOutsideContext(VariableProperty.SIZE_COPY));
        }
        if ("put".equals(methodInfo.name)) {
            if (iteration > 0) {
                Assert.assertEquals("(not (this.map.containsKey(k)) and not (this.frozen))", methodInfo.methodAnalysis.get().precondition.get().toString());
            }
            if (iteration > 1) {
                Assert.assertEquals("(not (this.map.containsKey(k)) and not (this.frozen))", methodInfo.methodAnalysis.get().preconditionForMarkAndOnly.get().toString());
            }
        }
    };

    TypeAnalyserVisitor typeAnalyserVisitor = (iteration, typeInfo) -> {
        if ("SetOnceMap".equals(typeInfo.simpleName)) {
            Assert.assertEquals(1, typeInfo.typeAnalysis.get().supportDataTypes.get().size());
            ParameterizedType supportDataType = typeInfo.typeAnalysis.get().supportDataTypes.get().stream().findFirst().orElseThrow();
            Assert.assertEquals("java.util.Map<K, V>", supportDataType.detailedString());

            if (iteration > 1) {
                Assert.assertEquals(1, typeInfo.typeAnalysis.get().approvedPreconditions.size());
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
