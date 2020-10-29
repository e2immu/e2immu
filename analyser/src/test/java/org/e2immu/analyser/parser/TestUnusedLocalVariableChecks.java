package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.StatementAnalyser;
import org.e2immu.analyser.analyser.TransferValue;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.MethodValue;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class TestUnusedLocalVariableChecks extends CommonTestRunner {
    private static final String PARAM_3_TO_LOWER = "org.e2immu.analyser.testexample.UnusedLocalVariableChecks.method3(String):0:param.toLowerCase()";

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
                String expectState = "org.e2immu.analyser.testexample.UnusedLocalVariableChecks.method3(String):0:param.contains(a)";
                Assert.assertEquals(expectState, d.state.toString());

                if (d.iteration > 1) {
                    Value value = d.statementAnalysis.stateData.valueOfExpression.get();
                    Assert.assertEquals("xzy.toLowerCase()", value.toString());
                    Assert.assertTrue("Is " + value.getClass(), value instanceof MethodValue);
                }
            }
            if ("1.0.1".equals(d.statementAnalysis.index)) {
                String expectStateString = d.iteration <= 2 ? UnknownValue.NO_VALUE.toString() : "";
                Assert.assertEquals(expectStateString, d.state.toString());
                if (d.iteration >= 3) {
                    Assert.assertNotNull(d.haveError(Message.CONDITION_EVALUATES_TO_CONSTANT));
                    mark("E1");
                }
            }
            if ("1.0.1.0.0".equals(d.statementAnalysis.index) && d.iteration > 1) {
                Assert.assertNull(d.haveError(Message.CONDITION_EVALUATES_TO_CONSTANT));
            }
        }
        // ERROR: Unused variable "a"
        // ERROR: useless assignment to "a" as well
        if ("UnusedLocalVariableChecks".equals(d.methodInfo.name) && "1".equals(d.statementId)) {
            Assert.assertEquals("ERROR in M:UnusedLocalVariableChecks:1: Unused local variable: a", d.haveError(Message.UNUSED_LOCAL_VARIABLE));
            Assert.assertEquals("ERROR in M:UnusedLocalVariableChecks:1: Useless assignment: a", d.haveError(Message.USELESS_ASSIGNMENT));
        }

        if ("method1".equals(d.methodInfo.name) && "2".equals(d.statementId)) {
            // ERROR: unused variable "s"
            Assert.assertEquals("ERROR in M:method1:2: Unused local variable: s", d.haveError(Message.UNUSED_LOCAL_VARIABLE));
            Assert.assertNull(d.haveError(Message.USELESS_ASSIGNMENT));
        }
        if ("checkArray2".equals(d.methodInfo.name) && "2".equals(d.statementId)) {
            Assert.assertEquals("ERROR in M:checkArray2:2: Useless assignment: integers[i]", d.haveError(Message.USELESS_ASSIGNMENT));
        }
        if ("checkForEach".equals(d.methodInfo.name) && "1.0.0".equals(d.statementId)) {
            Assert.assertEquals("ERROR in M:checkForEach:1.0.0: Unused local variable: loopVar", d.haveError(Message.UNUSED_LOCAL_VARIABLE));
        }
        if ("someMethod".equals(d.methodInfo.name)) {
            TransferValue tv = d.statementAnalysis.methodLevelData.returnStatementSummaries.get("0");
            Assert.assertEquals("org.e2immu.analyser.testexample.UnusedLocalVariableChecks.someMethod(String):0:a.toLowerCase()",
                    tv.value.get().toString());
            Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, tv.getProperty(VariableProperty.NOT_NULL));
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

        if ("method2".equals(d.methodInfo.name) && "b".equals(d.variableName) && "0".equals(d.statementId)) {
            String expectValue = d.iteration <= 2 ? UnknownValue.NO_VALUE.toString() :
                    "org.e2immu.analyser.testexample.UnusedLocalVariableChecks.method2(String):0:param.toLowerCase()";
            Assert.assertEquals(expectValue, d.currentValue.toString());
            if (d.iteration >= 3) {
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL,
                        d.currentValue.getProperty(d.evaluationContext, VariableProperty.NOT_NULL));
            }
        }
        if ("method3".equals(d.methodInfo.name)) {
            if ("b".equals(d.variableName) && d.iteration >= 2 && Set.of("0", "1").contains(d.statementId)) {
                // this is regardless the statement id, as b is defined in the very first statement
                Assert.assertEquals(d.toString(), PARAM_3_TO_LOWER, d.currentValue.toString());
            }
            if ("a".equals(d.variableName) && "1.0.0".equals(d.statementId) && d.iteration >= 2) {
                Assert.assertEquals("xzy.toLowerCase()", d.currentValue.toString());
            }
        }
        if ("checkForEach".equals(d.methodInfo.name) && "1.0.0".equals(d.statementId) && "integers".equals(d.variableName)) {
            Assert.assertEquals(2, d.properties.get(VariableProperty.READ));
        }
        if ("checkArray2".equals(d.methodInfo.name)) {
            int read = d.properties.getOrDefault(VariableProperty.READ, Level.DELAY);
            int assigned = d.properties.getOrDefault(VariableProperty.ASSIGNED, Level.DELAY);

            if ("0".equals(d.statementId) && "integers".equals(d.variableName)) {
                Assert.assertEquals(Level.READ_ASSIGN_ONCE, assigned); // integers=, and integers[i]=
                Assert.assertEquals(Level.DELAY, read);
                Assert.assertEquals("{1,2,3}", d.currentValue.toString());
            }
            if ("1".equals(d.statementId) && "i".equals(d.variableName)) {
                Assert.assertEquals(Level.READ_ASSIGN_ONCE, assigned); // integers=, and integers[i]=
                Assert.assertEquals(Level.DELAY, read);
                Assert.assertEquals("0", d.currentValue.toString());
            }
            if ("2".equals(d.statementId)) {
                if ("integers".equals(d.variableName)) {
                    Assert.assertEquals(Level.READ_ASSIGN_ONCE, assigned); // integers=, NOT integers[i]=
                    Assert.assertEquals(assigned + 1, read);
                    Assert.assertEquals("{1,2,3}", d.currentValue.toString());
                    Assert.assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL, d.getPropertyOfCurrentValue(VariableProperty.NOT_NULL)); // because in scope side
                } else if ("i".equals(d.variableName)) {
                    Assert.assertEquals(Level.READ_ASSIGN_ONCE, assigned);
                    Assert.assertEquals(assigned + 1, read);

                    // the standardized name is the evaluation value of expression and index, in this particular case, both constants
                } else if ("integers[i]".equals(d.variableName)) {
                    Assert.assertEquals(Level.READ_ASSIGN_ONCE, assigned);
                    Assert.assertTrue(read <= assigned);
                    Assert.assertEquals("3", d.currentValue.toString());
                } else Assert.fail("Variable named " + d.variableName);
            }
        }
    };

    EvaluationResultVisitor evaluationResultVisitor = d -> {
        if ("method2".equals(d.methodInfo().name)) {
            if ("0".equals(d.statementId())) {
                Assert.assertEquals(StatementAnalyser.STEP_2, d.step());
                String expectValueString = d.iteration() <= 2 ? UnknownValue.NO_VALUE.toString() :
                        "org.e2immu.analyser.testexample.UnusedLocalVariableChecks.method2(String):0:param.toLowerCase()";
                Assert.assertEquals(expectValueString,
                        d.evaluationResult().value.toString());
            }
        }
        if ("method3".equals(d.methodInfo().name)) {
            if ("1".equals(d.statementId())) {
                Assert.assertEquals(StatementAnalyser.STEP_4, d.step());
                Assert.assertEquals("org.e2immu.analyser.testexample.UnusedLocalVariableChecks.method3(String):0:param.contains(a)",
                        d.evaluationResult().value.toString());
            }
            if ("1.0.0".equals(d.statementId())) {
                Assert.assertEquals(StatementAnalyser.STEP_2, d.step());
                String expectValueString = d.iteration() <= 1 ? UnknownValue.NO_VALUE.toString() : "xzy.toLowerCase()";
                Assert.assertEquals(expectValueString,
                        d.evaluationResult().value.toString());
            }
        }
        if ("checkArray2".equals(d.methodInfo().name)) {
            // int[] integers = {1, 2, 3};
            if ("0".equals(d.statementId())) {
                Assert.assertEquals(StatementAnalyser.STEP_2, d.step());
                Assert.assertEquals("{1,2,3}", d.evaluationResult().value.toString());
                Variable integers = d.evaluationResult().valueChanges.keySet().stream().findFirst().orElseThrow();
                Assert.assertEquals("integers", integers.fullyQualifiedName());
                Assert.assertTrue(integers instanceof LocalVariableReference);
                Assert.assertEquals("{1,2,3}", d.evaluationResult().valueChanges.get(integers).toString());
            }
            // int i=0;

            // integers[i] = 3
            if ("2".equals(d.statementId())) {
                Assert.assertEquals(StatementAnalyser.STEP_4, d.step()); // just to make sure we're on the correct statement
                //Assert.assertEquals(2L, d.evaluationResult().getModificationStream().count());
            }
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        MethodAnalysis methodAnalysis = d.methodAnalysis();
        if ("someMethod".equals(d.methodInfo().name)) {
            int expectModified = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
            Assert.assertEquals(expectModified, d.methodAnalysis().getProperty(VariableProperty.MODIFIED));
            if (d.iteration() <= 1) {
                // single return value is set before modified in the method analyser
                Assert.assertNull(d.methodAnalysis().getSingleReturnValue());
            } else {
                Assert.assertEquals("inline someMethod on org.e2immu.analyser.testexample.UnusedLocalVariableChecks.someMethod(String):0:a.toLowerCase()",
                        d.methodAnalysis().getSingleReturnValue().toString());
            }
            int notNull = d.methodAnalysis().getProperty(VariableProperty.NOT_NULL);
            int expectNotNull = MultiLevel.EFFECTIVELY_NOT_NULL;
            Assert.assertEquals(expectNotNull, notNull);
        }
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
        Collections.addAll(markers, "E1");
        testClass("UnusedLocalVariableChecks", 9, 1, new DebugConfiguration.Builder()
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                        .addEvaluationResultVisitor(evaluationResultVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().setSkipTransformations(true).build());
        Assert.assertTrue(markers.isEmpty());
    }

}
