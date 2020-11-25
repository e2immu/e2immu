
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

import org.e2immu.analyser.analyser.AnalysisProvider;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.objectflow.Origin;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Set;

public class TestObjectFlow1 extends CommonTestRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestObjectFlow1.class);

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("KeyValue".equals(d.methodInfo().name) && "0".equals(d.statementId())) {
            if ("key".equals(d.variableName())) {
                Assert.assertSame(Origin.PARAMETER, d.variableInfo().getObjectFlow().origin);
            }
            if ("KeyValue.this.key".equals(d.variableName())) {
                Assert.assertSame(Origin.PARAMETER, d.variableInfo().getObjectFlow().origin);
            }
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        if ("useKv".equals(d.methodInfo().name)) {
            ParameterAnalysis p0 = d.methodInfo().methodInspection.get().getParameters().get(0).parameterAnalysis.get();
            ObjectFlow objectFlowP0 = p0.getObjectFlow();
            Assert.assertNotNull(objectFlowP0);
            Assert.assertSame(Origin.PARAMETER, objectFlowP0.origin);
            Assert.assertEquals(1L, objectFlowP0.getNonModifyingCallouts().count());
            ObjectFlow callOutP0 = objectFlowP0.getNonModifyingCallouts().findAny().orElseThrow();
            Assert.assertSame(Origin.PARAMETER, callOutP0.origin);
            Assert.assertEquals("value", callOutP0.location.info.name());
            Assert.assertTrue(callOutP0.containsPrevious(objectFlowP0));

            Set<ObjectFlow> internalFlows = d.methodAnalysis().getInternalObjectFlows();
            Assert.assertNotNull(internalFlows);
            LOGGER.info("Have internal flows of useKv: {}", internalFlows);
            Assert.assertEquals(2, internalFlows.size());
            ObjectFlow newKeyValue = internalFlows.stream()
                    .filter(of -> of.origin == Origin.NEW_OBJECT_CREATION)
                    .findAny().orElseThrow();
            Assert.assertEquals("KeyValue", newKeyValue.type.typeInfo.simpleName);
            ObjectFlow valueFieldOfNewKeyValue = internalFlows.stream()
                    .filter(of -> of.origin == Origin.FIELD_ACCESS)
                    .findAny().orElseThrow();
            Assert.assertEquals("value", valueFieldOfNewKeyValue.location.info.name());

            ObjectFlow returnFlow = d.methodAnalysis().getObjectFlow();
            Assert.assertNotNull(returnFlow);
            Assert.assertSame(d.evaluationContext().getPrimitives().integerTypeInfo, returnFlow.type.typeInfo);
            Assert.assertSame(valueFieldOfNewKeyValue, returnFlow);
        }
    };

    FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
        int iteration = d.iteration();
        if ("key".equals(d.fieldInfo().name)) {
            ObjectFlow objectFlow = d.fieldAnalysis().getObjectFlow();
            Assert.assertNotNull(objectFlow);
            LOGGER.info("Object flow is {}", objectFlow.detailed());

            // after the first iteration, the object flow becomes that of the parameter
            // in the first iteration, the field value is NO_VALUE
            if (iteration == 0) {
                Assert.assertTrue(objectFlow.location.info instanceof FieldInfo);
            } else {
                Assert.assertTrue(objectFlow.location.info instanceof ParameterInfo);
            }
            ParameterInfo key = d.fieldInfo().owner.typeInspection.get().constructors().get(0).methodInspection.get().getParameters().get(0);
            ObjectFlow objectFlowPI = key.parameterAnalysis.get().getObjectFlow();
            if (iteration > 0) {
                Assert.assertSame(objectFlow, objectFlowPI);
                Assert.assertEquals(1L, objectFlow.getLocalAssignments().count());
            } else {
                Assert.assertNotSame(objectFlow, objectFlowPI);
            }
        }
    };

    TypeAnalyserVisitor typeAnalyserVisitor = d -> {
        if ("ObjectFlow1".equals(d.typeInfo().simpleName)) {
            Assert.assertEquals(1, d.typeAnalysis().getConstantObjectFlows().size());
            ObjectFlow literal = d.typeAnalysis().getConstantObjectFlows().stream().findAny().orElseThrow();
            Assert.assertSame(d.primitives().stringTypeInfo, literal.type.typeInfo);
            Assert.assertSame(Origin.LITERAL, literal.origin);
            Assert.assertEquals(1L, literal.getNonModifyingCallouts().count());
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
        Assert.assertNotNull(objectFlowKey);
        Assert.assertSame(Origin.PARAMETER, objectFlowKey.getOrigin());

        Assert.assertEquals(1L, objectFlowKey.getPrevious().count());
        ObjectFlow keyConstant = objectFlowKey.getPrevious().findAny().orElseThrow();

        TypeInfo objectFlow1 = typeContext.typeMapBuilder.get("org.e2immu.analyser.testexample.ObjectFlow1");
        ObjectFlow inType = objectFlow1.typeAnalysis.get().getConstantObjectFlows().stream().findFirst().orElseThrow();
        Assert.assertSame(inType, keyConstant);
        Assert.assertSame(Origin.LITERAL, inType.origin);

        ParameterInfo value = keyValueConstructor.methodInspection.get().getParameters().get(1);
        ObjectFlow objectFlowValue = value.parameterAnalysis.get().getObjectFlow();
        Assert.assertSame(Origin.PARAMETER, objectFlowValue.getOrigin());

        MethodInfo useKv = objectFlow1.typeInspection.get().methods().stream().filter(m -> m.name.equals("useKv")).findAny().orElseThrow();
        ParameterInfo k = useKv.methodInspection.get().getParameters().get(0);
        ObjectFlow objectFlowK = k.parameterAnalysis.get().getObjectFlow();
        Assert.assertSame(Origin.PARAMETER, objectFlowK.origin);

        Assert.assertEquals(2L, useKv.methodAnalysis.get().getInternalObjectFlows().size());
        ObjectFlow newKeyValue = useKv.methodAnalysis.get().getInternalObjectFlows().stream()
                .filter(of -> Origin.NEW_OBJECT_CREATION == of.origin).findAny().orElseThrow();
        ObjectFlow accessValue = useKv.methodAnalysis.get().getInternalObjectFlows().stream()
                .filter(of -> Origin.FIELD_ACCESS == of.origin).findAny().orElseThrow();


        MethodInfo getKeyMethod = keyValue.typeInspection.get().methods().stream().filter(m -> "getKey".equals(m.name)).findAny().orElseThrow();
        ObjectFlow returnFlowGetKey = getKeyMethod.methodAnalysis.get().getObjectFlow();
        Assert.assertSame(Origin.FIELD_ACCESS, returnFlowGetKey.origin);

        Set<ObjectFlow> flowsOfObjectFlow1 = objectFlow1.objectFlows(AnalysisProvider.DEFAULT_PROVIDER);
        for (ObjectFlow objectFlow : flowsOfObjectFlow1) {
            LOGGER.info("Detailed: {}", objectFlow.detailed());
        }
        Assert.assertTrue(flowsOfObjectFlow1.contains(objectFlowK));
        Assert.assertTrue(flowsOfObjectFlow1.contains(objectFlowKey));
        Assert.assertTrue(flowsOfObjectFlow1.contains(objectFlowValue));
        Assert.assertTrue(flowsOfObjectFlow1.contains(keyConstant));
        Assert.assertTrue(flowsOfObjectFlow1.contains(newKeyValue));
        Assert.assertTrue(flowsOfObjectFlow1.contains(returnFlowGetKey));
        Assert.assertTrue(flowsOfObjectFlow1.contains(accessValue));
        Assert.assertEquals(7, flowsOfObjectFlow1.size());
    }

}
