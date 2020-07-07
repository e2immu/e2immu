
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
import org.e2immu.analyser.config.FieldAnalyserVisitor;

import org.e2immu.analyser.config.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Set;

public class TestObjectFlow1 extends CommonTestRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestObjectFlow1.class);

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("KeyValue".equals(d.methodInfo.name) && "0".equals(d.statementId)) {
            if ("key".equals(d.variableName)) {
                Assert.assertTrue(d.objectFlow.origin instanceof ObjectFlow.MethodCalls);
            }
            if ("KeyValue.this.key".equals(d.variableName)) {
                Assert.assertTrue(d.objectFlow.origin instanceof ObjectFlow.MethodCalls);
            }
        }
    };

    FieldAnalyserVisitor fieldAnalyserVisitor = new FieldAnalyserVisitor() {
        @Override
        public void visit(int iteration, FieldInfo fieldInfo) {
            if ("key".equals(fieldInfo.name)) {
                ObjectFlow objectFlow = fieldInfo.fieldAnalysis.get().getObjectFlow();
                Assert.assertNotNull(objectFlow);
                LOGGER.info("Object flow is {}", objectFlow.detailed());

                // after the first iteration, the object flow becomes that of the parameter
                // in the first iteration, the field value is NO_VALUE
                Assert.assertEquals(iteration == 0 ? 0 : 1, objectFlow.importance());

                ParameterInfo key = fieldInfo.owner.typeInspection.get().constructors.get(0).methodInspection.get().parameters.get(0);
                ObjectFlow objectFlowPI = key.parameterAnalysis.get().objectFlow;
                if (iteration > 0) {
                    Assert.assertSame(objectFlow, objectFlowPI);
                    Assert.assertEquals(1L, objectFlow.getLocalAssignments().count());
                } else {
                    Assert.assertNotSame(objectFlow, objectFlowPI);
                }
            }
        }
    };

    @Test
    public void test() throws IOException {
        TypeContext typeContext = testClass("ObjectFlow1", 0, 0, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());

        TypeInfo keyValue = typeContext.typeStore.get("org.e2immu.analyser.testexample.ObjectFlow1.KeyValue");
        MethodInfo keyValueConstructor = keyValue.typeInspection.get().constructors.get(0);
        ParameterInfo key = keyValueConstructor.methodInspection.get().parameters.get(0);
        ObjectFlow objectFlowKey = key.parameterAnalysis.get().objectFlow;
        Assert.assertTrue("Have " + objectFlowKey.getOrigin(), objectFlowKey.getOrigin() instanceof ObjectFlow.MethodCalls);
        ObjectFlow.MethodCalls methodCalls = (ObjectFlow.MethodCalls) objectFlowKey.getOrigin();

        Assert.assertEquals(1, methodCalls.objectFlows.size());
        ObjectFlow keyConstant = methodCalls.objectFlows.stream().findAny().orElseThrow();

        TypeInfo objectFlow1 = typeContext.typeStore.get("org.e2immu.analyser.testexample.ObjectFlow1");
        ObjectFlow inType = objectFlow1.typeAnalysis.get().getConstantObjectFlows().findFirst().orElseThrow();
        Assert.assertSame(inType, keyConstant);

        ParameterInfo value = keyValueConstructor.methodInspection.get().parameters.get(1);
        ObjectFlow objectFlowValue = value.parameterAnalysis.get().objectFlow;
        Assert.assertTrue("Have " + objectFlowKey.getOrigin(), objectFlowValue.getOrigin() instanceof ObjectFlow.MethodCalls);

        MethodInfo useKv = objectFlow1.typeInspection.get().methods.stream().filter(m -> m.name.equals("useKv")).findAny().orElseThrow();
        ParameterInfo k = useKv.methodInspection.get().parameters.get(0);
        ObjectFlow objectFlowK = k.parameterAnalysis.get().objectFlow;

        Assert.assertSame(objectFlowValue, useKv.methodAnalysis.get().getReturnedObjectFlow());

        Set<ObjectFlow> flowsOfObjectFlow1 = objectFlow1.objectFlows();
        for (ObjectFlow objectFlow : flowsOfObjectFlow1) {
            LOGGER.info("Detailed: {}", objectFlow.detailed());
        }
        Assert.assertEquals(5, flowsOfObjectFlow1.size());
        Assert.assertTrue(flowsOfObjectFlow1.contains(objectFlowK));
        Assert.assertTrue(flowsOfObjectFlow1.contains(objectFlowKey));
        Assert.assertTrue(flowsOfObjectFlow1.contains(objectFlowValue));
        Assert.assertTrue(flowsOfObjectFlow1.contains(keyConstant));
    }

}
