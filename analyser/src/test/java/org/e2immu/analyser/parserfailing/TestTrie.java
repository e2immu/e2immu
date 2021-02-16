
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
import org.e2immu.analyser.config.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.config.StatementAnalyserVisitor;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/*
https://github.com/bnaudts/e2immu/issues/14
 */

public class TestTrie extends CommonTestRunner {

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("add".equals(d.methodInfo().name) && "newTrieNode".equals(d.variableName())) {
            if (Set.of("1.0.1.0.2", "1.0.1.0.1").contains(d.statementId())) {
                Assert.assertTrue(d.currentValue() instanceof VariableExpression);
                Assert.assertEquals(Level.TRUE, (int) d.properties().get(VariableProperty.NOT_NULL_EXPRESSION));
            }
        }
        if ("goTo".equals(d.methodInfo().name) && "1.0.1".equals(d.statementId()) && "node".equals(d.variableName())) {
            Assert.assertFalse(d.hasProperty(VariableProperty.CONTEXT_NOT_NULL));
            Assert.assertEquals(Level.FALSE, d.getPropertyOfCurrentValue(VariableProperty.NOT_NULL_EXPRESSION));
        }

        if ("get".equals(d.methodInfo().name) && "0".equals(d.statementId()) && "node".equals(d.variableName())) {
            Assert.assertFalse(d.hasProperty(VariableProperty.CONTEXT_MODIFIED));
        }
    };

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if ("goTo".equals(d.methodInfo().name) && "1.0.0".equals(d.statementId())) {
            Assert.assertEquals("(not (null == node.map) and (not (i) + upToPosition) > 0)", d.condition().toString());
        }
    };

    // warnings:
    // for now we accept that "action.apply( )", as a Function, may return null, since we haven't implemented inference of @NotNull1 yet

    @Test
    public void test() throws IOException {
        testUtilClass(List.of("Freezable", "Trie"), 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

}
