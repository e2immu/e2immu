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

package org.e2immu.analyser.parserfailing;

import org.e2immu.analyser.analyser.MethodLevelData;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.visitor.*;
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
        if ("ExampleManualIterator1".equals(typeInfo.simpleName)) {
            assertEquals("E", d.typeAnalysis().getTransparentTypes().toString());
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
            assertNotNull(d.haveError(Message.Label.CONDITION_EVALUATES_TO_CONSTANT)); // TODO change message
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
