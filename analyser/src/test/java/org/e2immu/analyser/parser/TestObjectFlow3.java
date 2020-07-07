
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

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.FieldAnalyserVisitor;
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
        MethodInfo main = objectFlow3.typeInspection.get().methods.stream().filter(m -> "main".equals(m.name)).findAny().orElseThrow();
        main.methodAnalysis.get().getInternalObjectFlows().forEach(of -> LOGGER.info(of.detailed()));
        Assert.assertEquals(2L, main.methodAnalysis.get().getInternalObjectFlows().count());

        // the Config flow is used to initiate the Main flow
        TypeInfo config = typeContext.typeStore.get("org.e2immu.analyser.testexample.ObjectFlow3.Config");
        ObjectFlow newConfig = main.methodAnalysis.get().getInternalObjectFlows().filter(of -> of.type.typeInfo == config).findAny().orElseThrow();
        Assert.assertEquals(1L, newConfig.getNonModifyingCallouts().count());
    }

}
