package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.NumberedStatement;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.*;
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
        if ("add3".equals(methodInfo.name) && "local3".equals(variableName)) {
            if ("0".equals(statementId)) {
                if (iteration == 0) {
                    Assert.assertSame(UnknownValue.NO_VALUE, currentValue);
                } else {
                    Assert.assertTrue(currentValue instanceof FinalFieldValue);
                    FinalFieldValue variableValue = (FinalFieldValue) currentValue;
                    Assert.assertTrue(variableValue.variable instanceof FieldReference);
                    Assert.assertEquals("set3 (of this keyword (of org.e2immu.analyser.testexample.withannotatedapi.SimpleNotModifiedChecks.Example3))", currentValue.toString());
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
                    Assert.assertEquals("set4 (of this keyword (of org.e2immu.analyser.testexample.withannotatedapi.SimpleNotModifiedChecks.Example4))", currentValue.toString());
                }
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
                if (iteration == 2) {
                    Assert.assertEquals(1, fieldInfo.fieldAnalysis.get().variablesLinkedToMe.get().size());
                    Assert.assertEquals("in4", fieldInfo.fieldAnalysis.get().variablesLinkedToMe.get().stream().findFirst().orElseThrow().name());
                }
            }
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = new MethodAnalyserVisitor() {
        @Override
        public void visit(int iteration, MethodInfo methodInfo) {
            if ("Example4".equals(methodInfo.name)) {
                ParameterInfo in4 = methodInfo.methodInspection.get().parameters.get(0);
                if (iteration == 2) {
                    //       Assert.assertEquals(Level.FALSE, in4.parameterAnalysis.get().getProperty(VariableProperty.NOT_MODIFIED));
                }
            }
        }
    };

    TypeContextVisitor typeContextVisitor = new TypeContextVisitor() {
        @Override
        public void visit(TypeContext typeContext) {
            TypeInfo set = typeContext.getFullyQualified(Set.class);
            MethodInfo add = set.typeInspection.get().methods.stream().filter(mi -> mi.name.equals("add")).findFirst().orElseThrow();
            int notModified = add.methodAnalysis.get().getProperty(VariableProperty.NOT_MODIFIED);
            Assert.assertEquals(Level.FALSE, notModified);
        }
    };

    TypeAnalyserVisitor typeAnalyserVisitor = new TypeAnalyserVisitor() {
        @Override
        public void visit(int iteration, TypeInfo typeInfo) {
            if(iteration == 1 && "Example4".equals(typeInfo.simpleName)) {
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
