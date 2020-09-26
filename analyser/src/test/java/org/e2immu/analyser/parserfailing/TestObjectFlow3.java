
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

package org.e2immu.analyser.parserfailing;

import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.e2immu.analyser.config.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.model.MethodAnalysis;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.abstractvalue.VariableValue;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.objectflow.Origin;
import org.e2immu.analyser.objectflow.access.MethodAccess;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.TypeContext;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Set;

/*

https://github.com/bnaudts/e2immu/issues/9

 */
public class TestObjectFlow3 extends CommonTestRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestObjectFlow3.class);

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("main".equals(d.methodInfo.name) && "0".equals(d.statementId) && "config".equals(d.variableName)) {
            Assert.assertTrue(d.currentValue instanceof VariableValue);
            ObjectFlow objectFlow = d.currentValue.getObjectFlow();
            Assert.assertSame(Origin.NEW_OBJECT_CREATION, objectFlow.getOrigin());
        }
        if ("go".equals(d.methodInfo.name) && "Main".equals(d.methodInfo.typeInfo.simpleName) && "1".equals(d.statementId) && "inBetween".equals(d.variableName)) {
            ObjectFlow objectFlow = d.currentValue.getObjectFlow();
            if (d.iteration >= 100) {
                Assert.assertEquals(1L, objectFlow.getNonModifyingAccesses().count());
                MethodAccess methodAccess = (MethodAccess) objectFlow.getNonModifyingAccesses().findAny().orElseThrow();
                Assert.assertEquals("go", methodAccess.methodInfo.name);
                Assert.assertEquals("InBetween", methodAccess.methodInfo.typeInfo.simpleName);
            }
            Assert.assertNull(objectFlow.getModifyingAccess());
            Assert.assertSame(Origin.NEW_OBJECT_CREATION, objectFlow.origin);
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = (iteration, methodInfo) -> {
        if ("go".equals(methodInfo.name) && "Main".equals(methodInfo.typeInfo.simpleName)) {
            if (iteration < 100) {
                Assert.assertFalse(methodInfo.methodAnalysis.get().internalObjectFlows.isSet());
            } else {
                Set<ObjectFlow> objectFlows = methodInfo.methodAnalysis.get().internalObjectFlows.get();
                LOGGER.info("Have flows in Main.go(): {}", objectFlows);
                Assert.assertEquals(2, objectFlows.size());
                ObjectFlow newInBetween = objectFlows.stream().filter(of -> of.origin == Origin.NEW_OBJECT_CREATION).findFirst().orElseThrow();
                Assert.assertEquals("InBetween", newInBetween.type.typeInfo.simpleName);
                ObjectFlow fieldAccess = objectFlows.stream().filter(of -> of.origin == Origin.FIELD_ACCESS).findFirst().orElseThrow();
                Assert.assertEquals("config", fieldAccess.location.info.name());
            }
        }
    };

    @Test
    public void test() throws IOException {
        TypeContext typeContext = testClass("ObjectFlow3", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());

        // there are 2 flows in the 'main' method
        TypeInfo objectFlow3 = typeContext.typeStore.get("org.e2immu.analyser.testexample.ObjectFlow3");
        MethodInfo mainMethod = objectFlow3.typeInspection.getPotentiallyRun().methods.stream().filter(m -> "main".equals(m.name)).findAny().orElseThrow();
        mainMethod.methodAnalysis.get().internalObjectFlows.get().forEach(of ->
                LOGGER.info("internal object flows of static 'main': {}", of.detailed()));
        Assert.assertEquals(4L, mainMethod.methodAnalysis.get().internalObjectFlows.get().size());

        // the Config flow is used to initiate the Main flow
        TypeInfo config = typeContext.typeStore.get("org.e2immu.analyser.testexample.ObjectFlow3.Config");
        ObjectFlow newConfig = mainMethod.methodAnalysis.get().internalObjectFlows.get().stream().filter(of -> of.type.typeInfo == config).findAny().orElseThrow();
        Assert.assertEquals(1L, newConfig.getNonModifyingCallouts().count());

        TypeInfo main = typeContext.typeStore.get("org.e2immu.analyser.testexample.ObjectFlow3.Main");
        ObjectFlow newMain = mainMethod.methodAnalysis.get().internalObjectFlows.get().stream().filter(of -> of.type.typeInfo == main).findAny().orElseThrow();
        // test object access "go" method
        Assert.assertEquals(1L, newMain.getNonModifyingAccesses().count());
        MethodAccess newMainCallGo = (MethodAccess) newMain.getNonModifyingAccesses().findFirst().orElseThrow();
        Assert.assertEquals("go", newMainCallGo.methodInfo.name);

        // the Config flow in Main is linked to the creation in the main method
        MethodInfo mainConstructor = main.typeInspection.getPotentiallyRun().constructors.get(0);
        ObjectFlow mainConstructorParamObjectFlow = mainConstructor.methodInspection.get().parameters.get(0).parameterAnalysis.get().getObjectFlow();
        Assert.assertSame(Origin.PARAMETER, mainConstructorParamObjectFlow.origin);
        Assert.assertTrue(mainConstructorParamObjectFlow.containsPrevious(newConfig));

        // The go() method in main creates an InBetween flow
        MethodInfo goMethodMain = main.typeInspection.getPotentiallyRun().methods.stream().filter(m -> "go".equals(m.name)).findAny().orElseThrow();
        MethodAnalysis goMethodAnalysis =  goMethodMain.methodAnalysis.get();
        Set<ObjectFlow> goMethodObjectFlows = goMethodAnalysis.internalObjectFlows.get();
        goMethodObjectFlows.forEach(of -> LOGGER.info("internal object flows in Main.go(): {}", of.detailed()));
        TypeInfo inBetween = typeContext.typeStore.get("org.e2immu.analyser.testexample.ObjectFlow3.InBetween");
        Assert.assertEquals(2L, goMethodMain.methodAnalysis.get().internalObjectFlows.get().size());
        ObjectFlow newInBetween = goMethodMain.methodAnalysis.get().internalObjectFlows.get().stream().filter(of -> of.type.typeInfo == inBetween).findAny().orElseThrow();
        Assert.assertSame(Origin.NEW_OBJECT_CREATION, newInBetween.origin);

        // test object access "go" method
        Assert.assertEquals(1L, newInBetween.getNonModifyingAccesses().count());
        MethodAccess newInBetweenCallGo = (MethodAccess) newInBetween.getNonModifyingAccesses().findFirst().orElseThrow();
        Assert.assertEquals("go", newInBetweenCallGo.methodInfo.name);

        Assert.assertEquals(0L, mainConstructorParamObjectFlow.getNonModifyingCallouts().count());
        Assert.assertEquals(1L, mainConstructorParamObjectFlow.getLocalAssignments().count());

        MethodInfo inBetweenConstructor = inBetween.typeInspection.getPotentiallyRun().constructors.get(0);
        ObjectFlow inBetweenConstructorParamObjectFlow = inBetweenConstructor.methodInspection.get().parameters.get(0).parameterAnalysis.get().getObjectFlow();
        Assert.assertEquals(0L, inBetweenConstructorParamObjectFlow.getNonModifyingCallouts().count());
        Assert.assertEquals(1L, inBetweenConstructorParamObjectFlow.getLocalAssignments().count());

        // The go() method in inBetween creates a DoSomeWork flow
        MethodInfo goMethodInBetween = inBetween.typeInspection.getPotentiallyRun().methods.stream().filter(m -> "go".equals(m.name)).findAny().orElseThrow();
        goMethodInBetween.methodAnalysis.get().internalObjectFlows.get().forEach(of -> LOGGER.info(of.detailed()));
        TypeInfo doSomeWork = typeContext.typeStore.get("org.e2immu.analyser.testexample.ObjectFlow3.DoSomeWork");
        ObjectFlow newDoSomeWork = goMethodInBetween.methodAnalysis.get().internalObjectFlows.get().stream().filter(of -> of.type.typeInfo == doSomeWork).findAny().orElseThrow();

        // test object access "go" method
        Assert.assertEquals(1L, newDoSomeWork.getNonModifyingAccesses().count());
        MethodAccess newDoSomeWorkCallGo = (MethodAccess) newDoSomeWork.getNonModifyingAccesses().findFirst().orElseThrow();
        Assert.assertEquals("go", newDoSomeWorkCallGo.methodInfo.name);

        MethodInfo doSomeWorkConstructor = doSomeWork.typeInspection.getPotentiallyRun().constructors.get(0);
        ObjectFlow doSomeWorkConstructorParamObjectFlow = doSomeWorkConstructor.methodInspection.get().parameters.get(0).parameterAnalysis.get().getObjectFlow();
        Assert.assertEquals(0L, doSomeWorkConstructorParamObjectFlow.getNonModifyingCallouts().count());
        Assert.assertEquals(1L, doSomeWorkConstructorParamObjectFlow.getLocalAssignments().count());

        // now we've followed the "Config" object all along
    }

}
