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
            Assert.assertNotNull(d.haveError(Message.IGNORING_RESULT_OF_METHOD_CALL));
        }

        if ("method2".equals(d.methodInfo.name) && d.iteration > 2) {
            if ("1".equals(d.statementAnalysis.index)) {
                Assert.assertNotNull(d.haveError(Message.CONDITION_EVALUATES_TO_CONSTANT));
            }
            if ("1.0.0".equals(d.statementAnalysis.index)) {
                Assert.assertNull(d.haveError(Message.CONDITION_EVALUATES_TO_CONSTANT));
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
                if (d.iteration > 1) {
                    Assert.assertNotNull(d.haveError(Message.CONDITION_EVALUATES_TO_CONSTANT));
                }
            }
            if ("1.0.1.0.0".equals(d.statementAnalysis.index) && d.iteration > 1) {
                Assert.assertNull(d.haveError(Message.CONDITION_EVALUATES_TO_CONSTANT));
            }
        }
        // ERROR: Unused variable "a"
        // ERROR: useless assignment to "a" as well
        if ("UnusedLocalVariableChecks".equals(d.methodInfo.name) && "1".equals(d.statementId)) {
            Assert.assertEquals("a", d.haveError(Message.UNUSED_LOCAL_VARIABLE));
            Assert.assertEquals("a", d.haveError(Message.USELESS_ASSIGNMENT));
        }

        if ("method1".equals(d.methodInfo.name) && "2".equals(d.statementId)) {
            // ERROR: unused variable "s"
            Assert.assertEquals("s", d.haveError(Message.UNUSED_LOCAL_VARIABLE));
            Assert.assertNull(d.haveError(Message.USELESS_ASSIGNMENT));

            // ERROR: method should be static
            Assert.assertEquals("s", d.haveError(Message.METHOD_SHOULD_BE_MARKED_STATIC));
        }
        if ("checkArray2".equals(d.methodInfo.name) && "2".equals(d.statementId)) {
            Assert.assertEquals("s", d.haveError(Message.USELESS_ASSIGNMENT));
        }
        if ("checkForEach".equals(d.methodInfo.name) && "1.0.0".equals(d.statementId)) {
            Assert.assertEquals("s", d.haveError(Message.UNUSED_LOCAL_VARIABLE));
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
        int read = d.properties.getOrDefault(VariableProperty.READ, Level.DELAY);
        int assigned = d.properties.getOrDefault(VariableProperty.ASSIGNED, Level.DELAY);

        if ("checkArray2".equals(d.methodInfo.name) && "0".equals(d.statementId)) {
            if ("integers".equals(d.variableName)) {
                Assert.assertEquals(Level.READ_ASSIGN_ONCE, assigned); // integers=, and integers[i]=
                Assert.assertEquals(Level.DELAY, read);
            }
        }
        if ("checkArray2".equals(d.methodInfo.name) && "2".equals(d.statementId)) {
            if ("integers".equals(d.variableName)) {
                Assert.assertEquals(Level.READ_ASSIGN_ONCE, assigned); // integers=, NOT integers[i]=
                Assert.assertEquals(assigned + 1, read);
                Assert.assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL, d.currentValue.getPropertyOutsideContext(VariableProperty.NOT_NULL)); // because in scope side
            } else if ("i".equals(d.variableName)) {
                Assert.assertEquals(Level.READ_ASSIGN_ONCE, assigned);
                Assert.assertEquals(assigned + 1, read);

                // the standardized name is the evaluation value of expression and index, in this particular case, both constants
            } else if ("{1,2,3}[0]".equals(d.variableName)) {
                Assert.assertEquals(Level.READ_ASSIGN_ONCE, assigned);
                Assert.assertTrue(read <= assigned);
            } else Assert.fail();
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        MethodAnalysis methodAnalysis = d.methodAnalysis();

        if ("method1".equals(d.methodInfo().name)) {
            // ERROR: method should be static
            Assert.assertTrue(methodAnalysis.getComplainedAboutMissingStaticModifier());
        }
    };

    FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
        // ERROR: b is never read
        if ("b".equals(d.fieldInfo().name) && d.iteration() >= 1) {
            Assert.assertTrue(d.fieldAnalysis().getFieldError());
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
