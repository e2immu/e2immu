package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.NumberedStatement;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.e2immu.analyser.model.abstractvalue.VariableValue;
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
                    Assert.assertTrue(currentValue instanceof VariableValue);
                    VariableValue variableValue = (VariableValue) currentValue;
                    Assert.assertTrue(variableValue.variable instanceof FieldReference);
                    Assert.assertEquals("Example3.this.set3", currentValue.toString());
                }
            }
        }
        if ("add4".equals(methodInfo.name) && "local4".equals(variableName) && "1".equals(statementId)) {
            if (1 == iteration) {
                //  Assert.assertNull(properties.get(VariableProperty.NOT_NULL));
            }
        }
    };

    StatementAnalyserVisitor statementAnalyserVisitor = new StatementAnalyserVisitor() {
        @Override
        public void visit(int iteration, MethodInfo methodInfo, NumberedStatement numberedStatement, Value conditional) {
            if ("add4".equals(methodInfo.name) && "1".equals(numberedStatement.streamIndices())) {
                //    Assert.assertFalse(numberedStatement.errorValue.isSet()); // no potential null pointer exception
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
                    //    Assert.assertEquals(1, fieldInfo.fieldAnalysis.get().variablesLinkedToMe.get().size());
                    //     Assert.assertEquals("in4", fieldInfo.fieldAnalysis.get().variablesLinkedToMe.get().stream().findFirst().orElseThrow().name());
                    Assert.assertTrue(fieldInfo.fieldAnalysis.get().effectivelyFinalValue.isSet());
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

    @Test
    public void test() throws IOException {
        testClass("SimpleNotModifiedChecks", 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addTypeContextVisitor(typeContextVisitor)
                .build());
    }

}
