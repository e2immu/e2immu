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
import org.e2immu.analyser.analyser.NumberedStatement;
import org.e2immu.analyser.analyser.TransferValue;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.VariableValuePlaceholder;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.expression.LocalVariableCreation;
import org.e2immu.analyser.model.statement.Block;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;

// the @NotNull on value1 travels from isAbc to value1 to value as parameter of the constructor

/*

https://github.com/bnaudts/e2immu/issues/13

 */
public class TestE2ImmutableChecks extends CommonTestRunner {
    public TestE2ImmutableChecks() {
        super(true);
    }

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("isAbc".equals(d.methodInfo.name) && "0".equals(d.statementId) && "E2Container1.this.value1".equals(d.variableName)) {
            Assert.assertNull("At iteration " + d.iteration, d.properties.get(VariableProperty.NOT_NULL));
        }
        // no decision about immutable of "mingle" is ever made
        if ("input4".equals(d.variableName) && "1".equals(d.statementId) && "mingle".equals(d.methodInfo.name)) {
            Assert.assertEquals(MultiLevel.MUTABLE, (int) d.properties.get(VariableProperty.IMMUTABLE));
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = (iteration, methodInfo) -> {
        if ("isAbc".equals(methodInfo.name) && iteration > 0) {
            FieldInfo value1 = methodInfo.typeInfo.typeInspection.get().fields.stream().filter(f -> "value1".equals(f.name)).findFirst().orElseThrow();
            TransferValue transferValue = methodInfo.methodAnalysis.get().fieldSummaries.get(value1);
            Assert.assertTrue("Got: " + transferValue.linkedVariables.get(), transferValue.linkedVariables.get().isEmpty());
        }
        if ("E2Container1".equals(methodInfo.name) && iteration > 1) {
            ParameterInfo value = methodInfo.methodInspection.get().parameters.get(0);
            Assert.assertEquals("value", value.name);
            Assert.assertTrue(value.parameterAnalysis.get().assignedToField.isSet());
        }
        if ("E2Container2".equals(methodInfo.name) && 2 == methodInfo.methodInspection.get().parameters.size()) {
            if (iteration > 2) {
                FieldInfo parent2 = methodInfo.typeInfo.typeInspection.get().fields.stream().filter(f -> "parent2".equals(f.name)).findFirst().orElseThrow();
                TransferValue transferValue = methodInfo.methodAnalysis.get().fieldSummaries.get(parent2);
                Assert.assertEquals("[0:parent2Param]", transferValue.linkedVariables.get().toString());
                // NOTE: we allow the linking to take place, but ignore its presence in independent computation
            }
        }

        // no decision about immutable of "mingle" is ever made
        if ("mingle".equals(methodInfo.name)) {
            TransferValue transferValue = methodInfo.methodAnalysis.get().returnStatementSummaries.get("1");
            Assert.assertEquals(MultiLevel.MUTABLE, transferValue.properties.get(VariableProperty.IMMUTABLE));
            Assert.assertEquals("input4", transferValue.value.get().toString());
            Assert.assertTrue(transferValue.value.get() instanceof VariableValuePlaceholder);
            Assert.assertEquals(MultiLevel.MUTABLE, methodInfo.methodAnalysis.get().getProperty(VariableProperty.IMMUTABLE));
        }

        if ("getSet3".equals(methodInfo.name)) {
            TransferValue tv = methodInfo.methodAnalysis.get().returnStatementSummaries.get("0");
            if (iteration > 0) {
                int immutable = tv.getProperty(VariableProperty.IMMUTABLE);
                Assert.assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE, immutable);
                // ImmutableSet.copyOf returns an L2 immutable type, so there cannot be any linking!
                Assert.assertEquals("[]", tv.linkedVariables.get().toString());
            }
        }

        if ("get4".equals(methodInfo.name)) {
            if (iteration > 1) {
                int independent = methodInfo.methodAnalysis.get().getProperty(VariableProperty.INDEPENDENT);
                Assert.assertEquals(Level.TRUE, independent);
            }
            TransferValue tv = methodInfo.methodAnalysis.get().returnStatementSummaries.get("0");
            if (iteration > 0) {
                int immutable = tv.getProperty(VariableProperty.IMMUTABLE);
                Assert.assertEquals(MultiLevel.FALSE, immutable);
                // ImmutableSet.copyOf returns an L2 immutable type, so there cannot be any linking!
                Assert.assertEquals("[map4]", tv.linkedVariables.get().toString());
            }
        }
    };

