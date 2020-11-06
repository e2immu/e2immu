
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

import org.e2immu.analyser.analyser.AnalysisProvider;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.FieldAnalyserVisitor;
import org.e2immu.analyser.config.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.config.TypeContextVisitor;
import org.e2immu.analyser.model.FieldAnalysis;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.value.StringValue;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class Test_00_Basics extends CommonTestRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(Test_00_Basics.class);

    public Test_00_Basics() {
        super(false);
    }

    FieldAnalyserVisitor afterFieldAnalyserVisitor = d -> {
        FieldAnalysis fieldAnalysis = d.fieldAnalysis();
        if ("explicitlyFinal".equals(d.fieldInfo().name)) {
            if (d.iteration() == 0) {
                Assert.assertEquals(Level.TRUE, fieldAnalysis.getProperty(VariableProperty.FINAL));
                Assert.assertEquals("abc", fieldAnalysis.getEffectivelyFinalValue().toString());
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(fieldAnalysis.getEffectivelyFinalValue(),
                        VariableProperty.NOT_NULL));
            }
            if (d.iteration() > 0) {
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, fieldAnalysis.getProperty(VariableProperty.NOT_NULL));
                Assert.assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE, fieldAnalysis.getProperty(VariableProperty.IMMUTABLE));
            }
        }
    };

    StatementAnalyserVariableVisitor statementAnalyserVisitor = d -> {
        if (d.methodInfo().name.equals("getExplicitlyFinal")
                && "0".equals(d.statementId())) {
            if ("org.e2immu.analyser.testexample.Basics.explicitlyFinal".equals(d.variableName())) {
                Assert.assertFalse(d.hasProperty(VariableProperty.ASSIGNED));
                Assert.assertFalse(d.hasProperty(VariableProperty.NOT_NULL));
                Assert.assertEquals(Level.TRUE, d.getProperty(VariableProperty.READ));
                Assert.assertEquals(new StringValue(d.evaluationContext().getPrimitives(), "abc"), d.currentValue());
                return;
            }
            if ("org.e2immu.analyser.testexample.Basics.this".equals(d.variableName())) {
                Assert.assertEquals(Level.TRUE, d.getProperty(VariableProperty.READ)); //
                return;
            }
            // the return value
            Assert.assertEquals("org.e2immu.analyser.testexample.Basics.getExplicitlyFinal()", d.variableName());
            Assert.assertEquals("abc", d.currentValue().toString());
            Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.NOT_NULL));
            Assert.assertTrue(d.variableInfo().getLinkedVariables().isEmpty());
            return;
        }
        Assert.fail("Method name " + d.methodInfo().name + ", iteration " + d.iteration() + ", variable " + d.variableName() +
                ", statement id " + d.statementId());
    };

    TypeContextVisitor typeContextVisitor = typeContext -> {
        // check that the XML annotations have been read properly, and copied into the correct place
        TypeInfo stringType = typeContext.getPrimitives().stringTypeInfo;
        Assert.assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE, stringType.typeAnalysis.get().getProperty(VariableProperty.IMMUTABLE));
        Assert.assertFalse(stringType.hasSize(typeContext.getPrimitives(), AnalysisProvider.DEFAULT_PROVIDER));
    };

    @Test
    public void test() throws IOException {
        testClass("Basics", 0, 0, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(afterFieldAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVisitor)
                .addTypeContextVisitor(typeContextVisitor)
                .build());
    }

}
