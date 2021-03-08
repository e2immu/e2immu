
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
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.FieldAnalysis;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.expression.StringConstant;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class


Test_00_Basics_0 extends CommonTestRunner {
    private static final String TYPE = "org.e2immu.analyser.testexample.Basics_0";

    public Test_00_Basics_0() {
        super(false);
    }

    // value only comes in second iteration
    EvaluationResultVisitor evaluationResultVisitor = d -> {
        if (d.methodInfo().name.equals("getExplicitlyFinal") && "0".equals(d.statementId())) {
            String expectValue = d.iteration() == 0 ? "<f:explicitlyFinal>" : "\"abc\"";
            Assert.assertEquals(expectValue, d.evaluationResult().value().toString());

            Assert.assertEquals(d.iteration() == 0, d.evaluationResult().someValueWasDelayed());
        }
    };

    // everything in first iteration
    FieldAnalyserVisitor afterFieldAnalyserVisitor = d -> {
        FieldAnalysis fieldAnalysis = d.fieldAnalysis();
        if ("explicitlyFinal".equals(d.fieldInfo().name)) {
            Assert.assertEquals("", fieldAnalysis.getLinkedVariables().toString());
            Assert.assertEquals(Level.TRUE, fieldAnalysis.getProperty(VariableProperty.FINAL));
            Assert.assertEquals("\"abc\"", fieldAnalysis.getEffectivelyFinalValue().toString());
            Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(fieldAnalysis.getEffectivelyFinalValue(),
                    VariableProperty.NOT_NULL_EXPRESSION));
            Assert.assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE, fieldAnalysis.getProperty(VariableProperty.IMMUTABLE));
            Assert.assertTrue(fieldAnalysis.getLinkedVariables().isEmpty());
        }
    };

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if (d.methodInfo().name.equals("getExplicitlyFinal") && "0".equals(d.statementId())) {
            Assert.assertTrue(d.condition().isBoolValueTrue());
            Assert.assertTrue(d.state().isBoolValueTrue());
            Assert.assertTrue(d.localConditionManager().precondition().isBoolValueTrue());
        }
    };

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if (d.methodInfo().name.equals("getExplicitlyFinal") && "0".equals(d.statementId())) {
            // the field
            if ((TYPE + ".explicitlyFinal").equals(d.variableName())) {
                Assert.assertFalse(d.variableInfo().isAssigned());
                Assert.assertTrue(d.variableInfo().isRead());
                if (d.iteration() == 0) {
                    Assert.assertTrue(d.currentValueIsDelayed());
                } else {
                    Assert.assertEquals(new StringConstant(d.evaluationContext().getPrimitives(), "abc"), d.currentValue());
                }
                int expectEnn = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                Assert.assertEquals(expectEnn, d.getProperty(VariableProperty.EXTERNAL_NOT_NULL));
                return;
            }
            // this.
            if ((TYPE + ".this").equals(d.variableName())) {
                Assert.assertTrue(d.variableInfo().isRead());
                Assert.assertEquals("instance type Basics_0", d.currentValue().toString());
                Assert.assertEquals(MultiLevel.NOT_INVOLVED, d.getProperty(VariableProperty.EXTERNAL_NOT_NULL));
                return;
            }
            // the return value
            Assert.assertEquals((TYPE + ".getExplicitlyFinal()"), d.variableName());
            String expectReturn = d.iteration() == 0 ? "<f:explicitlyFinal>" : "\"abc\"";
            Assert.assertEquals(expectReturn, d.currentValue().toString());

            int expectNotNull = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
            Assert.assertEquals(expectNotNull, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));

            int expectEnn = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
            Assert.assertEquals(expectEnn, d.getProperty(VariableProperty.EXTERNAL_NOT_NULL));

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
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build());
    }

}
