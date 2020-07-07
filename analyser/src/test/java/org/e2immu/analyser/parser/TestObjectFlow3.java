
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
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.VariableValue;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class TestObjectFlow3 extends CommonTestRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestObjectFlow3.class);
    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("main".equals(d.methodInfo.name) && "0".equals(d.statementId) && "config".equals(d.variableName)) {
            Assert.assertTrue(d.currentValue instanceof VariableValue);
            ObjectFlow objectFlow = d.currentValue.getObjectFlow();
            Assert.assertTrue(objectFlow.getOrigin() instanceof ObjectFlow.ObjectCreation);
        }
    };

    @Test
    public void test() throws IOException {
        TypeContext typeContext = testClass("ObjectFlow3", 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());

        // there are 2 flows in the 'main' method
        TypeInfo objectFlow3 = typeContext.typeStore.get("org.e2immu.analyser.testexample.ObjectFlow3");
        MethodInfo mainMethod = objectFlow3.typeInspection.get().methods.stream().filter(m -> "main".equals(m.name)).findAny().orElseThrow();
        mainMethod.methodAnalysis.get().getInternalObjectFlows().forEach(of -> LOGGER.info(of.detailed()));
        Assert.assertEquals(4L, mainMethod.methodAnalysis.get().getInternalObjectFlows().count());

        // the Config flow is used to initiate the Main flow
        TypeInfo config = typeContext.typeStore.get("org.e2immu.analyser.testexample.ObjectFlow3.Config");
        ObjectFlow newConfig = mainMethod.methodAnalysis.get().getInternalObjectFlows().filter(of -> of.type.typeInfo == config).findAny().orElseThrow();
        Assert.assertEquals(1L, newConfig.getNonModifyingCallouts().count());

        TypeInfo main = typeContext.typeStore.get("org.e2immu.analyser.testexample.ObjectFlow3.Main");
        ObjectFlow newMain = mainMethod.methodAnalysis.get().getInternalObjectFlows().filter(of -> of.type.typeInfo == main).findAny().orElseThrow();
        // test object access "go" method
        Assert.assertEquals(1L, newMain.getObjectAccesses().count());
        ObjectFlow.MethodCall newMainCallGo = (ObjectFlow.MethodCall) newMain.getObjectAccesses().findFirst().orElseThrow();
        Assert.assertEquals("go", newMainCallGo.methodInfo.name);

        // the Config flow in Main is linked to the creation in the main method
        MethodInfo mainConstructor = main.typeInspection.get().constructors.get(0);
        ObjectFlow mainConstructorParamObjectFlow = mainConstructor.methodInspection.get().parameters.get(0).parameterAnalysis.get().objectFlow;
        Assert.assertTrue(mainConstructorParamObjectFlow.origin instanceof ObjectFlow.MethodCalls);
        ObjectFlow.MethodCalls mcs = (ObjectFlow.MethodCalls) mainConstructorParamObjectFlow.origin;
        Assert.assertTrue(mcs.objectFlows.contains(newConfig));

        // The go() method in main creates an InBetween flow
        MethodInfo goMethodMain = main.typeInspection.get().methods.stream().filter(m -> "go".equals(m.name)).findAny().orElseThrow();
        goMethodMain.methodAnalysis.get().getInternalObjectFlows().forEach(of -> LOGGER.info(of.detailed()));
        TypeInfo inBetween = typeContext.typeStore.get("org.e2immu.analyser.testexample.ObjectFlow3.InBetween");
        ObjectFlow newInBetween = goMethodMain.methodAnalysis.get().getInternalObjectFlows().filter(of -> of.type.typeInfo == inBetween).findAny().orElseThrow();

        // test object access "go" method
        Assert.assertEquals(1L, newInBetween.getObjectAccesses().count());
        ObjectFlow.MethodCall newInBetweenCallGo = (ObjectFlow.MethodCall) newInBetween.getObjectAccesses().findFirst().orElseThrow();
        Assert.assertEquals("go", newInBetweenCallGo.methodInfo.name);

        Set<ObjectFlow> callOutsOfMainConstructorParamObjectFlow = mainConstructorParamObjectFlow.getNonModifyingCallouts().collect(Collectors.toSet());

        MethodInfo inBetweenConstructor = inBetween.typeInspection.get().constructors.get(0);
        ObjectFlow inBetweenConstructorParamObjectFlow = inBetweenConstructor.methodInspection.get().parameters.get(0).parameterAnalysis.get().objectFlow;
        Assert.assertTrue(callOutsOfMainConstructorParamObjectFlow.contains(inBetweenConstructorParamObjectFlow));
        ObjectFlow.MethodCalls mcs2 = (ObjectFlow.MethodCalls) inBetweenConstructorParamObjectFlow.origin;
        Assert.assertTrue(mcs2.objectFlows.contains(mainConstructorParamObjectFlow));

        // The go() method in inBetween creates a DoSomeWork flow
        MethodInfo goMethodInBetween = inBetween.typeInspection.get().methods.stream().filter(m -> "go".equals(m.name)).findAny().orElseThrow();
        goMethodInBetween.methodAnalysis.get().getInternalObjectFlows().forEach(of -> LOGGER.info(of.detailed()));
        TypeInfo doSomeWork = typeContext.typeStore.get("org.e2immu.analyser.testexample.ObjectFlow3.DoSomeWork");
        ObjectFlow newDoSomeWork = goMethodInBetween.methodAnalysis.get().getInternalObjectFlows().filter(of -> of.type.typeInfo == doSomeWork).findAny().orElseThrow();

        // test object access "go" method
        Assert.assertEquals(1L, newDoSomeWork.getObjectAccesses().count());
        ObjectFlow.MethodCall newDoSomeWorkCallGo = (ObjectFlow.MethodCall) newDoSomeWork.getObjectAccesses().findFirst().orElseThrow();
        Assert.assertEquals("go", newDoSomeWorkCallGo.methodInfo.name);

        Set<ObjectFlow> callOutsOfInBetweenConstructorParamObjectFlow = inBetweenConstructorParamObjectFlow.getNonModifyingCallouts().collect(Collectors.toSet());
        MethodInfo doSomeWorkConstructor = doSomeWork.typeInspection.get().constructors.get(0);
        ObjectFlow doSomeWorkConstructorParamObjectFlow = doSomeWorkConstructor.methodInspection.get().parameters.get(0).parameterAnalysis.get().objectFlow;
        Assert.assertTrue(callOutsOfInBetweenConstructorParamObjectFlow.contains(doSomeWorkConstructorParamObjectFlow));

        // now we've followed the "Config" object all along
    }

}
