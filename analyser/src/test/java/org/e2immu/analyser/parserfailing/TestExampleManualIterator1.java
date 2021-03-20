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

package org.e2immu.analyser.parserfailing;

import org.e2immu.analyser.analyser.MethodLevelData;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.visitor.*;
import org.e2immu.annotation.AnnotationMode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class TestExampleManualIterator1 extends CommonTestRunner {

    public TestExampleManualIterator1() {
        super(true);
    }

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        MethodLevelData methodLevelData = d.methodAnalysis().methodLevelData();
        if ("iterator".equals(d.methodInfo().name)) {
            //  assertEquals(MultiLevel.EFFECTIVE, methodInfo.methodAnalysis.get().getProperty(VariableProperty.INDEPENDENT));
            VariableInfo variableInfo = d.getReturnAsVariable();
            assertTrue(variableInfo.isNotDelayed());
        }

        if (Set.of("hasNext", "next").contains(d.methodInfo().name) && "MyIteratorImpl".equals(d.methodInfo().typeInfo.simpleName)) {
            if (d.iteration() > 0) {
                assertTrue(methodLevelData.linksHaveBeenEstablished.isSet());
            }
        }
    };

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("hasNext".equals(d.methodInfo().name) && "MyIteratorImpl.this.list".equals(d.variableName())) {
            assertEquals(Level.FALSE, d.getProperty(VariableProperty.MODIFIED_VARIABLE));
        }
        if ("iterator".equals(d.methodInfo().name) && "ExampleManualIterator1.this.list".equals(d.variableName()) && d.iteration() > 1) {
            assertEquals(Level.FALSE, d.getProperty(VariableProperty.MODIFIED_VARIABLE));
        }
    };

    TypeAnalyserVisitor typeAnalyserVisitor = d -> {
        TypeInfo typeInfo = d.typeInfo();
        if ("MyConsumer".equals(typeInfo.simpleName)) {
            MethodInfo accept = typeInfo.findUniqueMethod("accept", 1);
            ParameterInfo param0 = accept.methodInspection.get().getParameters().get(0);
            assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL,
                    param0.parameterAnalysis.get().getProperty(VariableProperty.NOT_NULL_EXPRESSION));
        }
        if ("MyIterator".equals(typeInfo.simpleName)) {
            //MethodInfo hasNext = typeInfo.findUniqueMethod("hasNext", 0);
            assertSame(AnnotationMode.DEFENSIVE, typeInfo.typeInspection.get().annotationMode());
            // assertEquals(Level.TRUE, hasNext.methodAnalysis.get().getProperty(VariableProperty.MODIFIED));
        }
        if ("ExampleManualIterator1".equals(typeInfo.simpleName)) {
            assertEquals("E", d.typeAnalysis().getImplicitlyImmutableDataTypes()
                    .stream().map(ParameterizedType::detailedString).sorted().collect(Collectors.joining(";")));
        }
        if ("MyIteratorImpl".equals(typeInfo.simpleName)) {
            int container = d.typeAnalysis().getProperty(VariableProperty.CONTAINER);
            int expectContainer = d.iteration() < 2 ? Level.DELAY : Level.TRUE;
            assertEquals(expectContainer, container);

            int independent = d.typeAnalysis().getProperty(VariableProperty.INDEPENDENT);
            int expectIndependent = d.iteration() < 2 ? Level.DELAY : Level.TRUE;
            assertEquals(expectIndependent, independent);
        }
    };

    TypeMapVisitor typeMapVisitor = typeMap -> {
        TypeInfo list = typeMap.get(List.class);
        assertSame(AnnotationMode.DEFENSIVE, list.typeInspection.get().annotationMode());
        MethodInfo size = list.findUniqueMethod("size", 0);
        assertEquals(Level.FALSE, size.methodAnalysis.get().getProperty(VariableProperty.MODIFIED_METHOD));
    };

    FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
        FieldInfo fieldInfo = d.fieldInfo();
        int iteration = d.iteration();
        if ("list".equals(fieldInfo.name) && "MyIteratorImpl".equals(fieldInfo.owner.simpleName)) {
            int modified = d.fieldAnalysis().getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD);
            int expect = iteration <= 1 ? Level.DELAY : Level.FALSE;
            assertEquals(expect, modified);

            if (iteration > 0) {
                assertEquals("", d.fieldAnalysis().getLinkedVariables().toString());
            }
        }
        if ("list".equals(fieldInfo.name) && "ExampleManualIterator1".equals(fieldInfo.owner.simpleName)) {
            if (iteration > 0) {
                MethodInfo constructor = fieldInfo.owner.findConstructor(1);
                MethodAnalysis constructorMa = d.evaluationContext().getAnalyserContext().getMethodAnalysis(constructor);
                VariableInfo constructorTv = constructorMa.getLastStatement().getLatestVariableInfo(fieldInfo.fullyQualifiedName());

                assertTrue(constructorTv.isRead());
                assertEquals(Level.TRUE, constructorTv.getProperty(VariableProperty.MODIFIED_VARIABLE));

                MethodInfo visit = fieldInfo.owner.findUniqueMethod("visit", 1);
                MethodAnalysis visitMa = d.evaluationContext().getAnalyserContext().getMethodAnalysis(visit);
                VariableInfo visitTv = visitMa.getLastStatement().getLatestVariableInfo(fieldInfo.fullyQualifiedName());

                assertTrue(visitTv.isRead());
                assertEquals(Level.FALSE, visitTv.getProperty(VariableProperty.MODIFIED_VARIABLE));

                MethodInfo iterator = fieldInfo.owner.findUniqueMethod("iterator", 0);
                MethodAnalysis iteratorMa = d.evaluationContext().getAnalyserContext().getMethodAnalysis(iterator);
                VariableInfo iteratorTv = iteratorMa.getLastStatement().getLatestVariableInfo(fieldInfo.fullyQualifiedName());

                assertTrue(iteratorTv.isRead());

                if (iteration > 1) {
                    assertEquals(Level.FALSE, iteratorTv.getProperty(VariableProperty.MODIFIED_VARIABLE));
                }
            }
        }
    };

    // TODO we allow for one error at the moment, a transfer of @Size from Collections.addAll which has not yet been implemented
    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if ("visit".equals(d.methodInfo().name) && "0".equals(d.statementId()) && d.iteration() > 1) {
            assertNotNull(d.haveError(Message.CONDITION_EVALUATES_TO_CONSTANT)); // TODO change message
        }
    };

    @Test
    public void test() throws IOException {
        testClass("ExampleManualIterator1", 1, 0, new DebugConfiguration.Builder()
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

}
