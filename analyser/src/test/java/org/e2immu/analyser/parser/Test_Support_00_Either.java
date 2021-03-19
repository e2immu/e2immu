
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

import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.InlineConditional;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Test_Support_00_Either extends CommonTestRunner {

    public Test_Support_00_Either() {
        super(true);
    }

    /*  getLeftOrElse:
        A local = left;
        return local != null ? local : Objects.requireNonNull(orElse);
     */

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("getLeftOrElse".equals(d.methodInfo().name)) {
            if (d.variable() instanceof ParameterInfo orElse && "orElse".equals(orElse.name)) {
                if ("0".equals(d.statementId())) {
                    int expectContainer = d.iteration() <= 1 ? Level.DELAY : Level.TRUE;
                    assertEquals(expectContainer, d.getProperty(VariableProperty.CONTAINER));
                }
                if ("1".equals(d.statementId())) {
                    String expectValue = d.iteration() == 0 ? "<p:orElse>" : "nullable instance type A";
                    assertEquals(expectValue, d.currentValue().toString());
                }
            }
            if (d.variable() instanceof ReturnVariable) {
                if ("1".equals(d.statementId())) {
                    String expectValue = d.iteration() == 0 ? "null==<f:left>?orElse/*@NotNull*/:<f:left>" :
                            "null==left?orElse/*@NotNull*/:left";
                    assertEquals(expectValue, d.currentValue().toString());
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                }
            }
        }
        if ("Either".equals(d.methodInfo().name)) {
            if (d.variable() instanceof FieldReference fr && fr.fieldInfo.name.equals("left")) {
                assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                assertEquals("a", d.currentValue().toString());
                assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
            }
            if (d.variable() instanceof FieldReference fr && fr.fieldInfo.name.equals("right")) {
                assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                assertEquals("b", d.currentValue().toString());
                assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
            }
            if (d.variable() instanceof ParameterInfo a && "a".equals(a.name)) {
                assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
            }
            if (d.variable() instanceof ParameterInfo b && "b".equals(b.name)) {
                assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
            }
        }
    };

    EvaluationResultVisitor evaluationResultVisitor = d -> {
        if ("Either".equals(d.methodInfo().name) && "0".equals(d.statementId())) {
            assertEquals("(null==a||null!=b)&&(null!=a||null==b)", d.evaluationResult().value().toString());
        }
    };

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if ("Either".equals(d.methodInfo().name)) {
            if ("0.0.0".equals(d.statementId())) {
                assertEquals("(null==a||null!=b)&&(null!=a||null==b)", d.condition().toString());
                assertEquals("true", d.state().toString());
                assertEquals("(null==a||null==b)&&(null!=a||null!=b)",
                        d.statementAnalysis().stateData.precondition.get().toString());
            }
            if ("0".equals(d.statementId())) {
                assertEquals("(null==a||null==b)&&(null!=a||null!=b)",
                        d.statementAnalysis().methodLevelData.combinedPrecondition.get().toString());
            }
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        if ("getLeftOrElse".equals(d.methodInfo().name)) {
            VariableInfo tv = d.getReturnAsVariable();
            Expression retVal = tv.getValue();
            assertTrue(retVal instanceof InlineConditional);
            InlineConditional conditionalValue = (InlineConditional) retVal;
            String expectValue = d.iteration() == 0 ? "null==<f:left>?orElse/*@NotNull*/:<f:left>" :
                    "null==left?orElse/*@NotNull*/:left";
            assertEquals(expectValue, conditionalValue.toString());
            assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(retVal, VariableProperty.NOT_NULL_EXPRESSION));
        }
        if ("Either".equals(d.methodInfo().name)) {
            assertEquals("(null==a||null==b)&&(null!=a||null!=b)",
                    d.methodAnalysis().getPrecondition().toString());
            ParameterAnalysis a = d.parameterAnalyses().get(0);
            int expectNnp = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
            assertEquals(expectNnp, a.getProperty(VariableProperty.NOT_NULL_PARAMETER));
            ParameterAnalysis b = d.parameterAnalyses().get(1);
            assertEquals(expectNnp, b.getProperty(VariableProperty.NOT_NULL_PARAMETER));
        }
    };

    FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
        if ("left".equals(d.fieldInfo().name)) {
            assertEquals("a", d.fieldAnalysis().getEffectivelyFinalValue().toString());
            assertEquals(MultiLevel.NULLABLE, d.fieldAnalysis().getProperty(VariableProperty.EXTERNAL_NOT_NULL));
        }
        if ("right".equals(d.fieldInfo().name)) {
            assertEquals("b", d.fieldAnalysis().getEffectivelyFinalValue().toString());
            assertEquals(MultiLevel.NULLABLE, d.fieldAnalysis().getProperty(VariableProperty.EXTERNAL_NOT_NULL));
        }
    };

    // we do expect 2x potential null pointer exception, because you can call getLeft() when you initialised with right() and vice versa

    @Test
    public void test() throws IOException {
        testSupportClass(List.of("Either"), 0, 2, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }

}
