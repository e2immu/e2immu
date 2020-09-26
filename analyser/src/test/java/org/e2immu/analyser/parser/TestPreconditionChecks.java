
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

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.FieldAnalyserVisitor;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.e2immu.analyser.config.StatementAnalyserVisitor;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MethodAnalysis;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

/*
https://github.com/bnaudts/e2immu/issues/10
 */
public class TestPreconditionChecks extends CommonTestRunner {

    public TestPreconditionChecks() {
        super(false);
    }

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if ("setPositive1".equals(d.methodInfo.name)) {
            if ("0.0.0".equals(d.statementId)) {
                if (d.iteration == 0) {
                    Assert.assertSame(UnknownValue.NO_VALUE, d.condition);
                    Assert.assertSame(UnknownValue.NO_VALUE, d.state);
                } else if (d.iteration == 1) {
                    Assert.assertEquals("((-1) + (-this.i)) >= 0", d.condition.toString());
                    Assert.assertEquals("((-1) + (-this.i)) >= 0", d.state.toString());
                } else if (d.iteration > 1) {
                    Assert.assertEquals("((-1) + (-this.i)) >= 0", d.condition.toString());
                    // the precondition is now fed into the initial state, results in
                    // (((-1) + (-this.i)) >= 0 and this.i >= 0) which should resolve to false
                    Assert.assertEquals("false", d.state.toString());
                }
            }
            if ("0".equals(d.statementId)) {
                Assert.assertSame(UnknownValue.EMPTY, d.condition);
                if (d.iteration == 0) {
                    Assert.assertSame(UnknownValue.NO_VALUE, d.state);
                } else {
                    Assert.assertEquals("this.i >= 0", d.state.toString());
                }
            }
        }
        if ("setInteger".equals(d.methodInfo.name) && "0".equals(d.statementId)) {
            if (d.iteration == 0) {
                Assert.assertSame(UnknownValue.NO_VALUE, d.state);
            } else if (d.iteration == 1) {
                Assert.assertEquals("iteration " + d.iteration, "ii >= 0", d.state.toString());
            } else {
                Assert.assertEquals("iteration " + d.iteration, "(null == this.integer and ii >= 0)", d.state.toString());

                // set at the end of the synchronized block
                Assert.assertEquals("ii >= 0", d.statementAnalysis.stateData.conditionManager.get().state.toString());
            }
        }
        if ("setInteger".equals(d.methodInfo.name) && "1".equals(d.statementId)) {
            if (d.iteration > 0) {
                Assert.assertTrue("Iteration: " + d.iteration, d.statementAnalysis.errorFlags.errorValue.isSet());
            }
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = (iteration, methodInfo) -> {
        if ("setInteger".equals(methodInfo.name)) {
            if (iteration > 0) {
                Assert.assertEquals("(null == this.integer and ii >= 0)", methodInfo.methodAnalysis.get().precondition.get().toString());
            }
        }
        if ("either".equals(methodInfo.name)) {
            MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
            Assert.assertEquals("(not (null == e1) or not (null == e2))", methodAnalysis.precondition.get().toString());
        }
        if ("setPositive1".equals(methodInfo.name) && iteration > 0) {
            Assert.assertEquals("this.i >= 0", methodInfo.methodAnalysis.get().precondition.get().toString());
        }
    };

    FieldAnalyserVisitor fieldAnalyserVisitor = (iteration, fieldInfo) -> {
        if (iteration > 0 && "integer".equals(fieldInfo.name)) {
            Assert.assertEquals(Level.FALSE, fieldInfo.fieldAnalysis.get().getProperty(VariableProperty.FINAL));
        }
    };

    @Test
    public void test() throws IOException {
        testClass("PreconditionChecks", 1, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }

}
