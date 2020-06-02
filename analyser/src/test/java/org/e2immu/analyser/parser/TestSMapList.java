
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
import java.util.Set;

public class TestSMapList extends CommonTestRunner {

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = new StatementAnalyserVariableVisitor() {
        @Override
        public void visit(int iteration, MethodInfo methodInfo, String statementId, String variableName, Variable variable, Value currentValue, Map<VariableProperty, Integer> properties) {
            if ("list".equals(methodInfo.name) && "list".equals(variableName) && "3".equals(statementId)) {
                Assert.assertEquals("map.get(a)", currentValue.toString());
                Assert.assertEquals(0, currentValue.getPropertyOutsideContext(VariableProperty.NOT_NULL));
            }
        }
    };

    StatementAnalyserVisitor statementAnalyserVisitor = new StatementAnalyserVisitor() {
        @Override
        public void visit(int iteration, MethodInfo methodInfo, NumberedStatement numberedStatement, Value conditional) {
            if ("3".equals(numberedStatement.streamIndices()) && "list".equals(methodInfo.name)) {
                Assert.assertEquals("(not (null == map.get(a)) and not (null == a))", conditional.toString());
            }
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = new MethodAnalyserVisitor() {
        @Override
        public void visit(int iteration, MethodInfo methodInfo) {
            if ("list".equals(methodInfo.name)) {
                TransferValue returnValue1 = methodInfo.methodAnalysis.get().returnStatementSummaries.get("2.0.0");
                Assert.assertEquals(3, returnValue1.properties.get(VariableProperty.NOT_NULL));

                // this is the one that needs to combine with the null conditional
                TransferValue returnValue2 = methodInfo.methodAnalysis.get().returnStatementSummaries.get("3");
                Assert.assertEquals(1, returnValue2.properties.get(VariableProperty.NOT_NULL));
            }
        }
    };

    TypeContextVisitor typeContextVisitor = new TypeContextVisitor() {
        @Override
        public void visit(TypeContext typeContext) {
            TypeInfo map = typeContext.getFullyQualified(Map.class);
            MethodInfo entrySet = map.typeInspection.get().methods.stream().filter(m -> m.name.equals("entrySet")).findFirst().orElseThrow();
            Assert.assertEquals(Level.TRUE_LEVEL_1, entrySet.methodAnalysis.get().getProperty(VariableProperty.SIZE_COPY));
            Assert.assertEquals(0, entrySet.methodAnalysis.get().getProperty(VariableProperty.SIZE));
        }
    };

    @Test
    public void test() throws IOException {
        testUtilClass("SMapList", 0, 0, new DebugConfiguration.Builder()
                .addTypeContextVisitor(typeContextVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

}
