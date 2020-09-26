
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

import org.e2immu.analyser.analyser.TransferValue;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.e2immu.analyser.config.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.config.StatementAnalyserVisitor;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.abstractvalue.ConditionalValue;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

public class TestEither extends CommonTestRunner {

    /*  getLeftOrElse:
        A local = left;
        return local != null ? local : Objects.requireNonNull(orElse);
     */

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("getLeftOrElse".equals(d.methodInfo.name) && "orElse".equals(d.variableName) && "1".equals(d.statementId)) {
            Assert.assertEquals("orElse", d.currentValue.toString());
            Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.currentValue.getPropertyOutsideContext(VariableProperty.NOT_NULL));
        }
    };

    private static final String EXPRESSION = "((null == a or not (null == b)) and (not (null == a) or null == b))";

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if ("Either".equals(d.methodInfo.name) && "0.0.0".equals(d.statementId)) {
            if (0 == d.iteration) {
                Assert.assertEquals(EXPRESSION, d.condition.toString());
            } else if (1 == d.iteration) {
                Assert.assertEquals(EXPRESSION, d.state.toString());
                Assert.assertEquals(EXPRESSION, d.condition.toString());
            }
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = (iteration, methodInfo) -> {
        if ("getLeftOrElse".equals(methodInfo.name) && iteration > 0) {
            TransferValue tv = methodInfo.methodAnalysis.get().methodLevelData().returnStatementSummaries.get("1");
            Assert.assertTrue(tv.value.get() instanceof ConditionalValue);
            ConditionalValue conditionalValue = (ConditionalValue) tv.value.get();
            Assert.assertEquals("null == this.left?orElse,@NotNull:this.left", conditionalValue.toString());
            Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, tv.value.get().getPropertyOutsideContext(VariableProperty.NOT_NULL));
        }
        if ("Either".equals(methodInfo.name) && iteration > 0) {
            Assert.assertEquals("((null == a or null == b) and (not (null == a) or not (null == b)))",
                    methodInfo.methodAnalysis.get().precondition.get().toString());
        }
    };

    // we do expect 2x potential null pointer exception, because you can call getLeft() when you initialised with right() and vice versa

    @Test
    public void test() throws IOException {
        testUtilClass(List.of("Either"), 0, 2, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

}
