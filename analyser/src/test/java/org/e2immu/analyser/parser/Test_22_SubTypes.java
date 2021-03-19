
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
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

public class Test_22_SubTypes extends CommonTestRunner {
    public Test_22_SubTypes() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        testClass("SubTypes_0", 0, 0, new DebugConfiguration.Builder().build());
    }

    @Test
    public void test_1() throws IOException {
        final String SUBTYPE = "MethodWithSubType$KV$1";
        final String KV = "org.e2immu.analyser.testexample.SubTypes_1." + SUBTYPE;
        final String KEY = KV + ".key";

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("key".equals(d.fieldInfo().name) && SUBTYPE.equals(d.fieldInfo().owner.simpleName)) {
                assertEquals(Level.TRUE, d.fieldAnalysis().getProperty(VariableProperty.FINAL));
                assertEquals("", d.fieldAnalysis().getLinkedVariables().toString());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if (SUBTYPE.equals(d.methodInfo().name)) {
                assertTrue(d.methodInfo().isConstructor);
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if (SUBTYPE.equals(d.methodInfo().name) && KEY.equals(d.variableName())) {
                assertEquals("key", d.currentValue().toString());
                // empty because String is @E2Container!
                assertEquals("", d.variableInfo().getLinkedVariables().toString());
            }

        };

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if (SUBTYPE.equals(d.methodInfo().name) && "0".equals(d.statementId())) {
                assertEquals("key", d.evaluationResult().value().toString());
            }
            if (SUBTYPE.equals(d.methodInfo().name) && "1".equals(d.statementId())) {
                assertEquals("value+\"abc\"", d.evaluationResult().value().toString());
            }
        };

        testClass("SubTypes_1", 0, 0, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build());
    }

    @Test
    public void test_2() throws IOException {

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("NonStaticSubType2".equals(d.methodInfo().typeInfo.simpleName) && "toString".equals(d.methodInfo().name)) {
                Set<MethodAnalysis> overrides = d.methodAnalysis().getOverrides(d.evaluationContext().getAnalyserContext());
                assertEquals(1, overrides.size());
                MethodAnalysis objectToString = overrides.stream().findFirst().orElseThrow();
                assertEquals("Object", objectToString.getMethodInfo().typeInfo.simpleName);
            }
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo object = typeMap.get(Object.class);
            MethodInfo toString = object.findUniqueMethod("toString", 0);
            assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL,
                    toString.methodAnalysis.get().getProperty(VariableProperty.NOT_NULL_EXPRESSION));
            assertEquals(Level.FALSE, toString.methodAnalysis.get().getProperty(VariableProperty.MODIFIED_METHOD));

            TypeInfo nonStatic2 = typeMap.get("org.e2immu.analyser.testexample.SubTypes_2.NonStaticSubType2");
            MethodInfo toString2 = nonStatic2.findUniqueMethod("toString", 0);
            Set<MethodInfo> overrides = toString2.methodResolution.get().overrides();
            assertEquals(1, overrides.size());
            assertSame(toString, overrides.stream().findFirst().orElseThrow());
        };

        testClass("SubTypes_2", 3, 0, new DebugConfiguration.Builder()
                .addTypeMapVisitor(typeMapVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test_3() throws IOException {
        testClass("SubTypes_3", 0, 0, new DebugConfiguration.Builder().build());
    }

    @Test
    public void test_4() throws IOException {
        testClass("SubTypes_4", 2, 0, new DebugConfiguration.Builder().build());
    }

    @Test
    public void test_5() throws IOException {
        testClass("SubTypes_5", 0, 0, new DebugConfiguration.Builder().build());
    }

    @Test
    public void test_6() throws IOException {
        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo function = typeMap.get(Function.class);
            MethodInfo apply = function.findUniqueMethod("apply", 1);
            ParameterInfo apply0 = apply.methodInspection.get().getParameters().get(0);
            int modified = apply0.parameterAnalysis.get().getProperty(VariableProperty.MODIFIED_VARIABLE);
            assertEquals(Level.FALSE, modified);
        };

        // error: we postulate that Function has a @NotModified parameter
        testClass("SubTypes_6", 1, 0, new DebugConfiguration.Builder()
                .addTypeMapVisitor(typeMapVisitor)
                .build());
    }

    @Test
    public void test_7() throws IOException {
        testClass("SubTypes_7", 0, 0, new DebugConfiguration.Builder().build());
    }
}
