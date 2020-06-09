package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Objects;

public class TestFinalNotNullChecks extends CommonTestRunner {
    public TestFinalNotNullChecks() {
        super(true);
    }

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor =
            (iteration, methodInfo, statementId, variableName, variable, currentValue, properties) -> {
                if ("toString".equals(methodInfo.name) && "FinalNotNullChecks.this.input".equals(variableName)) {
                    if (iteration >= 1) {
                        Assert.assertEquals(1, currentValue.getPropertyOutsideContext(VariableProperty.NOT_NULL));
                    }
                }
                if ("FinalNotNullChecks".equals(methodInfo.name) && "param".equals(variableName)) {
                    Assert.assertEquals(1, currentValue.getPropertyOutsideContext(VariableProperty.NOT_NULL));
                    int notNull = properties.getOrDefault(VariableProperty.NOT_NULL, Level.DELAY);
                    if (iteration == 0) {
                        // only during the 1st iteration there is no @NotNull on the parameter, so there is a restriction
                        Assert.assertEquals(1, (int) properties.get(VariableProperty.NOT_NULL));
                    }
                }
                // the variable has the value of param, which has received a @NotNull
                if ("FinalNotNullChecks".equals(methodInfo.name) && "FinalNotNullChecks.this.input".equals(variableName)) {
                    Assert.assertNull(properties.get(VariableProperty.NOT_NULL));
                }
            };

    TypeContextVisitor typeContextVisitor = new TypeContextVisitor() {
        @Override
        public void visit(TypeContext typeContext) {
            TypeInfo objects = typeContext.getFullyQualified(Objects.class);
            MethodInfo requireNonNull = objects.typeInspection.get().methods.stream().filter(mi -> mi.name.equals("requireNonNull") &&
                    1 == mi.methodInspection.get().parameters.size()).findFirst().orElseThrow();
            Assert.assertEquals(1, requireNonNull.methodAnalysis.get().getProperty(VariableProperty.NOT_NULL));
            Assert.assertEquals(1, requireNonNull.methodAnalysis.get().getProperty(VariableProperty.IDENTITY));
            ParameterInfo parameterInfo = requireNonNull.methodInspection.get().parameters.get(0);
            Assert.assertEquals(1, parameterInfo.parameterAnalysis.get().getProperty(VariableProperty.NOT_NULL));
        }
    };

    FieldAnalyserVisitor fieldAnalyserVisitor = new FieldAnalyserVisitor() {
        @Override
        public void visit(int iteration, FieldInfo fieldInfo) {
            if (iteration == 0 && "input".equals(fieldInfo.name)) {
                Assert.assertEquals(Level.DELAY, fieldInfo.fieldAnalysis.get().getProperty(VariableProperty.NOT_NULL));
            }
            if (iteration >= 2 && "input".equals(fieldInfo.name)) {
                Assert.assertEquals(Level.TRUE, fieldInfo.fieldAnalysis.get().getProperty(VariableProperty.NOT_NULL));
            }
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = new MethodAnalyserVisitor() {
        @Override
        public void visit(int iteration, MethodInfo methodInfo) {
            if (methodInfo.name.equals("FinalNotNullChecks")) {
                ParameterInfo parameterInfo = methodInfo.methodInspection.get().parameters.get(0);
                Assert.assertEquals(1, parameterInfo.parameterAnalysis.get().getProperty(VariableProperty.NOT_NULL));
            }
        }
    };

    @Test
    public void test() throws IOException {
        testClass("FinalNotNullChecks", 0, 0, new DebugConfiguration.Builder()
                .addTypeContextVisitor(typeContextVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

}
