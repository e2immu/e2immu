
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
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.e2immu.analyser.config.StatementAnalyserVisitor;
import org.e2immu.analyser.model.MethodAnalysis;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.Variable;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;

public class TestDependencyGraph extends CommonTestRunner {

    StatementAnalyserVisitor statementAnalyserVisitor = new StatementAnalyserVisitor() {
        @Override
        public void visit(int iteration, MethodInfo methodInfo, NumberedStatement numberedStatement, Value conditional) {
            if ("sorted".equals(methodInfo.name) && "3.0.0".equals(numberedStatement.streamIndices())) {
                Assert.assertEquals("((-1) + toDo.size(),?>=0) >= 0", conditional.toString());
                Map<Variable, Value> isr = conditional.individualSizeRestrictions(false);
                Assert.assertEquals(1, isr.size());
                Map.Entry<Variable, Value> entry = isr.entrySet().stream().findAny().orElseThrow();
                Assert.assertEquals("toDo", entry.getKey().name());
                Assert.assertEquals("((-1) + toDo.size(),?>=0) >= 0", entry.getValue().toString());
            }
            // we have to make sure that there is no "Empty loop" error raised
            if ("sorted".equals(methodInfo.name) && "3.0.1".equals(numberedStatement.streamIndices())) {
                Assert.assertFalse(numberedStatement.errorValue.isSet());
            }
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = new MethodAnalyserVisitor() {
        @Override
        public void visit(int iteration, MethodInfo methodInfo) {
            MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
            if ("sorted".equals(methodInfo.name)) {
                int size = methodAnalysis.getProperty(VariableProperty.SIZE);
                //Assert.assertEquals(0, size);
            }
        }
    };

    @Test
    public void test() throws IOException {
        testUtilClass("DependencyGraph", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

}
