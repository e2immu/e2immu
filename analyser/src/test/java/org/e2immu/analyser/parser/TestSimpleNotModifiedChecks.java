package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.NumberedStatement;
import org.e2immu.analyser.analyser.TransferValue;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.*;
import org.e2immu.annotation.AnnotationMode;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Set;

public class TestSimpleNotModifiedChecks extends CommonTestRunner {
    public TestSimpleNotModifiedChecks() {
        super(true);
    }

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = (iteration, methodInfo, statementId, variableName,
                                                                         variable, currentValue, properties) -> {

        if ("size".equals(methodInfo.name) && "Example2".equals(methodInfo.typeInfo.simpleName)) {
            Assert.assertEquals(0, (int) properties.get(VariableProperty.MODIFIED));
        }

        if ("add3".equals(methodInfo.name) && "local3".equals(variableName)) {
            if ("0".equals(statementId)) {
                if (iteration == 0) {
                    Assert.assertSame(UnknownValue.NO_VALUE, currentValue);
                } else {
                    Assert.assertTrue(currentValue instanceof FinalFieldValue);
                    FinalFieldValue variableValue = (FinalFieldValue) currentValue;
                    Assert.assertTrue(variableValue.variable instanceof FieldReference);
                    Assert.assertEquals("set3", currentValue.toString());
                }
            }
        }
        if ("add4".equals(methodInfo.name) && "local4".equals(variableName)) {
            if ("0".equals(statementId)) {
                if (iteration == 0) {
                    Assert.assertSame(UnknownValue.NO_VALUE, currentValue);
                } else {
                    Assert.assertTrue(currentValue instanceof FinalFieldValue);
                    FinalFieldValue variableValue = (FinalFieldValue) currentValue;
                    Assert.assertTrue(variableValue.variable instanceof FieldReference);
                    Assert.assertEquals("set4", currentValue.toString());
                }
            }
            if ("1".equals(statementId) && iteration > 0) {
                Assert.assertEquals(Level.TRUE, (int) properties.get(VariableProperty.NOT_NULL));
            }
        }
        if ("add4".equals(methodInfo.name) && "Example4.this.set4".equals(variableName)) {
            if ("1".equals(statementId)) {
                if (iteration == 0) {
                    Assert.assertSame(UnknownValue.NO_VALUE, currentValue);
                } else {
                    Assert.assertEquals(Level.TRUE, (int) properties.get(VariableProperty.NOT_NULL));
                    Assert.assertTrue(currentValue instanceof FinalFieldValue);
                }
            }
        }
        if ("Example4".equals(methodInfo.name) && "set4".equals(variableName) && "0".equals(statementId)) {
            if (iteration == 3) {
                Assert.assertEquals(Level.TRUE, (int) properties.get(VariableProperty.MODIFIED));
                Assert.assertEquals(Level.TRUE, (int) properties.get(VariableProperty.NOT_NULL));
            }
        }

        if ("Example5".equals(methodInfo.name) && "in5".equals(variableName) && "0".equals(statementId)) {
            Assert.assertEquals(Level.FALSE, (int) properties.get(VariableProperty.MODIFIED));
        }

        if ("Example5".equals(methodInfo.name) && "Example5.this.set5".equals(variableName) && "0".equals(statementId)) {
            if (iteration == 0) {
                Assert.assertTrue(currentValue instanceof Instance);
            } else {
                Assert.assertTrue(currentValue instanceof FinalFieldValue);
            }
        }

        if ("add6".equals(methodInfo.name) && "values6".equals(variableName)) {
            Assert.assertEquals(Level.FALSE, (int) properties.get(VariableProperty.MODIFIED));
            Assert.assertEquals(Level.TRUE, (int) properties.get(VariableProperty.NOT_NULL));
        }
        if ("add6".equals(methodInfo.name) && "example6".equals(variableName)) {
            Assert.assertEquals(Level.TRUE, (int) properties.get(VariableProperty.NOT_NULL));
        }
        if ("add6".equals(methodInfo.name) && "example6.set6".equals(variableName)) {
            Assert.assertEquals(Level.TRUE, (int) properties.get(VariableProperty.MODIFIED));
            if (iteration > 1) Assert.assertEquals(Level.TRUE, (int) properties.get(VariableProperty.NOT_NULL));
        }
        if ("Example6".equals(methodInfo.name) && "set6".equals(variableName) && "0".equals(statementId)) {
            if (iteration == 3) {
                Assert.assertEquals(Level.TRUE, (int) properties.get(VariableProperty.MODIFIED));
                Assert.assertEquals(Level.TRUE, (int) properties.get(VariableProperty.NOT_NULL));
            }
        }
        if ("Example6".equals(methodInfo.name) && "in6".equals(variableName) && "0".equals(statementId)) {
            if (iteration == 0) {
                Assert.assertNull(properties.get(VariableProperty.MODIFIED));
            }
        }
    };

