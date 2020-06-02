package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.NumberedStatement;
import org.e2immu.analyser.analyser.TransferValue;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;

public class TestContainerChecks extends CommonTestRunner {
    public TestContainerChecks() {
        super(true);
    }

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = new StatementAnalyserVariableVisitor() {
        @Override
        public void visit(int iteration, MethodInfo methodInfo, String statementId, String variableName, Variable variable, Value currentValue, Map<VariableProperty, Integer> properties) {
            if ("setStrings1".equals(methodInfo.name)) {
                if ("strings1param".equals(variableName) && "0".equals(statementId)) {
                    Assert.assertNull(properties.get(VariableProperty.NOT_NULL));
                }
                if ("strings1param".equals(variableName) && "1".equals(statementId)) {
                    Assert.assertEquals(Level.TRUE, (int) properties.get(VariableProperty.NOT_NULL));
                }
                if ("Container1.this.strings1".equals(variableName) && "1".equals(statementId)) {
                    Assert.assertEquals(Level.TRUE, (int) properties.get(VariableProperty.NOT_NULL));
                }
            }
            if ("setStrings3".equals(methodInfo.name)) {
                if ("strings3param".equals(variableName) && "0".equals(statementId)) {
                    Assert.assertEquals(Level.FALSE, (int) properties.get(VariableProperty.MODIFIED));
                }
            }
        }
    };

    StatementAnalyserVisitor statementAnalyserVisitor = new StatementAnalyserVisitor() {
        @Override
        public void visit(int iteration, MethodInfo methodInfo, NumberedStatement numberedStatement, Value conditional) {
            if ("add2b".equals(methodInfo.name) && "0.0.0".equals(numberedStatement.streamIndices())) {
                if (iteration == 0) {
                    Assert.assertNull(conditional);
                } else {
                    Assert.assertEquals("not (null == strings2b)", conditional.toString());
                }
            }
            // POTENTIAL NULL POINTER EXCEPTION
            if ("add2".equals(methodInfo.name) && "0".equals(numberedStatement.streamIndices())) {
                if (iteration > 0) Assert.assertTrue(numberedStatement.errorValue.isSet());
            }
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = new MethodAnalyserVisitor() {
        @Override
        public void visit(int iteration, MethodInfo methodInfo) {
            if ("setStrings1".equals(methodInfo.name)) {
                FieldInfo strings = methodInfo.typeInfo.typeInspection.get().fields.get(0);
                Assert.assertEquals("strings1", strings.name);
                TransferValue transferValue = methodInfo.methodAnalysis.get().fieldSummaries.get(strings);
                Assert.assertEquals(Level.TRUE, transferValue.properties.get(VariableProperty.NOT_NULL));
                Assert.assertEquals(Level.TRUE, transferValue.properties.get(VariableProperty.ASSIGNED));
            }
            if ("getStrings1".equals(methodInfo.name)) {
                FieldInfo strings = methodInfo.typeInfo.typeInspection.get().fields.get(0);
                Assert.assertEquals("strings1", strings.name);
                TransferValue transferValue = methodInfo.methodAnalysis.get().fieldSummaries.get(strings);
                Assert.assertFalse(transferValue.properties.isSet(VariableProperty.NOT_NULL));
                Assert.assertEquals(Level.TRUE, transferValue.properties.get(VariableProperty.READ));
                Assert.assertEquals(Level.DELAY, transferValue.properties.get(VariableProperty.ASSIGNED));
            }

            if ("setStrings2".equals(methodInfo.name)) {
                ParameterInfo strings2 = methodInfo.methodInspection.get().parameters.get(0);
                Assert.assertEquals("strings2param", strings2.name);
                if (iteration > 2) {
                    //   Assert.assertTrue(strings2.parameterAnalysis.get().assignedToField.isSet());
                }
            }
            if ("add2b".equals(methodInfo.name)) {
                FieldInfo strings = methodInfo.typeInfo.typeInspection.get().fields.get(0);
                Assert.assertEquals("strings2b", strings.name);
                TransferValue transferValue = methodInfo.methodAnalysis.get().fieldSummaries.get(strings);
                Assert.assertEquals(Level.DELAY, transferValue.properties.get(VariableProperty.ASSIGNED));
                Assert.assertEquals(Level.TRUE_LEVEL_1, transferValue.properties.get(VariableProperty.READ));
                Assert.assertFalse(transferValue.properties.isSet(VariableProperty.NOT_NULL));
            }
        }
    };

    FieldAnalyserVisitor fieldAnalyserVisitor = new FieldAnalyserVisitor() {
        @Override
        public void visit(int iteration, FieldInfo fieldInfo) {
            if ("strings1".equals(fieldInfo.name)) {
                if (iteration == 0) {
                    Assert.assertEquals(Level.DELAY, fieldInfo.fieldAnalysis.get().getProperty(VariableProperty.NOT_NULL));
                } else {
                    // setter may not have been called yet; there is no initialiser
                    Assert.assertEquals(Level.FALSE, fieldInfo.fieldAnalysis.get().getProperty(VariableProperty.NOT_NULL));
                }
            }
            if ("strings2".equals(fieldInfo.name)) {
                if (iteration >= 1) {
                    Assert.assertEquals(Level.FALSE, fieldInfo.fieldAnalysis.get().getProperty(VariableProperty.FINAL));
                }
            }
        }
    };

    TypeContextVisitor typeContextVisitor = new TypeContextVisitor() {
        @Override
        public void visit(TypeContext typeContext) {
            TypeInfo collection = typeContext.getFullyQualified(Collection.class);
            MethodInfo forEach = collection.typeInspection.get().methods.stream().filter(m -> "forEach".equals(m.name)).findAny().orElseThrow();
            Assert.assertSame(Primitives.PRIMITIVES.voidTypeInfo, forEach.returnType().typeInfo);

            TypeInfo hashSet = typeContext.getFullyQualified(HashSet.class);
            MethodInfo constructor1 = hashSet.typeInspection.get().constructors.stream()
                    .filter(m -> m.methodInspection.get().parameters.size() == 1)
                    .filter(m -> m.methodInspection.get().parameters.get(0).parameterizedType.typeInfo == collection)
                    .findAny().orElseThrow();
            ParameterInfo param1Constructor1 = constructor1.methodInspection.get().parameters.get(0);
            Assert.assertEquals(Level.FALSE, param1Constructor1.parameterAnalysis.get().getProperty(VariableProperty.MODIFIED));
        }
    };


    @Test
    public void test() throws IOException {
        testClass("ContainerChecks", 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addTypeContextVisitor(typeContextVisitor)
                .build());
    }

}
