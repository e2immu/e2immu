
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
import org.e2immu.analyser.config.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.config.StatementAnalyserVisitor;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.Variable;
import org.e2immu.analyser.model.abstractvalue.VariableValue;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

public class TestTrie extends CommonTestRunner {

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = new StatementAnalyserVariableVisitor() {
        @Override
        public void visit(int iteration, MethodInfo methodInfo, String statementId, String variableName, Variable variable, Value currentValue, Map<VariableProperty, Integer> properties) {
            if ("add".equals(methodInfo.name) && "newTrieNode".equals(variableName)) {
                if (Set.of("1.0.1.0.2", "1.0.1.0.1").contains(statementId)) {
                    Assert.assertTrue(currentValue instanceof VariableValue);
                    Assert.assertEquals(Level.TRUE, (int) properties.get(VariableProperty.NOT_NULL));
                }
            }
            if ("goTo".equals(methodInfo.name) && "1.0.1".equals(statementId) && "node".equals(variableName)) {
                Assert.assertNull(properties.get(VariableProperty.NOT_NULL));
                Assert.assertEquals(Level.FALSE, currentValue.getPropertyOutsideContext(VariableProperty.NOT_NULL));
            }

            if("get".equals(methodInfo.name) && "0".equals(statementId) && "node".equals(variableName)) {
                Assert.assertNull(properties.get(VariableProperty.MODIFIED));
            }
        }
    };

    StatementAnalyserVisitor statementAnalyserVisitor = new StatementAnalyserVisitor() {
        @Override
        public void visit(int iteration, MethodInfo methodInfo, NumberedStatement numberedStatement, Value conditional) {
            if ("goTo".equals(methodInfo.name) && "1.0.0".equals(numberedStatement.streamIndices())) {
                Assert.assertEquals("(not (null == node.map) and (not (i) + upToPosition) > 0)", conditional.toString());
            }
        }
    };

    // warnings:
    // for now we accept that "action.apply( )", as a Function, may return null, since we haven't implemented inference of @NotNull1 yet

    @Test
    public void test() throws IOException {
        testUtilClass("Trie", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

}
