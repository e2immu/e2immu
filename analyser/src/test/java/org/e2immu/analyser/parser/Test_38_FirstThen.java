
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

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.visitor.FieldAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.TypeAnalyserVisitor;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

/*
a series of tests to detect an infinite delayed loop 20210311
 */
public class Test_38_FirstThen extends CommonTestRunner {

    public Test_38_FirstThen() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("FirstThen_0".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo first && "first".equals(first.name)) {
                    Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                    Assert.assertEquals("nullable instance type S", d.currentValue().toString());
                    Assert.assertEquals("", d.variableInfo().getLinkedVariables().toString());
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("first".equals(d.fieldInfo().name)) {
                Assert.assertEquals(Level.FALSE, d.fieldAnalysis().getProperty(VariableProperty.FINAL));

                String expectValues = "[first/*@NotNull*/,null]";
          //      Assert.assertEquals(expectValues, ((FieldAnalysisImpl.Builder) d.fieldAnalysis()).getValues().toString());
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("FirstThen_0".equals(d.typeInfo().simpleName)) {
                Assert.assertEquals("[Type param S]", d.typeAnalysis().getImplicitlyImmutableDataTypes().toString());
            }
        };

        testClass("FirstThen_0", 0, 0, new DebugConfiguration.Builder()
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }

    @Test
    public void test_1() throws IOException {
        testClass("FirstThen_1", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

}
