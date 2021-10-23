
/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.LinkedVariables;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Set;

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

        testClass("SubTypes_2", 2, 0, new DebugConfiguration.Builder()
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
        testClass("SubTypes_4", 3, 0, new DebugConfiguration.Builder().build());
    }

    @Test
    public void test_5() throws IOException {
        testClass("SubTypes_5", 0, 0, new DebugConfiguration.Builder().build());
    }

    @Test
    public void test_6() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("go".equals(d.methodInfo().name)) {
                if ("it2".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        String expectLinked = d.iteration() == 0 ? LinkedVariables.DELAY_STRING : "set2";
                        assertEquals(expectLinked, d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        // the link should not simply disappear
                        String expectLinked = d.iteration() == 0 ? LinkedVariables.DELAY_STRING : "set2";
                        assertEquals(expectLinked, d.variableInfo().getLinkedVariables().toString());
                    }
                }
                if (d.variable() instanceof ParameterInfo p && "set2".equals(p.name)) {
                    if ("0".equals(d.statementId())) {
                        // the link is symmetric, but only shows in one direction
                        assertEquals("", d.variableInfo().getLinkedVariables().toString());
                    }
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("apply".equals(d.methodInfo().name) && "$1".equals(d.methodInfo().typeInfo.simpleName)) {
                {
                    int expectModified = d.iteration() <= 1 ? Level.DELAY : Level.FALSE;
                    assertEquals(expectModified, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
                }
                {
                    ParameterAnalysis p0 = d.parameterAnalyses().get(0);
                    assertEquals("set1", ((ParameterAnalysisImpl.Builder) p0).simpleName);
                    int expectModified = d.iteration() <= 2 ? Level.DELAY : Level.TRUE;
                    assertEquals(expectModified, p0.getProperty(VariableProperty.MODIFIED_VARIABLE));
                }
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("SubTypes_6".equals(d.typeInfo().simpleName)) {
                assertEquals("", d.typeAnalysis().getTransparentTypes().toString());
            }
        };

        testClass("SubTypes_6", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .build());
    }

    @Test
    public void test_7() throws IOException {
        testClass("SubTypes_7", 0, 0, new DebugConfiguration.Builder().build());
    }
}
