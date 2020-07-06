
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
import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class TestObjectFlow1 extends CommonTestRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestObjectFlow1.class);

    FieldAnalyserVisitor fieldAnalyserVisitor = new FieldAnalyserVisitor() {
        @Override
        public void visit(int iteration, FieldInfo fieldInfo) {
            if ("key".equals(fieldInfo.name)) {
                ObjectFlow objectFlow = fieldInfo.fieldAnalysis.get().getObjectFlow();
                Assert.assertNotNull(objectFlow);
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
        LOGGER.info("Detail: " + inType.detailed());

    }

}
