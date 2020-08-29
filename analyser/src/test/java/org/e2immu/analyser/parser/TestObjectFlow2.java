
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
import org.e2immu.analyser.config.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.objectflow.Origin;
import org.e2immu.analyser.objectflow.access.MethodAccess;
import org.e2immu.analyser.testexample.ObjectFlow2;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class TestObjectFlow2 extends CommonTestRunner {

    public TestObjectFlow2() {
        super(true);
    }

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("of".equals(d.methodInfo.name) && "4".equals(d.statementId) && "res".equals(d.variableName)) {
            Assert.assertSame(Origin.INTERNAL, d.objectFlow.origin);
            ObjectFlow parent = d.objectFlow.getPrevious().findFirst().orElseThrow();
            Assert.assertSame(Origin.NEW_OBJECT_CREATION, parent.origin);
        }
    };

    @Test
    public void test() throws IOException {
        TypeContext typeContext = testClass("ObjectFlow2", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());

        TypeInfo hashSet = typeContext.typeStore.get(HashSet.class.getCanonicalName());
        TypeInfo set = typeContext.typeStore.get(Set.class.getCanonicalName());

        TypeInfo objectFlow2 = typeContext.typeStore.get(ObjectFlow2.class.getCanonicalName());
        MethodInfo ofMethod = objectFlow2.typeInspection.get().methods.stream().filter(m -> "of".equals(m.name)).findAny().orElseThrow();
        ObjectFlow newHashSet = ofMethod.methodAnalysis.get().internalObjectFlows.get().stream()
                .filter(of -> of.type.typeInfo == hashSet)
                .filter(of -> of.origin == Origin.NEW_OBJECT_CREATION)
                .findAny().orElseThrow();
        Assert.assertEquals(1L, newHashSet.getNext().count());
        ObjectFlow newHashSet2 = newHashSet.getNext().findFirst().orElseThrow();
        Assert.assertSame(newHashSet2, ofMethod.methodAnalysis.get().getObjectFlow());

        ObjectFlow ofParam = ofMethod.methodInspection.get().parameters.get(0).parameterAnalysis.get().getObjectFlow();

        MethodInfo useOf = objectFlow2.typeInspection.get().methods.stream().filter(m -> "useOf".equals(m.name)).findAny().orElseThrow();

        ObjectFlow constantX = objectFlow2.typeAnalysis.get().getConstantObjectFlows()
                .filter(of -> of.type.typeInfo == Primitives.PRIMITIVES.stringTypeInfo).findFirst().orElseThrow();
        Assert.assertTrue(ofParam.containsPrevious(constantX));

        ObjectFlow useOfFlow = useOf.methodAnalysis.get().internalObjectFlows.get().stream()
                .filter(of -> of.type.typeInfo == set)
                .findAny().orElseThrow();
        Assert.assertSame(Origin.RESULT_OF_METHOD, useOfFlow.origin);
        Assert.assertTrue(useOfFlow.containsPrevious(newHashSet2));
        Assert.assertTrue(newHashSet2.getNext().collect(Collectors.toSet()).contains(useOfFlow));

        FieldInfo set1 = objectFlow2.typeInspection.get().fields.stream().filter(f -> "set1".equals(f.name)).findAny().orElseThrow();
        ObjectFlow set1ObjectFlow = set1.fieldAnalysis.get().getObjectFlow();

        Assert.assertSame(Origin.RESULT_OF_METHOD, set1ObjectFlow.origin);
        Assert.assertEquals(1L, set1ObjectFlow.getPrevious().count());
        Assert.assertTrue(set1ObjectFlow.containsPrevious(newHashSet2));
        Assert.assertTrue(newHashSet2.getNext().collect(Collectors.toSet()).contains(set1ObjectFlow));

        Assert.assertEquals(0L, newHashSet.getNonModifyingAccesses().count());
        Assert.assertEquals(0L, newHashSet2.getNonModifyingAccesses().count());
        MethodAccess add1 = newHashSet.getModifyingAccess();
        Assert.assertEquals("add", add1.methodInfo.name);
        MethodAccess add2 = newHashSet2.getModifyingAccess();
        Assert.assertEquals("add", add2.methodInfo.name);
    }

}