    FieldAnalyserVisitor fieldAnalyserVisitor = (iteration, fieldInfo) -> {
        if ("value1".equals(fieldInfo.name) && iteration > 1) {
            Assert.assertEquals(MultiLevel.FALSE, fieldInfo.fieldAnalysis.get().getProperty(VariableProperty.NOT_NULL));
        }
    };

    TypeContextVisitor typeContextVisitor = typeContext -> {
        TypeInfo collection = typeContext.getFullyQualified(Collection.class);
        TypeInfo hashSet = typeContext.getFullyQualified(HashSet.class);
        MethodInfo constructor1 = hashSet.typeInspection.get().constructors.stream()
                .filter(m -> m.methodInspection.get().parameters.size() == 1)
                .filter(m -> m.methodInspection.get().parameters.get(0).parameterizedType.typeInfo == collection)
                .findAny().orElseThrow();
        Assert.assertEquals(Level.TRUE, constructor1.methodAnalysis.get().getProperty(VariableProperty.INDEPENDENT));

        // result of copyOf is @E2Immutable (and therefore the method is independent)
        TypeInfo immutableSet = typeContext.getFullyQualified(ImmutableSet.class);
        MethodInfo copyOf = immutableSet.typeInspection.get().methods.stream()
                .filter(m -> "copyOf".equals(m.name) && m.methodInspection.get().parameters.size() == 1)
                .filter(m -> m.methodInspection.get().parameters.get(0).parameterizedType.typeInfo == collection)
                .findAny().orElseThrow();
        Assert.assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE, copyOf.methodAnalysis.get().getProperty(VariableProperty.IMMUTABLE));
    };

    TypeAnalyserVisitor typeAnalyserVisitor = (iteration, typeInfo) -> {
        if ("E2Container5".equals(typeInfo.simpleName)) {
            Assert.assertEquals("T", typeInfo.typeAnalysis.get().implicitlyImmutableDataTypes.get()
                    .stream().findFirst().orElseThrow().detailedString());
        } else if ("E2Container6".equals(typeInfo.simpleName)) {
            Assert.assertEquals("org.e2immu.analyser.testexample.E2ImmutableChecks.SimpleContainer",
                    typeInfo.typeAnalysis.get().implicitlyImmutableDataTypes.get()
                            .stream().findFirst().orElseThrow().detailedString());

        } else if ("E2Container7".equals(typeInfo.simpleName)) {
            Assert.assertEquals(0, typeInfo.typeAnalysis.get().implicitlyImmutableDataTypes.get().size());

            MethodInfo getMap7 = typeInfo.findUniqueMethod("getMap7", 0);
            Block block = getMap7.methodInspection.get().methodBody.get();
            Assert.assertEquals(3, block.structure.statements.size());
            Statement statement1 = block.structure.statements.get(0);

            Assert.assertTrue(statement1.getStructure().initialisers.get(0) instanceof LocalVariableCreation);
            Assert.assertSame(EmptyExpression.EMPTY_EXPRESSION, statement1.getStructure().expression);
            Assert.assertEquals("Map<String, SimpleContainer> incremented = new HashMap(map7);\n", statement1.statementString(0, null));
        } else {
            Assert.assertEquals(0, typeInfo.typeAnalysis.get().implicitlyImmutableDataTypes.get().size());
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
