/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.NumberedStatement;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.e2immu.analyser.config.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.config.StatementAnalyserVisitor;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.abstractvalue.InlineValue;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.e2immu.analyser.model.abstractvalue.VariableValue;
import org.e2immu.analyser.model.value.StringValue;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class TestNotNullWithPatterns extends CommonTestRunner {
    public TestNotNullWithPatterns() {
        super(true);
    }

    MethodAnalyserVisitor methodAnalyserVisitor = (iteration, methodInfo) -> {
        if ("conditionalValue".equals(methodInfo.name) && iteration > 2) {
            Value srv = methodInfo.methodAnalysis.get().singleReturnValue.get();
            Assert.assertEquals("inline conditionalValue on condition.test(initial)?alternative:initial", srv.toString());
            Assert.assertTrue(srv instanceof InlineValue);

        }

        if ("method4bis".equals(methodInfo.name) && iteration > 0) {
            NumberedStatement start = methodInfo.methodAnalysis.get().numberedStatements.get().get(0).followReplacements();
            Assert.assertEquals("return a1 == null ? a2 == null ? \"abc\" : a2 : a3 == null ? \"xyz\" : a1;\n",
                    start.statement.statementString(0, null));
            Assert.assertNull(start.next.get().orElse(null));
        }
    };

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if("method7".equals(d.methodInfo.name) && "0".equals(d.statementId) && d.iteration > 1) {
            Assert.assertEquals("null == a1?Was null...:a1", d.numberedStatement.valueOfExpression.get().toString());
        }
    };

    @Test
    public void test() throws IOException {
        testClass("NotNullWithPatterns", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

}
