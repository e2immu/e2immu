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

import com.google.common.collect.ImmutableSet;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.VariableValue;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.expression.LocalVariableCreation;
import org.e2immu.analyser.model.statement.Block;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class TestE2ImmutableChecks extends CommonTestRunner {
    public TestE2ImmutableChecks() {
        super(true);
    }

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("isAbc".equals(d.methodInfo().name) && "0".equals(d.statementId()) && "E2Container1.this.value1".equals(d.variableName())) {
            Assert.assertFalse("At iteration " + d.iteration(), d.properties().isSet(VariableProperty.NOT_NULL));
        }
        if ("input4".equals(d.variableName()) && "1".equals(d.statementId()) && "mingle".equals(d.methodInfo().name)) {
            Assert.assertEquals(MultiLevel.MUTABLE, d.getProperty(VariableProperty.IMMUTABLE));
        }
        if ("input4".equals(d.variableName()) && "0".equals(d.statementId()) && "mingle".equals(d.methodInfo().name)) {
            Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL,  d.getProperty(VariableProperty.NOT_NULL));
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        String name = d.methodInfo().name;
        int iteration = d.iteration();

        if ("isAbc".equals(name) && iteration > 0) {
            FieldInfo value1 = d.methodInfo().typeInfo.getFieldByName("value1", true);
            VariableInfo transferValue = d.getFieldAsVariable(value1);
            Assert.assertTrue("Got: " + transferValue.linkedVariables.get(), transferValue.linkedVariables.get().isEmpty());
        }
        if ("E2Container1".equals(name) && iteration > 1) {
            ParameterInfo value = d.methodInfo().methodInspection.get().parameters.get(0);
            Assert.assertEquals("value", value.name);
            Assert.assertNotNull(value.parameterAnalysis.get().getAssignedToField());
        }
        if ("E2Container2".equals(name) && 2 == d.methodInfo().methodInspection.get().parameters.size()) {
            if (iteration > 2) {
                FieldInfo parent2 = d.methodInfo().typeInfo.getFieldByName("parent2", true);
                VariableInfo transferValue = d.getFieldAsVariable(parent2);
                Assert.assertEquals("[0:parent2Param]", transferValue.linkedVariables.get().toString());
                // NOTE: we allow the linking to take place, but ignore its presence in independent computation
            }
        }

        // no decision about immutable of "mingle" is ever made
        if ("mingle".equals(name)) {
            VariableInfo transferValue = d.getReturnAsVariable();
            Assert.assertEquals(MultiLevel.MUTABLE, transferValue.properties.get(VariableProperty.IMMUTABLE));
            Assert.assertEquals("input4", transferValue.getValue().toString());
            Assert.assertTrue(transferValue.getValue() instanceof VariableValue);
            Assert.assertEquals(MultiLevel.MUTABLE, d.methodAnalysis().getProperty(VariableProperty.IMMUTABLE));
        }

        if ("getSet3".equals(name)) {
            VariableInfo tv = d.getReturnAsVariable();
            if (iteration > 0) {
                int immutable = tv.getProperty(VariableProperty.IMMUTABLE);
                Assert.assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE, immutable);
                // ImmutableSet.copyOf returns an L2 immutable type, so there cannot be any linking!
                Assert.assertEquals("[]", tv.linkedVariables.get().toString());
            }
        }

        if ("get4".equals(name)) {
            if (iteration > 1) {
                int independent = d.methodAnalysis().getProperty(VariableProperty.INDEPENDENT);
                Assert.assertEquals(MultiLevel.EFFECTIVE, independent);
            }
            VariableInfo tv = d.getReturnAsVariable();
            if (iteration > 0) {
                int immutable = tv.getProperty(VariableProperty.IMMUTABLE);
                Assert.assertEquals(MultiLevel.FALSE, immutable);
                // ImmutableSet.copyOf returns an L2 immutable type, so there cannot be any linking!
                Assert.assertEquals("[map4]", tv.linkedVariables.get().toString());
            }
        }

        if ("get7".equals(name) && iteration > 1) {
            Assert.assertEquals(MultiLevel.FALSE, d.methodAnalysis().getProperty(VariableProperty.INDEPENDENT));
        }
    };

    FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
        int iteration = d.iteration();
        String name = d.fieldInfo().name;
        if ("value1".equals(name) && iteration > 1) {
            Assert.assertEquals(MultiLevel.FALSE, d.fieldAnalysis().getProperty(VariableProperty.NOT_NULL));
        }
        if ("map7".equals(name)) {
            int immutable = d.fieldAnalysis().getProperty(VariableProperty.IMMUTABLE);
            if (iteration == 0) {
                Assert.assertEquals(Level.DELAY, immutable);
            } else {
                Assert.assertEquals("Iteration " + iteration, MultiLevel.FALSE, immutable);
            }
        }
    };

    TypeContextVisitor typeContextVisitor = typeContext -> {
        TypeInfo collection = typeContext.getFullyQualified(Collection.class);
        TypeInfo hashSet = typeContext.getFullyQualified(HashSet.class);
        MethodInfo constructor1 = hashSet.typeInspection.getPotentiallyRun().constructors.stream()
                .filter(m -> m.methodInspection.get().parameters.size() == 1)
                .filter(m -> m.methodInspection.get().parameters.get(0).parameterizedType.typeInfo == collection)
                .findAny().orElseThrow();
        Assert.assertEquals(MultiLevel.EFFECTIVE, constructor1.methodAnalysis.get().getProperty(VariableProperty.INDEPENDENT));

        // result of copyOf is @E2Immutable (and therefore the method is independent)
        TypeInfo immutableSet = typeContext.getFullyQualified(ImmutableSet.class);
        MethodInfo copyOf = immutableSet.typeInspection.getPotentiallyRun().methods.stream()
                .filter(m -> "copyOf".equals(m.name) && m.methodInspection.get().parameters.size() == 1)
                .filter(m -> m.methodInspection.get().parameters.get(0).parameterizedType.typeInfo == collection)
                .findAny().orElseThrow();
        Assert.assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE, copyOf.methodAnalysis.get().getProperty(VariableProperty.IMMUTABLE));

        TypeInfo set = typeContext.getFullyQualified(Set.class);
        MethodInfo addAll = set.findUniqueMethod("addAll", 1);
        ParameterInfo addAllP0 = addAll.methodInspection.get().parameters.get(0);
        Assert.assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL, addAllP0.parameterAnalysis.get().getProperty(VariableProperty.NOT_NULL));
    };

    TypeAnalyserVisitor typeAnalyserVisitor = d -> {
        if ("E2Container5".equals(d.typeInfo().simpleName)) {
            Assert.assertEquals("T", d.typeAnalysis().getImplicitlyImmutableDataTypes()
                    .stream().findFirst().orElseThrow().detailedString());
        } else if ("E2Container6".equals(d.typeInfo().simpleName)) {
            Assert.assertEquals("org.e2immu.analyser.testexample.E2ImmutableChecks.SimpleContainer",
                    d.typeAnalysis().getImplicitlyImmutableDataTypes()
                            .stream().findFirst().orElseThrow().detailedString());

        } else if ("E1Container7".equals(d.typeInfo().simpleName)) {
            Assert.assertEquals(0, d.typeAnalysis().getImplicitlyImmutableDataTypes().size());

            if (d.iteration() > 2) {
                Assert.assertEquals(MultiLevel.EFFECTIVE, d.typeAnalysis().getProperty(VariableProperty.INDEPENDENT));
            }

            // a bit of inspection check... was a temporary bug during a refactoring.
            MethodInfo getMap7 = d.typeInfo().findUniqueMethod("getMap7", 0);
            Block block = getMap7.methodInspection.get().methodBody.get();
            Assert.assertEquals(3, block.structure.statements.size());
            Statement statement1 = block.structure.statements.get(0);

            Assert.assertTrue(statement1.getStructure().initialisers.get(0) instanceof LocalVariableCreation);
            Assert.assertSame(EmptyExpression.EMPTY_EXPRESSION, statement1.getStructure().expression);
            Assert.assertEquals("Map<String, SimpleContainer> incremented = new HashMap(map7);\n", statement1.statementString(0, null));
        } else {
            Assert.assertEquals(0, d.typeAnalysis().getImplicitlyImmutableDataTypes().size());
        }
    };


    @Test
    public void test() throws IOException {
        testClass("E2ImmutableChecks", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addTypeContextVisitor(typeContextVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .build());
    }

}
