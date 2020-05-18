package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

public class TestDependentVariables extends CommonTestRunner {
    public TestDependentVariables() {
        super(false);
    }

    StatementAnalyserVisitor statementAnalyserVisitor = (iteration, methodInfo, statement) -> {

    };

    /*
     private static void checkArray2() {
        int[] integers = {1, 2, 3};
        int i = 0;
        integers[i] = 3;
        // ERROR: assignment is not used
    }
     */

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = new StatementAnalyserVariableVisitor() {
        @Override
        public void visit(int iteration, MethodInfo methodInfo, String statementId, String variableName, Variable variable, Value currentValue, Map<VariableProperty, Integer> properties) {
            if ("method1".equals(methodInfo.name)) {
                if ("2".equals(statementId) && "array[0]".equals(variableName)) {
                    Assert.assertEquals(Level.TRUE, (int) properties.get(VariableProperty.NOT_YET_READ_AFTER_ASSIGNMENT));
                }
                if ("4".equals(statementId) && "array[0]".equals(variableName)) {
                    Assert.assertNull(properties.get(VariableProperty.NOT_YET_READ_AFTER_ASSIGNMENT));
                    Assert.assertEquals(Level.TRUE, (int) properties.get(VariableProperty.READ));
                }
                if ("4".equals(statementId) && "array".equals(variableName)) {
                    Assert.assertEquals(VariableProperty.READ.best, (int) properties.get(VariableProperty.READ));
                }
            }
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = (iteration, methodInfo) -> {
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();


    };

    FieldAnalyserVisitor fieldAnalyserVisitor = (iteration, fieldInfo) -> {

    };

    @Test
    public void test() throws IOException {
        testClass("DependentVariables", 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }

}
