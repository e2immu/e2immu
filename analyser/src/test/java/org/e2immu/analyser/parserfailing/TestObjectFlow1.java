
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

import org.e2immu.analyser.analyser.AnalysisProvider;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.objectflow.Origin;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.FieldAnalyserVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.TypeAnalyserVisitor;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class TestObjectFlow1 extends CommonTestRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestObjectFlow1.class);

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("KeyValue".equals(d.methodInfo().name) && "0".equals(d.statementId())) {
            if ("key".equals(d.variableName())) {
                assertSame(Origin.PARAMETER, d.variableInfo().getObjectFlow().origin);
            }
            if ("KeyValue.this.key".equals(d.variableName())) {
                assertSame(Origin.PARAMETER, d.variableInfo().getObjectFlow().origin);
            }
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        if ("useKv".equals(d.methodInfo().name)) {
            ParameterAnalysis p0 = d.methodInfo().methodInspection.get().getParameters().get(0).parameterAnalysis.get();
            ObjectFlow objectFlowP0 = p0.getObjectFlow();
            assertNotNull(objectFlowP0);
            assertSame(Origin.PARAMETER, objectFlowP0.origin);
            assertEquals(1L, objectFlowP0.getNonModifyingCallouts().count());
            ObjectFlow callOutP0 = objectFlowP0.getNonModifyingCallouts().findAny().orElseThrow();
            assertSame(Origin.PARAMETER, callOutP0.origin);
            assertEquals("value", callOutP0.location.info.name());
            assertTrue(callOutP0.containsPrevious(objectFlowP0));

            Set<ObjectFlow> internalFlows = d.methodAnalysis().getInternalObjectFlows();
            assertNotNull(internalFlows);
            LOGGER.info("Have internal flows of useKv: {}", internalFlows);
            assertEquals(2, internalFlows.size());
            ObjectFlow newKeyValue = internalFlows.stream()
                    .filter(of -> of.origin == Origin.NEW_OBJECT_CREATION)
                    .findAny().orElseThrow();
            assertEquals("KeyValue", newKeyValue.type.typeInfo.simpleName);
            ObjectFlow valueFieldOfNewKeyValue = internalFlows.stream()
                    .filter(of -> of.origin == Origin.FIELD_ACCESS)
                    .findAny().orElseThrow();
            assertEquals("value", valueFieldOfNewKeyValue.location.info.name());

            ObjectFlow returnFlow = d.methodAnalysis().getObjectFlow();
            assertNotNull(returnFlow);
            assertSame(d.evaluationContext().getPrimitives().integerTypeInfo, returnFlow.type.typeInfo);
            assertSame(valueFieldOfNewKeyValue, returnFlow);
        }
    };

    FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
        int iteration = d.iteration();
        if ("key".equals(d.fieldInfo().name)) {
            ObjectFlow objectFlow = d.fieldAnalysis().getObjectFlow();
            assertNotNull(objectFlow);
            LOGGER.info("Object flow is {}", objectFlow.detailed());

            // after the first iteration, the object flow becomes that of the parameter
            // in the first iteration, the field value is NO_VALUE
            if (iteration == 0) {
                assertTrue(objectFlow.location.info instanceof FieldInfo);
            } else {
                assertTrue(objectFlow.location.info instanceof ParameterInfo);
            }
            ParameterInfo key = d.fieldInfo().owner.typeInspection.get().constructors().get(0).methodInspection.get().getParameters().get(0);
            ObjectFlow objectFlowPI = key.parameterAnalysis.get().getObjectFlow();
            if (iteration > 0) {
                assertSame(objectFlow, objectFlowPI);
                assertEquals(1L, objectFlow.getLocalAssignments().count());
            } else {
                assertNotSame(objectFlow, objectFlowPI);
            }
        }
    };

    TypeAnalyserVisitor typeAnalyserVisitor = d -> {
        if ("ObjectFlow1".equals(d.typeInfo().simpleName)) {
            assertEquals(1, d.typeAnalysis().getConstantObjectFlows().size());
            ObjectFlow literal = d.typeAnalysis().getConstantObjectFlows().stream().findAny().orElseThrow();
            assertSame(d.primitives().stringTypeInfo, literal.type.typeInfo);
            assertSame(Origin.LITERAL, literal.origin);
            assertEquals(1L, literal.getNonModifyingCallouts().count());
        }
    };

    @Test
    public void test() throws IOException {
        TypeContext typeContext = testClass("ObjectFlow1", 0, 0, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .build());

        TypeInfo keyValue = typeContext.typeMapBuilder.get("org.e2immu.analyser.testexample.ObjectFlow1.KeyValue");
        MethodInfo keyValueConstructor = keyValue.typeInspection.get().constructors().get(0);
        ParameterInfo key = keyValueConstructor.methodInspection.get().getParameters().get(0);
        ObjectFlow objectFlowKey = key.parameterAnalysis.get().getObjectFlow();
        assertNotNull(objectFlowKey);
        assertSame(Origin.PARAMETER, objectFlowKey.getOrigin());

        assertEquals(1L, objectFlowKey.getPrevious().count());
        ObjectFlow keyConstant = objectFlowKey.getPrevious().findAny().orElseThrow();

        TypeInfo objectFlow1 = typeContext.typeMapBuilder.get("org.e2immu.analyser.testexample.ObjectFlow1");
        ObjectFlow inType = objectFlow1.typeAnalysis.get().getConstantObjectFlows().stream().findFirst().orElseThrow();
        assertSame(inType, keyConstant);
        assertSame(Origin.LITERAL, inType.origin);

        ParameterInfo value = keyValueConstructor.methodInspection.get().getParameters().get(1);
        ObjectFlow objectFlowValue = value.parameterAnalysis.get().getObjectFlow();
        assertSame(Origin.PARAMETER, objectFlowValue.getOrigin());

        MethodInfo useKv = objectFlow1.typeInspection.get().methods().stream().filter(m -> m.name.equals("useKv")).findAny().orElseThrow();
        ParameterInfo k = useKv.methodInspection.get().getParameters().get(0);
        ObjectFlow objectFlowK = k.parameterAnalysis.get().getObjectFlow();
        assertSame(Origin.PARAMETER, objectFlowK.origin);

        assertEquals(2L, useKv.methodAnalysis.get().getInternalObjectFlows().size());
        ObjectFlow newKeyValue = useKv.methodAnalysis.get().getInternalObjectFlows().stream()
                .filter(of -> Origin.NEW_OBJECT_CREATION == of.origin).findAny().orElseThrow();
        ObjectFlow accessValue = useKv.methodAnalysis.get().getInternalObjectFlows().stream()
                .filter(of -> Origin.FIELD_ACCESS == of.origin).findAny().orElseThrow();


        MethodInfo getKeyMethod = keyValue.typeInspection.get().methods().stream().filter(m -> "getKey".equals(m.name)).findAny().orElseThrow();
        ObjectFlow returnFlowGetKey = getKeyMethod.methodAnalysis.get().getObjectFlow();
        assertSame(Origin.FIELD_ACCESS, returnFlowGetKey.origin);

        Set<ObjectFlow> flowsOfObjectFlow1 = objectFlow1.objectFlows(AnalysisProvider.DEFAULT_PROVIDER);
        for (ObjectFlow objectFlow : flowsOfObjectFlow1) {
            LOGGER.info("Detailed: {}", objectFlow.detailed());
        }
        assertTrue(flowsOfObjectFlow1.contains(objectFlowK));
        assertTrue(flowsOfObjectFlow1.contains(objectFlowKey));
        assertTrue(flowsOfObjectFlow1.contains(objectFlowValue));
        assertTrue(flowsOfObjectFlow1.contains(keyConstant));
        assertTrue(flowsOfObjectFlow1.contains(newKeyValue));
        assertTrue(flowsOfObjectFlow1.contains(returnFlowGetKey));
        assertTrue(flowsOfObjectFlow1.contains(accessValue));
        assertEquals(7, flowsOfObjectFlow1.size());
    }

}