    StatementAnalyserVisitor statementAnalyserVisitor = new StatementAnalyserVisitor() {
        @Override
        public void visit(int iteration, MethodInfo methodInfo, NumberedStatement numberedStatement, Value conditional) {
            if ("add4".equals(methodInfo.name) && "1".equals(numberedStatement.streamIndices())) {
                Assert.assertFalse(numberedStatement.errorValue.isSet()); // no potential null pointer exception
            }
        }
    };

    FieldAnalyserVisitor fieldAnalyserVisitor = new FieldAnalyserVisitor() {
        @Override
        public void visit(int iteration, FieldInfo fieldInfo) {
            if (fieldInfo.name.equals("set2")) {
                if (iteration == 0 || iteration == 1) {
                    Assert.assertEquals(Level.DELAY, fieldInfo.fieldAnalysis.get().getProperty(VariableProperty.MODIFIED));
                } else {
                    Assert.assertEquals(Level.FALSE, fieldInfo.fieldAnalysis.get().getProperty(VariableProperty.MODIFIED));
                }
            }
            if (fieldInfo.name.equals("set3")) {
                if (iteration == 0) {
                    Assert.assertEquals(Level.DELAY, fieldInfo.fieldAnalysis.get().getProperty(VariableProperty.FINAL));
                }
                if (iteration == 1) {
                    Assert.assertEquals(Level.TRUE, fieldInfo.fieldAnalysis.get().getProperty(VariableProperty.FINAL));
                    Assert.assertTrue(fieldInfo.fieldAnalysis.get().effectivelyFinalValue.isSet());
                }
            }
            if (fieldInfo.name.equals("set4")) {
                if (iteration == 0) {
                    Assert.assertEquals(Level.DELAY, fieldInfo.fieldAnalysis.get().getProperty(VariableProperty.FINAL));
                }
                if (iteration == 1) {
                    Assert.assertEquals(Level.TRUE, fieldInfo.fieldAnalysis.get().getProperty(VariableProperty.FINAL));
                    Assert.assertTrue(fieldInfo.fieldAnalysis.get().effectivelyFinalValue.isSet());
                    Assert.assertFalse(fieldInfo.fieldAnalysis.get().variablesLinkedToMe.isSet());
                }
                if (iteration >= 2) {
                    Assert.assertEquals(1, fieldInfo.fieldAnalysis.get().variablesLinkedToMe.get().size());
                    Assert.assertEquals("in4", fieldInfo.fieldAnalysis.get().variablesLinkedToMe.get().stream().findFirst().orElseThrow().name());
                    Assert.assertEquals(Level.TRUE, fieldInfo.fieldAnalysis.get().getProperty(VariableProperty.MODIFIED));
                    Assert.assertEquals(Level.TRUE, fieldInfo.fieldAnalysis.get().getProperty(VariableProperty.NOT_NULL));
                }
            }
            if (fieldInfo.name.equals("set6")) {
                if (iteration == 0) {
                    Assert.assertEquals(Level.DELAY, fieldInfo.fieldAnalysis.get().getProperty(VariableProperty.FINAL));
                }
                if (iteration == 1) {
                    Assert.assertEquals(Level.TRUE, fieldInfo.fieldAnalysis.get().getProperty(VariableProperty.FINAL));
                    Assert.assertTrue(fieldInfo.fieldAnalysis.get().effectivelyFinalValue.isSet());
                    Assert.assertFalse(fieldInfo.fieldAnalysis.get().variablesLinkedToMe.isSet());
                }
                if (iteration >= 2) {
                    Assert.assertEquals("in6", fieldInfo.fieldAnalysis.get().variablesLinkedToMe.get().stream().findFirst().orElseThrow().name());
                    Assert.assertEquals(Level.TRUE, fieldInfo.fieldAnalysis.get().getProperty(VariableProperty.NOT_NULL));
                    Assert.assertEquals(Level.TRUE, fieldInfo.fieldAnalysis.get().getProperty(VariableProperty.MODIFIED));
                }
            }
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = new MethodAnalyserVisitor() {
        @Override
        public void visit(int iteration, MethodInfo methodInfo) {
            if ("size".equals(methodInfo.name) && "Example2".equals(methodInfo.typeInfo.simpleName)) {
                if (iteration > 0) {
                    FieldInfo set2 = methodInfo.typeInfo.typeInspection.get().fields.get(0);
                    Assert.assertEquals("set2", set2.name);
                    TransferValue tv = methodInfo.methodAnalysis.get().fieldSummaries.get(set2);
                    Assert.assertEquals(0, tv.properties.get(VariableProperty.MODIFIED));
                }
                if (iteration > 1) {
                    Assert.assertEquals(0, methodInfo.methodAnalysis.get().getProperty(VariableProperty.MODIFIED));
                }
            }
            if ("Example4".equals(methodInfo.name)) {
                ParameterInfo in4 = methodInfo.methodInspection.get().parameters.get(0);
                if (iteration >= 2) {
                    Assert.assertEquals(Level.TRUE, in4.parameterAnalysis.get().getProperty(VariableProperty.NOT_NULL));
                    Assert.assertEquals(Level.TRUE, in4.parameterAnalysis.get().getProperty(VariableProperty.MODIFIED));
                }
            }
            if ("add4".equals(methodInfo.name)) {
                if (iteration >= 1) {
                    FieldInfo set4 = methodInfo.typeInfo.typeInspection.get().fields.stream().filter(f -> f.name.equals("set4")).findAny().orElseThrow();
                    Assert.assertEquals(Level.TRUE, methodInfo.methodAnalysis.get().fieldSummaries.get(set4).properties.get(VariableProperty.NOT_NULL));
                }
                if (iteration >= 2) {
                    Assert.assertEquals(Level.TRUE, methodInfo.methodAnalysis.get().getProperty(VariableProperty.MODIFIED));
                }
            }
            if ("Example6".equals(methodInfo.name)) {
                ParameterInfo in6 = methodInfo.methodInspection.get().parameters.get(0);
                if (iteration == 0 || iteration == 1) {
                    Assert.assertEquals(Level.DELAY, in6.parameterAnalysis.get().getProperty(VariableProperty.NOT_NULL));
                    Assert.assertEquals(Level.DELAY, in6.parameterAnalysis.get().getProperty(VariableProperty.MODIFIED));
                } else {
                    Assert.assertEquals(Level.TRUE, in6.parameterAnalysis.get().getProperty(VariableProperty.NOT_NULL));
                    Assert.assertEquals(Level.TRUE, in6.parameterAnalysis.get().getProperty(VariableProperty.MODIFIED));
                }
            }
            if ("add6".equals(methodInfo.name)) {
                FieldInfo set6 = methodInfo.typeInfo.typeInspection.get().fields.stream().filter(f -> f.name.equals("set6")).findAny().orElseThrow();

                if (iteration >= 2) {
                    Assert.assertEquals(Level.TRUE, methodInfo.methodAnalysis.get().fieldSummaries.get(set6).properties.get(VariableProperty.NOT_NULL));
                    Assert.assertEquals(Level.TRUE, methodInfo.methodAnalysis.get().getProperty(VariableProperty.MODIFIED));
                }
            }
        }
    };

    TypeContextVisitor typeContextVisitor = new TypeContextVisitor() {
        @Override
        public void visit(TypeContext typeContext) {
            TypeInfo set = typeContext.getFullyQualified(Set.class);
            Assert.assertEquals(AnnotationMode.DEFENSIVE, set.typeInspection.get().annotationMode);
            MethodInfo add = set.typeInspection.get().methods.stream().filter(mi -> mi.name.equals("add")).findFirst().orElseThrow();
            Assert.assertFalse(add.methodAnalysis.get().hasBeenDefined);
            Assert.assertEquals(Level.TRUE, add.methodAnalysis.get().getProperty(VariableProperty.MODIFIED));

            MethodInfo addAll = set.typeInspection.get().methods.stream().filter(mi -> mi.name.equals("addAll")).findFirst().orElseThrow();
            Assert.assertEquals(Level.TRUE, addAll.methodAnalysis.get().getProperty(VariableProperty.MODIFIED));
            ParameterInfo first = addAll.methodInspection.get().parameters.get(0);
            Assert.assertEquals(Level.FALSE, first.parameterAnalysis.get().getProperty(VariableProperty.MODIFIED));

            MethodInfo size = set.typeInspection.get().methods.stream().filter(mi -> mi.name.equals("size")).findFirst().orElseThrow();
            Assert.assertEquals(Level.FALSE, size.methodAnalysis.get().getProperty(VariableProperty.MODIFIED));

            TypeInfo hashSet = typeContext.getFullyQualified(Set.class);
            Assert.assertEquals(Level.TRUE, hashSet.typeAnalysis.get().getProperty(VariableProperty.CONTAINER));
        }
    };

    TypeAnalyserVisitor typeAnalyserVisitor = new TypeAnalyserVisitor() {
        @Override
        public void visit(int iteration, TypeInfo typeInfo) {
            if (iteration == 1 && "Example4".equals(typeInfo.simpleName)) {
                int immutable = typeInfo.typeAnalysis.get().getProperty(VariableProperty.IMMUTABLE);
                Assert.assertEquals(Level.TRUE, Level.value(immutable, Level.E1IMMUTABLE));
                Assert.assertEquals(Level.DELAY, Level.value(immutable, Level.E2IMMUTABLE));
            }
        }
    };

    @Test
    public void test() throws IOException {
        testClass("SimpleNotModifiedChecks", 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addTypeContextVisitor(typeContextVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .build());
    }

}
