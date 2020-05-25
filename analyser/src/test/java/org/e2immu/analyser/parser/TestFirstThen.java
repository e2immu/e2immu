
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

import org.e2immu.analyser.analyser.NumberedStatement;
import org.e2immu.analyser.analyser.TransferValue;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

public class TestFirstThen extends CommonTestRunner {

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = new StatementAnalyserVariableVisitor() {
        @Override
        public void visit(int iteration, MethodInfo methodInfo, String statementId, String variableName, Variable variable, Value currentValue, Map<VariableProperty, Integer> properties) {

            if ("getFirst".equals(methodInfo.name) && "FirstThen.this.first".equals(variableName)) {
                Assert.assertEquals(Level.TRUE, (int) properties.get(VariableProperty.READ));
            }
            if ("equals".equals(methodInfo.name) && "o".equals(variableName)) {
                Assert.assertNull("At iteration " + iteration + " statement " + statementId,
                        properties.get(VariableProperty.NOT_NULL));
            }
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = new MethodAnalyserVisitor() {
        @Override
        public void visit(int iteration, MethodInfo methodInfo) {
            MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
            if ("getFirst".equals(methodInfo.name)) {
                TransferValue tv = methodAnalysis.fieldSummaries.stream().findAny().orElseThrow().getValue();
                Assert.assertEquals(Level.TRUE, tv.properties.get(VariableProperty.READ));
            }
            if ("hashCode".equals(methodInfo.name)) {
                Assert.assertEquals(2, methodAnalysis.fieldSummaries.size());
                TransferValue tv = methodAnalysis.fieldSummaries.stream().findAny().orElseThrow().getValue();
                Assert.assertEquals(Level.TRUE, tv.properties.get(VariableProperty.READ));
                Assert.assertEquals(Level.DELAY, tv.properties.get(VariableProperty.METHOD_CALLED));
            }
        }
    };

    TypeContextVisitor typeContextVisitor = new TypeContextVisitor() {
        @Override
        public void visit(TypeContext typeContext) {
            TypeInfo objects = typeContext.getFullyQualified(Objects.class);
            MethodInfo hash = objects.typeInspection.get().methods.stream().filter(m -> m.name.equals("hash")).findFirst().orElseThrow();
            ParameterInfo objectsParam = hash.methodInspection.get().parameters.get(0);
            Assert.assertEquals(Level.TRUE, objectsParam.parameterAnalysis.get().getProperty(VariableProperty.NOT_MODIFIED));
        }
    };

    @Test
    public void test() throws IOException {
        tesUtilClass("FirstThen", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addTypeContextVisitor(typeContextVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

}
