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

import org.e2immu.analyser.analyser.StatementAnalysis;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.e2immu.analyser.config.StatementAnalyserVisitor;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.expression.InlinedMethod;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class TestNotNullWithPatterns extends CommonTestRunner {
    public TestNotNullWithPatterns() {
        super(true);
    }

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        if ("conditionalValue".equals(d.methodInfo().name) && d.iteration() > 2) {
            Expression srv = d.methodAnalysis().getSingleReturnValue();
            Assert.assertEquals("inline conditionalValue on condition.test(initial)?alternative:initial", srv.toString());
            Assert.assertTrue(srv instanceof InlinedMethod);

        }

        if ("method4bis".equals(d.methodInfo().name) && d.iteration() > 0) {
            StatementAnalysis start = d.methodAnalysis().getFirstStatement().followReplacements();
            Assert.assertEquals("return a1 == null ? a2 == null ? \"abc\" : a2 : a3 == null ? \"xyz\" : a1;\n",
                    start.statement.minimalOutput());
            Assert.assertNull(start.navigationData.next.get().orElse(null));
        }
    };

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if ("method7".equals(d.methodInfo().name) && "0".equals(d.statementId()) && d.iteration() > 1) {
            Assert.assertEquals("null == a1?Was null...:a1", d.statementAnalysis().stateData.getValueOfExpression().toString());
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
