package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;
import org.e2immu.annotation.AnnotationMode;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

public class TestNotModifiedChecks extends CommonTestRunner {
    public TestNotModifiedChecks() {
        super(true);
    }

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = new StatementAnalyserVariableVisitor() {
        @Override
        public void visit(int iteration, MethodInfo methodInfo, String statementId, String variableName, Variable variable, Value currentValue, Map<VariableProperty, Integer> properties) {
            if ("addAll".equals(methodInfo.name) && "d".equals(variableName)) {
                Assert.assertEquals(0, (int) properties.get(VariableProperty.MODIFIED));
            }
            if ("addAll".equals(methodInfo.name) && "c".equals(variableName)) {
                Assert.assertEquals(1, (int) properties.get(VariableProperty.MODIFIED));
            }
            if ("addAllOnC".equals(methodInfo.name)) {
                if ("d".equals(variableName)) {
                    Assert.assertEquals(0, (int) properties.get(VariableProperty.MODIFIED));
                }
                if ("d.set".equals(variableName)) {
                    Assert.assertEquals(0, (int) properties.get(VariableProperty.MODIFIED));
                }
                if ("c.set".equals(variableName)) {
                    Assert.assertEquals(1, (int) properties.get(VariableProperty.MODIFIED));
                }
                if ("c".equals(variableName)) {
                    Assert.assertEquals(1, (int) properties.get(VariableProperty.MODIFIED));
                }
            }
            if ("NotModifiedChecks".equals(methodInfo.name)) {
                if ("list".equals(variableName)) {
                    if (iteration > 1) {
                        Assert.assertEquals(0, (int) properties.get(VariableProperty.MODIFIED));
                    }
                }
            }
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = new MethodAnalyserVisitor() {
        @Override
        public void visit(int iteration, MethodInfo methodInfo) {
            if ("NotModifiedChecks".equals(methodInfo.name)) {
                ParameterAnalysis list = methodInfo.methodInspection.get().parameters.get(0).parameterAnalysis.get();
                if (iteration == 0) {
                    Assert.assertFalse(list.assignedToField.isSet());
                } else {
                    Assert.assertTrue(list.assignedToField.isSet());
                }
            }
        }
    };

    FieldAnalyserVisitor fieldAnalyserVisitor = new FieldAnalyserVisitor() {
        @Override
        public void visit(int iteration, FieldInfo fieldInfo) {
            if ("c0".equals(fieldInfo.name)) {
                if (iteration > 0) {
                    Assert.assertEquals(0, fieldInfo.fieldAnalysis.get().getProperty(VariableProperty.MODIFIED));
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
