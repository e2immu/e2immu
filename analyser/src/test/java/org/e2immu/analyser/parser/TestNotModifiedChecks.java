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
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.annotation.AnnotationMode;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/*
https://github.com/bnaudts/e2immu/issues/11
 */
public class TestNotModifiedChecks extends CommonTestRunner {
    public TestNotModifiedChecks() {
        super(true);
    }

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("addAll".equals(d.methodInfo.name) && "d".equals(d.variableName)) {
            Assert.assertEquals(0, (int) d.properties.get(VariableProperty.MODIFIED));
        }
        if ("addAll".equals(d.methodInfo.name) && "c".equals(d.variableName)) {
            Assert.assertEquals(1, (int) d.properties.get(VariableProperty.MODIFIED));
        }
        if ("addAllOnC".equals(d.methodInfo.name)) {
            if ("d".equals(d.variableName)) {
                Assert.assertEquals(0, (int) d.properties.get(VariableProperty.MODIFIED));
            }
            if ("d.set".equals(d.variableName)) {
                Assert.assertEquals(0, (int) d.properties.get(VariableProperty.MODIFIED));
            }
            if ("c.set".equals(d.variableName)) {
                Assert.assertEquals(1, (int) d.properties.get(VariableProperty.MODIFIED));
            }
            if ("c".equals(d.variableName)) {
                Assert.assertEquals(1, (int) d.properties.get(VariableProperty.MODIFIED));
            }
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = (iteration, methodInfo) -> {
        if ("NotModifiedChecks".equals(methodInfo.name)) {
            ParameterAnalysis list = methodInfo.methodInspection.get().parameters.get(0).parameterAnalysis.get();
            ParameterAnalysis set2 = methodInfo.methodInspection.get().parameters.get(1).parameterAnalysis.get();
            ParameterAnalysis set3 = methodInfo.methodInspection.get().parameters.get(2).parameterAnalysis.get();
            ParameterAnalysis set4 = methodInfo.methodInspection.get().parameters.get(3).parameterAnalysis.get();

            if (iteration == 0) {
                Assert.assertFalse(list.assignedToField.isSet());
            } else {
                Assert.assertTrue(list.assignedToField.isSet());
            }
            if (iteration >= 2) {
                Assert.assertEquals(0, list.getProperty(VariableProperty.MODIFIED));
                Assert.assertTrue(set3.assignedToField.isSet());
                //   Assert.assertEquals(1, set3.getProperty(VariableProperty.MODIFIED)); // directly assigned to s0
                //   Assert.assertEquals(1, set2.getProperty(VariableProperty.MODIFIED));
                //  Assert.assertEquals(1, set4.getProperty(VariableProperty.MODIFIED));
            }
        }
        if ("addAllOnC".equals(methodInfo.name)) {
            ParameterInfo c1 = methodInfo.methodInspection.get().parameters.get(0);
            Assert.assertEquals("c1", c1.name);
            Assert.assertEquals(Level.TRUE, c1.parameterAnalysis.get().getProperty(VariableProperty.MODIFIED));
        }
    };

    FieldAnalyserVisitor fieldAnalyserVisitor = new FieldAnalyserVisitor() {
        @Override
        public void visit(int iteration, FieldInfo fieldInfo) {
            if ("c0".equals(fieldInfo.name)) {
                if (iteration >= 2) {
                    Assert.assertEquals(0, fieldInfo.fieldAnalysis.get().getProperty(VariableProperty.MODIFIED));
                }
            }
            if ("s0".equals(fieldInfo.name)) {
                if (iteration >= 2) {
                    // Assert.assertEquals(1, fieldInfo.fieldAnalysis.get().getProperty(VariableProperty.MODIFIED));
                }
            }
        }
    };

    TypeContextVisitor typeContextVisitor = new TypeContextVisitor() {
        @Override
        public void visit(TypeContext typeContext) {
            TypeInfo set = typeContext.getFullyQualified(Set.class);

            MethodInfo addAll = set.typeInspection.get().methods.stream().filter(mi -> mi.name.equals("addAll")).findFirst().orElseThrow();
            Assert.assertEquals(Level.TRUE, addAll.methodAnalysis.get().getProperty(VariableProperty.MODIFIED));

            ParameterInfo first = addAll.methodInspection.get().parameters.get(0);
            Assert.assertEquals(Level.FALSE, first.parameterAnalysis.get().getProperty(VariableProperty.MODIFIED));

        }
    };


    @Test
    public void test() throws IOException {
        testClass("NotModifiedChecks", 0, 0, new DebugConfiguration.Builder()
                .addTypeContextVisitor(typeContextVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

}
