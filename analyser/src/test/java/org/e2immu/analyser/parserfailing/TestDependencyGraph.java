
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

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.e2immu.analyser.config.StatementAnalyserVisitor;
import org.e2immu.analyser.model.MethodAnalysis;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.Variable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.Message;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/*
https://github.com/bnaudts/e2immu/issues/16
 */
public class TestDependencyGraph extends CommonTestRunner {

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if ("sorted".equals(d.methodInfo.name) && "3.0.0".equals(d.statementId)) {
            Assert.assertEquals("((-1) + toDo.size(),?>=0) >= 0", d.condition.toString());
            Map<Variable, Value> isr = d.condition.filter(Value.FilterMode.ACCEPT, Value::isIndividualSizeRestriction).accepted;
            Assert.assertEquals(1, isr.size());
            Map.Entry<Variable, Value> entry = isr.entrySet().stream().findAny().orElseThrow();
            Assert.assertEquals("toDo", entry.getKey().simpleName());
            Assert.assertEquals("((-1) + toDo.size(),?>=0) >= 0", entry.getValue().toString());
        }
        // we have to make sure that there is no "Empty loop" error raised
        if ("sorted".equals(d.methodInfo.name) && "3.0.1".equals(d.statementId)) {
            Assert.assertNull(d.haveError(Message.EMPTY_LOOP));
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        MethodAnalysis methodAnalysis = d.methodAnalysis();
        if ("sorted".equals(d.methodInfo().name)) {
            int size = methodAnalysis.getProperty(VariableProperty.SIZE);
            //Assert.assertEquals(0, size);
        }
    };

    @Test
    public void test() throws IOException {
        testUtilClass(List.of("Freezable", "DependencyGraph"), 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

}
