package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MethodAnalysis;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.abstractvalue.MethodValue;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;

public class TestUnusedLocalVariableChecks extends CommonTestRunner {
    public TestUnusedLocalVariableChecks() {
        super(true);
    }

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        // ERROR: t.trim() result is not used
        if ("method1".equals(d.methodInfo.name) && "2".equals(d.statementId) && d.iteration > 1) {
            Assert.assertTrue(d.statementAnalysis.errorFlags.errorValue.get());
        }

        if ("method2".equals(d.methodInfo.name) && d.iteration > 2) {
            if ("1".equals(d.statementAnalysis.index)) {
                Assert.assertTrue(d.statementAnalysis.errorFlags.errorValue.get()); // if switches
            }
            if ("1.0.0".equals(d.statementAnalysis.index)) {
                Assert.assertTrue(d.statementAnalysis.inErrorState());
                Assert.assertFalse(d.statementAnalysis.errorFlags.errorValue.isSet());
            }
        }

        if ("method3".equals(d.methodInfo.name)) {
            if ("1.0.0".equals(d.statementId)) {
                Assert.assertEquals("param.contains(a)", d.state.toString());

                if (d.iteration > 1) {
                    Value value = d.statementAnalysis.stateData.valueOfExpression.get();
                    Assert.assertEquals("xzy.toLowerCase()", value.toString());
                    Assert.assertTrue("Is " + value.getClass(), value instanceof MethodValue);
                }
            }
            if ("1.0.1".equals(d.statementAnalysis.index)) {
                if (d.iteration > 1) Assert.assertTrue(d.statementAnalysis.errorFlags.errorValue.get()); // if switches
            }
            if ("1.0.1.0.0".equals(d.statementAnalysis.index) && d.iteration > 1) {
                Assert.assertTrue(d.statementAnalysis.inErrorState());
                Assert.assertFalse(d.statementAnalysis.errorFlags.errorValue.isSet());
            }
        }
        // ERROR: Unused variable "a"
        // ERROR: useless assignment to "a" as well
        if ("UnusedLocalVariableChecks".equals(d.methodInfo.name) && "1".equals(d.statementId)) {
            Assert.assertEquals(1L,
                    d.statementAnalysis.errorFlags.unusedLocalVariables.stream().filter(Map.Entry::getValue).count());
            Assert.assertEquals("a", d.statementAnalysis.errorFlags.unusedLocalVariables.stream()
                    .findFirst().orElseThrow().getKey().name);

            Assert.assertEquals(1L,
                    d.statementAnalysis.errorFlags.uselessAssignments.stream().filter(Map.Entry::getValue).count());
            Assert.assertEquals("a", d.statementAnalysis.errorFlags.uselessAssignments.stream()
                    .findFirst().orElseThrow().getKey().name());
        }

        if ("method1".equals(d.methodInfo.name) && "2".equals(d.statementId)) {
            // ERROR: unused variable "s"
            Assert.assertEquals(1L,
                    d.statementAnalysis.errorFlags.unusedLocalVariables.stream().filter(Map.Entry::getValue).count());
            Assert.assertEquals("s", d.statementAnalysis.errorFlags.unusedLocalVariables.stream()
                    .findFirst().orElseThrow().getKey().name);

            // but NO useless assignment anymore
            Assert.assertEquals(0L,
                    d.statementAnalysis.errorFlags.uselessAssignments.stream().filter(Map.Entry::getValue).count());

            // ERROR: method should be static
            Assert.assertTrue(d.methodInfo.methodAnalysis.get().complainedAboutMissingStaticModifier.get());
        }
        if ("checkArray2".equals(d.methodInfo.name) && "2".equals(d.statementId)) {
            Assert.assertEquals(1L, d.statementAnalysis.errorFlags.uselessAssignments.stream().filter(Map.Entry::getValue).count());
        }
        if ("checkForEach".equals(d.methodInfo.name) && "1.0.0".equals(d.statementId)) {
            Assert.assertEquals(1L,
                    d.statementAnalysis.errorFlags.unusedLocalVariables.stream().filter(Map.Entry::getValue).count());
            Assert.assertEquals("loopVar", d.statementAnalysis.errorFlags.unusedLocalVariables.stream()
                    .findFirst().orElseThrow().getKey().name);
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

        if ("method1".equals(methodInfo.name)) {
            // ERROR: method should be static
            Assert.assertTrue(methodAnalysis.complainedAboutMissingStaticModifier.get());
        }
    };

    FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
        // ERROR: b is never read
        if ("b".equals(d.fieldInfo().name) && d.iteration() >= 1) {
            Assert.assertTrue(d.fieldAnalysis().fieldError.get());
        }
    };

    @Test
    public void test() throws IOException {
        testClass("UnusedLocalVariableChecks", 9, 1, new DebugConfiguration.Builder()
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().setSkipTransformations(true).build());
    }

}
