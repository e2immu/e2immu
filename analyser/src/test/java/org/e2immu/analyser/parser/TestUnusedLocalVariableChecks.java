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

public class TestUnusedLocalVariableChecks extends CommonTestRunner {
    public TestUnusedLocalVariableChecks() {
        super(true);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(TestUnusedLocalVariableChecks.class);

    StatementAnalyserVisitor statementAnalyserVisitor = (iteration, methodInfo, statement) -> {
        // ERROR: t.trim() result is not used
        if ("method1".equals(methodInfo.name) && "2".equals(statement.streamIndices())) {
            Assert.assertTrue(statement.errorValue.get());
        }
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
            if ("checkArray2".equals(methodInfo.name) && "0".equals(statementId)) {
                if ("integers".equals(variableName)) {
                    Assert.assertEquals(1, (int) properties.get(VariableProperty.ASSIGNED)); // integers=, and integers[i]=
                    Assert.assertNull(properties.get(VariableProperty.READ));
                    Assert.assertEquals(1, (int) properties.get(VariableProperty.NOT_YET_READ_AFTER_ASSIGNMENT));
                }
            }
            if ("checkArray2".equals(methodInfo.name) && "2".equals(statementId)) {
                if ("integers".equals(variableName)) {
                    Assert.assertEquals(1, (int) properties.get(VariableProperty.ASSIGNED)); // integers=, NOT integers[i]=
                    Assert.assertEquals(1, (int) properties.get(VariableProperty.READ));
                    Assert.assertNull(properties.get(VariableProperty.NOT_YET_READ_AFTER_ASSIGNMENT));
                    Assert.assertEquals(3, (int) properties.get(VariableProperty.IN_NOT_NULL_CONTEXT)); // because in scope side
                } else if ("i".equals(variableName)) {
                    Assert.assertEquals(1, (int) properties.get(VariableProperty.READ));
                    Assert.assertEquals(1, (int) properties.get(VariableProperty.CREATED));
                    Assert.assertEquals(1, (int) properties.get(VariableProperty.ASSIGNED));

                    // the standardized name is the evaluation value of expression and index, in this particular case, both constants
                } else if ("{1,2,3}[0]".equals(variableName)) {
                    Assert.assertEquals(1, (int) properties.get(VariableProperty.NOT_YET_READ_AFTER_ASSIGNMENT));
                    Assert.assertNull(properties.get(VariableProperty.CREATED));
                    Assert.assertEquals(1, (int) properties.get(VariableProperty.ASSIGNED));
                } else Assert.fail();
            }
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = (iteration, methodInfo) -> {
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();

        // ERROR: Unused variable "a"
        if ("UnusedLocalVariableChecks".equals(methodInfo.name)) {
            Assert.assertEquals(1L,
                    methodAnalysis.unusedLocalVariables.stream().filter(Map.Entry::getValue).count());
            Assert.assertEquals("a", methodAnalysis.unusedLocalVariables.stream()
                    .findFirst().orElseThrow().getKey().name);
        }

        if ("method1".equals(methodInfo.name)) {
            // ERROR: unused variable "s"
            Assert.assertEquals(1L,
                    methodAnalysis.unusedLocalVariables.stream().filter(Map.Entry::getValue).count());
            Assert.assertEquals("s", methodAnalysis.unusedLocalVariables.stream()
                    .findFirst().orElseThrow().getKey().name);

            // ERROR: method should be static
            Assert.assertTrue(methodAnalysis.complainedAboutMissingStaticStatement.get());

        }

        if ("checkArray2".equals(methodInfo.name)) {
            Assert.assertEquals(1L, methodAnalysis.uselessAssignments.stream().filter(Map.Entry::getValue).count());
        }
    };

    FieldAnalyserVisitor fieldAnalyserVisitor = (iteration, fieldInfo) -> {
        // ERROR: b is never read
        if ("b".equals(fieldInfo.name) && iteration >= 1) {
            Assert.assertTrue(fieldInfo.fieldAnalysis.get().fieldError.get());
        }
    };

    @Test
    public void test() throws IOException {
        testClass("UnusedLocalVariableChecks", 6, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }

}
