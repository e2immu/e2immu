package org.e2immu.analyser.parser;

import com.google.common.collect.ImmutableSet;
import org.e2immu.analyser.analyser.TransferValue;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.VariableValuePlaceholder;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;

// the @NotNull on value1 travels from isAbc to value1 to value as parameter of the constructor

public class TestE2ImmutableChecks extends CommonTestRunner {
    public TestE2ImmutableChecks() {
        super(true);
    }

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = new StatementAnalyserVariableVisitor() {
        @Override
        public void visit(int iteration, MethodInfo methodInfo, String statementId, String variableName, Variable variable, Value currentValue, Map<VariableProperty, Integer> properties) {
            if ("isAbc".equals(methodInfo.name) && "0".equals(statementId) && "E2Container1.this.value1".equals(variableName)) {
                if (iteration > 0) Assert.assertEquals(Level.TRUE, (int) properties.get(VariableProperty.NOT_NULL));
            }
            // no decision about immutable of "mingle" is ever made
            if ("input4".equals(variableName) && "1".equals(statementId) && "mingle".equals(methodInfo.name)) {
                Assert.assertEquals(0, (int)properties.get(VariableProperty.IMMUTABLE));
            }
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = new MethodAnalyserVisitor() {
        @Override
        public void visit(int iteration, MethodInfo methodInfo) {
            if ("isAbc".equals(methodInfo.name) && iteration > 0) {
                FieldInfo value1 = methodInfo.typeInfo.typeInspection.get().fields.stream().filter(f -> "value1".equals(f.name)).findFirst().orElseThrow();
                TransferValue transferValue = methodInfo.methodAnalysis.get().fieldSummaries.get(value1);
                Assert.assertTrue("Got: " + transferValue.linkedVariables.get(), transferValue.linkedVariables.get().isEmpty());
                Assert.assertEquals(Level.TRUE, transferValue.properties.get(VariableProperty.NOT_NULL));
            }
            if ("E2Container1".equals(methodInfo.name) && iteration > 1) {
                ParameterInfo value = methodInfo.methodInspection.get().parameters.get(0);
                Assert.assertEquals("value", value.name);
                Assert.assertTrue(value.parameterAnalysis.get().assignedToField.isSet());
                Assert.assertEquals(Level.TRUE, value.parameterAnalysis.get().getProperty(VariableProperty.NOT_NULL));
            }
            if ("E2Container2".equals(methodInfo.name) && 2 == methodInfo.methodInspection.get().parameters.size()) {
                if (iteration > 2) {
                    FieldInfo parent2 = methodInfo.typeInfo.typeInspection.get().fields.stream().filter(f -> "parent2".equals(f.name)).findFirst().orElseThrow();
                    TransferValue transferValue = methodInfo.methodAnalysis.get().fieldSummaries.get(parent2);
                    Assert.assertTrue("Got: " + transferValue.linkedVariables.get(), transferValue.linkedVariables.get().isEmpty());
                }
            }

            // no decision about immutable of "mingle" is ever made
            if ("mingle".equals(methodInfo.name)) {
                TransferValue transferValue = methodInfo.methodAnalysis.get().returnStatementSummaries.get("1");
                Assert.assertEquals(Level.FALSE, transferValue.properties.get(VariableProperty.IMMUTABLE));
                Assert.assertEquals("input4", transferValue.value.get().toString());
                Assert.assertTrue(transferValue.value.get() instanceof VariableValuePlaceholder);
                Assert.assertEquals(Level.FALSE, methodInfo.methodAnalysis.get().getProperty(VariableProperty.IMMUTABLE));
            }

            if ("getSet3".equals(methodInfo.name)) {
                TransferValue tv = methodInfo.methodAnalysis.get().returnStatementSummaries.get("0");
                if (iteration > 0) {
                    Assert.assertTrue(tv.linkedVariables.get().isEmpty());
                }
            }
        }
    };

    FieldAnalyserVisitor fieldAnalyserVisitor = new FieldAnalyserVisitor() {
        @Override
        public void visit(int iteration, FieldInfo fieldInfo) {
            if ("value1".equals(fieldInfo.name) && iteration > 1) {
                Assert.assertEquals(Level.TRUE, fieldInfo.fieldAnalysis.get().getProperty(VariableProperty.NOT_NULL));
            }
        }
    };

    TypeContextVisitor typeContextVisitor = new TypeContextVisitor() {
        @Override
        public void visit(TypeContext typeContext) {
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
            Assert.assertEquals(Level.TRUE_LEVEL_1, copyOf.methodAnalysis.get().getProperty(VariableProperty.IMMUTABLE));
        }
    };

    @Test
    public void test() throws IOException {
        testClass("E2ImmutableChecks", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addTypeContextVisitor(typeContextVisitor)
                .build());
    }

}
