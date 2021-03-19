
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
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.objectflow.Origin;
import org.e2immu.analyser.objectflow.access.MethodAccess;
import org.e2immu.analyser.testexample.ObjectFlow2;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class TestObjectFlow2 extends CommonTestRunner {

    public TestObjectFlow2() {
        super(true);
    }

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("of".equals(d.methodInfo().name) && "4".equals(d.statementId()) && "res".equals(d.variableName())) {
            ObjectFlow objectFlow = d.variableInfo().getObjectFlow();
            assertSame(Origin.INTERNAL, objectFlow.origin);
            ObjectFlow parent = objectFlow.getPrevious().findFirst().orElseThrow();
            assertSame(Origin.NEW_OBJECT_CREATION, parent.origin);
        }
    };

    @Test
    public void test() throws IOException {
        TypeContext typeContext = testClass("ObjectFlow2", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());

        TypeInfo hashSet = typeContext.typeMapBuilder.get(HashSet.class.getCanonicalName());
        TypeInfo set = typeContext.typeMapBuilder.get(Set.class.getCanonicalName());

        TypeInfo objectFlow2 = typeContext.typeMapBuilder.get(ObjectFlow2.class.getCanonicalName());
        MethodInfo ofMethod = objectFlow2.typeInspection.get().methods().stream().filter(m -> "of".equals(m.name)).findAny().orElseThrow();
        ObjectFlow newHashSet = ofMethod.methodAnalysis.get().getInternalObjectFlows().stream()
                .filter(of -> of.type.typeInfo == hashSet)
                .filter(of -> of.origin == Origin.NEW_OBJECT_CREATION)
                .findAny().orElseThrow();
        assertEquals(1L, newHashSet.getNext().count());
        ObjectFlow newHashSet2 = newHashSet.getNext().findFirst().orElseThrow();
        assertSame(newHashSet2, ofMethod.methodAnalysis.get().getObjectFlow());

        ObjectFlow ofParam = ofMethod.methodInspection.get().getParameters().get(0).parameterAnalysis.get().getObjectFlow();

        MethodInfo useOf = objectFlow2.typeInspection.get().methods().stream().filter(m -> "useOf".equals(m.name)).findAny().orElseThrow();

        ObjectFlow constantX = objectFlow2.typeAnalysis.get().getConstantObjectFlows().stream()
                .filter(of -> of.type.typeInfo == typeContext.getPrimitives().stringTypeInfo).findFirst().orElseThrow();
        assertTrue(ofParam.containsPrevious(constantX));

        ObjectFlow useOfFlow = useOf.methodAnalysis.get().getInternalObjectFlows().stream()
                .filter(of -> of.type.typeInfo == set)
                .findAny().orElseThrow();
        assertSame(Origin.RESULT_OF_METHOD, useOfFlow.origin);
        assertTrue(useOfFlow.containsPrevious(newHashSet2));
        assertTrue(newHashSet2.getNext().collect(Collectors.toSet()).contains(useOfFlow));

        FieldInfo set1 = objectFlow2.typeInspection.get().fields().stream().filter(f -> "set1".equals(f.name)).findAny().orElseThrow();
        ObjectFlow set1ObjectFlow = set1.fieldAnalysis.get().getObjectFlow();

        assertSame(Origin.RESULT_OF_METHOD, set1ObjectFlow.origin);
        assertEquals(1L, set1ObjectFlow.getPrevious().count());
        assertTrue(set1ObjectFlow.containsPrevious(newHashSet2));
        assertTrue(newHashSet2.getNext().collect(Collectors.toSet()).contains(set1ObjectFlow));

        assertEquals(0L, newHashSet.getNonModifyingAccesses().count());
        assertEquals(0L, newHashSet2.getNonModifyingAccesses().count());
        MethodAccess add1 = newHashSet.getModifyingAccess();
        assertEquals("add", add1.methodInfo.name);
        MethodAccess add2 = newHashSet2.getModifyingAccess();
        assertEquals("add", add2.methodInfo.name);
    }

}
