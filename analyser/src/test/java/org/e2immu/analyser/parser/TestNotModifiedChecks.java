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

import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.value.UnknownValue;
import org.e2immu.analyser.model.variable.Variable;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Set;

public class TestNotModifiedChecks extends CommonTestRunner {
    public TestNotModifiedChecks() {
        super(true);
    }

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("addAll".equals(d.methodInfo().name) && "d".equals(d.variableName())) {
            Assert.assertEquals(0, d.getProperty(VariableProperty.MODIFIED));
        }
        if ("addAll".equals(d.methodInfo().name) && "c".equals(d.variableName())) {
            Assert.assertEquals(1, d.getProperty(VariableProperty.MODIFIED));
        }
        if ("addAllOnC".equals(d.methodInfo().name)) {
            if ("d".equals(d.variableName())) {
                Assert.assertEquals(0, d.getProperty(VariableProperty.MODIFIED));
            }
            if ("d.set".equals(d.variableName())) {
                Assert.assertEquals(0, d.getProperty(VariableProperty.MODIFIED));
            }
            if ("c.set".equals(d.variableName())) {
                Assert.assertEquals(1, d.getProperty(VariableProperty.MODIFIED));
            }
            if ("c".equals(d.variableName())) {
                Assert.assertEquals(1, d.getProperty(VariableProperty.MODIFIED));
            }
        }
        if ("NotModifiedChecks".equals(d.methodInfo().name) && "NotModifiedChecks.this.s2".equals(d.variableName())) {
            if (d.iteration() < 2) {
                Assert.assertSame(UnknownValue.NO_VALUE, d.currentValue());
            } else {
                Assert.assertEquals("set2", d.currentValue().toString());
            }
        }
        if ("C1".equals(d.methodInfo().name) && "C1.this.set".equals(d.variableName())) {
            Assert.assertEquals("set1,@NotNull", d.currentValue().toString());
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        int iteration = d.iteration();
        String name = d.methodInfo().name;

        if ("NotModifiedChecks".equals(d.methodInfo().name)) {
            ParameterAnalysis list = d.parameterAnalyses().get(0);
            ParameterAnalysis set2 = d.parameterAnalyses().get(1);
            ParameterAnalysis set3 = d.parameterAnalyses().get(2);
            ParameterAnalysis set4 = d.parameterAnalyses().get(3);

            if (iteration == 0) {
                Assert.assertNull(list.getAssignedToField());
            } else {
                Assert.assertNotNull(list.getAssignedToField());
            }
            if (iteration >= 2) {
                Assert.assertEquals(0, list.getProperty(VariableProperty.MODIFIED));
                Assert.assertNotNull(set3.getAssignedToField());
                Assert.assertEquals(1, set3.getProperty(VariableProperty.MODIFIED)); // directly assigned to s0
                Assert.assertEquals(1, set2.getProperty(VariableProperty.MODIFIED));
                Assert.assertEquals(1, set4.getProperty(VariableProperty.MODIFIED));
            }
            FieldInfo s2 = d.methodInfo().typeInfo.getFieldByName("s2", true);
            if (iteration > 1) {
                Set<Variable> s2links = d.getFieldAsVariable(s2).getLinkedVariables();
                Assert.assertEquals("[1:set2]", s2links.toString());
            }
            FieldInfo set = d.methodInfo().typeInfo.typeInspection.get().subTypes().get(0).getFieldByName("set", true);
            Assert.assertFalse(d.methodAnalysis().getLastStatement().variables.isSet(set.fullyQualifiedName()));
        }
        if ("addAllOnC".equals(name)) {
            ParameterInfo c1 = d.methodInfo().methodInspection.get().getParameters().get(0);
            Assert.assertEquals("c1", c1.name);
            Assert.assertEquals(Level.TRUE, c1.parameterAnalysis.get().getProperty(VariableProperty.MODIFIED));
        }
        if ("getSet".equals(name)) {
            if (iteration > 0) {
                int identity = d.getReturnAsVariable().getProperty(VariableProperty.IDENTITY);
                Assert.assertEquals(Level.FALSE, identity);
                Assert.assertEquals(Level.FALSE, d.methodAnalysis().getProperty(VariableProperty.IDENTITY));

            }
            if (iteration > 1) {
                Value value = d.methodAnalysis().getSingleReturnValue();
                Assert.assertEquals("inline getSet on this.set", value.toString());
            }
        }
        if ("C1".equals(name)) {
            FieldInfo fieldInfo = d.methodInfo().typeInfo.getFieldByName("set", true);
            VariableInfo tv = d.getFieldAsVariable(fieldInfo);
            Assert.assertEquals("[0:set1]", tv.getLinkedVariables().toString());
        }
    };

    FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
        int iteration = d.iteration();
        FieldInfo fieldInfo = d.fieldInfo();
        if ("c0".equals(fieldInfo.name)) {
            if (iteration >= 2) {
                Assert.assertEquals(0, d.fieldAnalysis().getProperty(VariableProperty.MODIFIED));
            }
        }
        if ("s0".equals(fieldInfo.name)) {
            if (iteration >= 2) {
                Assert.assertEquals(1, d.fieldAnalysis().getProperty(VariableProperty.MODIFIED));
            }
        }
        if ("set".equals(fieldInfo.name)) {
            if (iteration > 0) {
                Assert.assertEquals("this.set", d.fieldAnalysis().getEffectivelyFinalValue().toString());
            }
            if (iteration > 0) {
                Assert.assertEquals("[0:set1]", d.fieldAnalysis().getVariablesLinkedToMe().toString());
            }
        }
    };

    TypeMapVisitor typeMapVisitor = typeMap -> {
        TypeInfo set = typeMap.get(Set.class);

        MethodInfo addAll = set.typeInspection.get().methods().stream().filter(mi -> mi.name.equals("addAll")).findFirst().orElseThrow();
        Assert.assertEquals(Level.TRUE, addAll.methodAnalysis.get().getProperty(VariableProperty.MODIFIED));

        ParameterInfo first = addAll.methodInspection.get().getParameters().get(0);
        Assert.assertEquals(Level.FALSE, first.parameterAnalysis.get().getProperty(VariableProperty.MODIFIED));

    };


    @Test
    public void test() throws IOException {
        testClass("NotModifiedChecks", 0, 0, new DebugConfiguration.Builder()
                .addTypeContextVisitor(typeMapVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

}
