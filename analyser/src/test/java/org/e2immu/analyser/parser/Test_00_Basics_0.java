
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
import org.e2immu.analyser.config.FieldAnalyserVisitor;
import org.e2immu.analyser.config.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.config.TypeMapVisitor;
import org.e2immu.analyser.model.FieldAnalysis;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.expression.StringConstant;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class Test_00_Basics_0 extends CommonTestRunner {
    private static final String TYPE = "org.e2immu.analyser.testexample.Basics_0";

    public Test_00_Basics_0() {
        super(false);
    }

    FieldAnalyserVisitor afterFieldAnalyserVisitor = d -> {
        FieldAnalysis fieldAnalysis = d.fieldAnalysis();
        if ("explicitlyFinal".equals(d.fieldInfo().name)) {
            if (d.iteration() == 0) {
                Assert.assertEquals(Level.TRUE, fieldAnalysis.getProperty(VariableProperty.FINAL));
                Assert.assertEquals("\"abc\"", fieldAnalysis.getEffectivelyFinalValue().toString());
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
            if ((TYPE + ".explicitlyFinal").equals(d.variableName())) {
                Assert.assertFalse(d.hasProperty(VariableProperty.ASSIGNED));
                Assert.assertFalse(d.hasProperty(VariableProperty.NOT_NULL));
                Assert.assertEquals(Level.TRUE, d.getProperty(VariableProperty.READ));
                Assert.assertEquals(new StringConstant(d.evaluationContext().getPrimitives(), "abc"), d.currentValue());
                return;
            }
            if ((TYPE + ".this").equals(d.variableName())) {
                Assert.assertEquals(Level.TRUE, d.getProperty(VariableProperty.READ)); //
                return;
            }
            // the return value
            Assert.assertEquals((TYPE + ".getExplicitlyFinal()"), d.variableName());
            Assert.assertEquals("\"abc\"", d.currentValue().toString());
            Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.NOT_NULL));
            Assert.assertTrue(d.variableInfo().getLinkedVariables().isEmpty());
            return;
        }
        Assert.fail("Method name " + d.methodInfo().name + ", iteration " + d.iteration() + ", variable " + d.variableName() +
                ", statement id " + d.statementId());
    };

    TypeMapVisitor typeMapVisitor = typeMap -> {
        // check that the XML annotations have been read properly, and copied into the correct place
        TypeInfo stringType = typeMap.getPrimitives().stringTypeInfo;
        Assert.assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE, stringType.typeAnalysis.get().getProperty(VariableProperty.IMMUTABLE));
    };

    @Test
    public void test() throws IOException {
        testClass("Basics_0", 0, 0, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(afterFieldAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .build());
    }

}
