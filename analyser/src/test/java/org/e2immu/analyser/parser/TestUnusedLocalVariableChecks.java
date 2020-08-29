package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MethodAnalysis;
import org.e2immu.analyser.model.MultiLevel;
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

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        // ERROR: t.trim() result is not used
        if ("method1".equals(d.methodInfo.name) && "2".equals(d.statementId)) {
            Assert.assertTrue(d.numberedStatement.errorValue.get());
        }

        if ("method2".equals(d.methodInfo.name)) {
            if ("1".equals(d.numberedStatement.streamIndices())) {
                Assert.assertTrue(d.numberedStatement.errorValue.get()); // if switches
            }
            if ("1.0.0".equals(d.numberedStatement.streamIndices())) {
                Assert.assertTrue(d.numberedStatement.inErrorState());
                Assert.assertFalse(d.numberedStatement.errorValue.isSet());
            }
        }

        if ("method3".equals(d.methodInfo.name)) {
            if ("1.0.1".equals(d.numberedStatement.streamIndices())) {
                Assert.assertTrue(d.numberedStatement.errorValue.get()); // if switches
            }
            if ("1.0.1.0.0".equals(d.numberedStatement.streamIndices())) {
                Assert.assertTrue(d.numberedStatement.inErrorState());
                Assert.assertFalse(d.numberedStatement.errorValue.isSet());
            }
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

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("checkArray2".equals(d.methodInfo.name) && "0".equals(d.statementId)) {
            if ("integers".equals(d.variableName)) {
                Assert.assertEquals(Level.READ_ASSIGN_ONCE, (int) d.properties.get(VariableProperty.ASSIGNED)); // integers=, and integers[i]=
                Assert.assertNull(d.properties.get(VariableProperty.READ));
                Assert.assertEquals(Level.TRUE, (int) d.properties.get(VariableProperty.NOT_YET_READ_AFTER_ASSIGNMENT));
            }
        }
        if ("checkArray2".equals(d.methodInfo.name) && "2".equals(d.statementId)) {
            if ("integers".equals(d.variableName)) {
                Assert.assertEquals(Level.READ_ASSIGN_ONCE, (int) d.properties.get(VariableProperty.ASSIGNED)); // integers=, NOT integers[i]=
                Assert.assertEquals(Level.READ_ASSIGN_ONCE, (int) d.properties.get(VariableProperty.READ));
                Assert.assertNull(d.properties.get(VariableProperty.NOT_YET_READ_AFTER_ASSIGNMENT));
                Assert.assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL, d.currentValue.getPropertyOutsideContext(VariableProperty.NOT_NULL)); // because in scope side
            } else if ("i".equals(d.variableName)) {
                Assert.assertEquals(Level.READ_ASSIGN_ONCE, (int) d.properties.get(VariableProperty.READ));
                Assert.assertEquals(Level.READ_ASSIGN_ONCE, (int) d.properties.get(VariableProperty.ASSIGNED));

                // the standardized name is the evaluation value of expression and index, in this particular case, both constants
            } else if ("{1,2,3}[0]".equals(d.variableName)) {
                Assert.assertEquals(Level.TRUE, (int) d.properties.get(VariableProperty.NOT_YET_READ_AFTER_ASSIGNMENT));
                Assert.assertEquals(Level.READ_ASSIGN_ONCE, (int) d.properties.get(VariableProperty.ASSIGNED));
            } else Assert.fail();
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = (iteration, methodInfo) -> {
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();

        // ERROR: Unused variable "a"
        // ERROR: useless assignment to "a" as well
        if ("UnusedLocalVariableChecks".equals(methodInfo.name)) {
            Assert.assertEquals(1L,
                    methodAnalysis.unusedLocalVariables.stream().filter(Map.Entry::getValue).count());
            Assert.assertEquals("a", methodAnalysis.unusedLocalVariables.stream()
                    .findFirst().orElseThrow().getKey().name);

            Assert.assertEquals(1L,
                    methodAnalysis.uselessAssignments.stream().filter(Map.Entry::getValue).count());
            Assert.assertEquals("a", methodAnalysis.uselessAssignments.stream()
                    .findFirst().orElseThrow().getKey().name());
        }

        if ("method1".equals(methodInfo.name)) {
            // ERROR: unused variable "s"
            Assert.assertEquals(1L,
                    methodAnalysis.unusedLocalVariables.stream().filter(Map.Entry::getValue).count());
            Assert.assertEquals("s", methodAnalysis.unusedLocalVariables.stream()
                    .findFirst().orElseThrow().getKey().name);

            // but NO useless assignment anymore
            Assert.assertEquals(0L,
                    methodAnalysis.uselessAssignments.stream().filter(Map.Entry::getValue).count());

            // ERROR: method should be static
            Assert.assertTrue(methodAnalysis.complainedAboutMissingStaticModifier.get());
        }
        if ("checkArray2".equals(methodInfo.name)) {
            Assert.assertEquals(1L, methodAnalysis.uselessAssignments.stream().filter(Map.Entry::getValue).count());
        }
        if ("checkForEach".equals(methodInfo.name)) {
            Assert.assertEquals(1L,
                    methodAnalysis.unusedLocalVariables.stream().filter(Map.Entry::getValue).count());
            Assert.assertEquals("loopVar", methodAnalysis.unusedLocalVariables.stream()
                    .findFirst().orElseThrow().getKey().name);
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
        testClass("UnusedLocalVariableChecks", 9, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }

}
