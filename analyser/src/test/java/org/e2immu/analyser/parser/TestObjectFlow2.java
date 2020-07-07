
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

import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.testexample.ObjectFlow2;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class TestObjectFlow2 extends CommonTestRunner {

    @Test
    public void test() throws IOException {
        TypeContext typeContext = testClass("ObjectFlow2", 0, 0, new DebugConfiguration.Builder()
                .build());

        TypeInfo hashSet = typeContext.typeStore.get(HashSet.class.getCanonicalName());
        TypeInfo set = typeContext.typeStore.get(Set.class.getCanonicalName());

        TypeInfo objectFlow2 = typeContext.typeStore.get(ObjectFlow2.class.getCanonicalName());
        MethodInfo ofMethod = objectFlow2.typeInspection.get().methods.stream().filter(m -> "of".equals(m.name)).findAny().orElseThrow();
        ObjectFlow newHashSet = ofMethod.methodAnalysis.get().getInternalObjectFlows().filter(of -> of.type.typeInfo == hashSet).findAny().orElseThrow();

        Assert.assertSame(newHashSet, ofMethod.methodAnalysis.get().getReturnedObjectFlow());
        ObjectFlow ofParam = ofMethod.methodInspection.get().parameters.get(0).parameterAnalysis.get().objectFlow;

        MethodInfo useOf = objectFlow2.typeInspection.get().methods.stream().filter(m -> "useOf".equals(m.name)).findAny().orElseThrow();

        ObjectFlow constantX = objectFlow2.typeAnalysis.get().getConstantObjectFlows()
                .filter(of -> of.type.typeInfo == Primitives.PRIMITIVES.stringTypeInfo).findFirst().orElseThrow();
        ObjectFlow.MethodCalls methodCallsOfOfParam = (ObjectFlow.MethodCalls)ofParam.origin ;
        Assert.assertTrue(methodCallsOfOfParam.objectFlows.contains(constantX));

        ObjectFlow useOfFlow = useOf.methodAnalysis.get().getInternalObjectFlows().findAny().orElseThrow();
        Assert.assertSame(set, useOfFlow.type.typeInfo);
        Assert.assertTrue(useOfFlow.origin instanceof ObjectFlow.MethodCalls);
        ObjectFlow.MethodCalls useOfFlowMcs = (ObjectFlow.MethodCalls) useOfFlow.origin;
        Assert.assertTrue(useOfFlowMcs.objectFlows.contains(newHashSet));
        Assert.assertTrue(newHashSet.getNextViaReturnOrFieldAccess().collect(Collectors.toSet()).contains(useOfFlow));

        FieldInfo set1 = objectFlow2.typeInspection.get().fields.stream().filter(f -> "set1".equals(f.name)).findAny().orElseThrow();
        ObjectFlow set1ObjectFlow = set1.fieldAnalysis.get().getObjectFlow();

        Assert.assertTrue(set1ObjectFlow.origin instanceof ObjectFlow.MethodCalls);
        ObjectFlow.MethodCalls set1ObjectFlowMcs = (ObjectFlow.MethodCalls) set1ObjectFlow.origin;
        Assert.assertEquals(1, set1ObjectFlowMcs.objectFlows.size());
        Assert.assertTrue(set1ObjectFlowMcs.objectFlows.contains(newHashSet));
        Assert.assertTrue(newHashSet.getNextViaReturnOrFieldAccess().collect(Collectors.toSet()).contains(set1ObjectFlow));

        Assert.assertEquals(2L, newHashSet.getObjectAccesses().count());
        // TODO check that ofParam is linked to add method access
    }

}
