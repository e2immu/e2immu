
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

    StatementAnalyserVisitor statementAnalyserVisitor = new StatementAnalyserVisitor() {
        @Override
        public void visit(int iteration, MethodInfo methodInfo, NumberedStatement numberedStatement, Value conditional) {
            if ("equals".equals(methodInfo.name) && "2".equals(numberedStatement.streamIndices())) {
                Assert.assertEquals("(not (null == o) and o.getClass() == this.getClass() and not (o == this))", conditional.toString());
            }
        }
    };

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {

        if ("getFirst".equals(d.methodInfo.name) && "FirstThen.this.first".equals(d.variableName)) {
            if("0".equals(d.statementId)) {
                Assert.assertEquals(Level.TRUE, (int) d.properties.get(VariableProperty.READ));
            }
            if("1".equals(d.statementId)) {
                Assert.assertEquals(Level.READ_ASSIGN_MULTIPLE_TIMES, (int) d.properties.get(VariableProperty.READ));
            }
        }
        if ("equals".equals(d.methodInfo.name) && "o".equals(d.variableName)) {
            if ("2".equals(d.statementId)) {
                Assert.assertEquals(Level.FALSE, (int) d.properties.get(VariableProperty.MODIFIED));
            }
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = new MethodAnalyserVisitor() {
        @Override
        public void visit(int iteration, MethodInfo methodInfo) {
            MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
            if ("getFirst".equals(methodInfo.name)) {
                TransferValue tv = methodAnalysis.fieldSummaries.stream().findAny().orElseThrow().getValue();
                Assert.assertEquals(Level.READ_ASSIGN_MULTIPLE_TIMES, tv.properties.get(VariableProperty.READ));
            }
            if ("hashCode".equals(methodInfo.name)) {
                Assert.assertEquals(2, methodAnalysis.fieldSummaries.size());
                TransferValue tv = methodAnalysis.fieldSummaries.stream().findAny().orElseThrow().getValue();
                Assert.assertEquals(Level.TRUE, tv.properties.get(VariableProperty.READ));
                Assert.assertEquals(Level.DELAY, tv.properties.get(VariableProperty.METHOD_CALLED));

                if (iteration > 0) {
                    Assert.assertEquals(Level.FALSE, methodAnalysis.getProperty(VariableProperty.MODIFIED));
                }
            }
            if ("equals".equals(methodInfo.name)) {
                ParameterInfo o = methodInfo.methodInspection.get().parameters.get(0);
                Assert.assertEquals(Level.FALSE, o.parameterAnalysis.get().getProperty(VariableProperty.MODIFIED));
            }
        }
    };

    TypeContextVisitor typeContextVisitor = new TypeContextVisitor() {
        @Override
        public void visit(TypeContext typeContext) {
            TypeInfo objects = typeContext.getFullyQualified(Objects.class);
            MethodInfo hash = objects.typeInspection.get().methods.stream().filter(m -> m.name.equals("hash")).findFirst().orElseThrow();
            ParameterInfo objectsParam = hash.methodInspection.get().parameters.get(0);
            Assert.assertEquals(Level.FALSE, objectsParam.parameterAnalysis.get().getProperty(VariableProperty.MODIFIED));
        }
    };

    @Test
    public void test() throws IOException {
        testUtilClass("FirstThen", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addTypeContextVisitor(typeContextVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

}
