package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.AnalysisStatus;
import org.e2immu.analyser.analyser.StatementAnalyser;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class TestFieldNotRead extends CommonTestRunner {

    public TestFieldNotRead() {
        super(true);
    }

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if ("method1".equals(d.methodInfo().name)) {
            if ("0".equals(d.statementId())) {
                AnalysisStatus expectAnalysisStatus = d.iteration() <= 1 ? AnalysisStatus.PROGRESS : AnalysisStatus.DONE;
                Assert.assertEquals(d.toString(), expectAnalysisStatus, d.analysisStatus());
            }
            // ERROR: t.trim() result is not used
            if ("2".equals(d.statementId())) {
                // ERROR: unused variable "s"
                Assert.assertEquals("ERROR in M:method1:2: Unused local variable: s", d.haveError(Message.UNUSED_LOCAL_VARIABLE));
                Assert.assertNull(d.haveError(Message.USELESS_ASSIGNMENT));
                if (d.iteration() >= 2) {
                    Assert.assertNotNull(d.haveError(Message.IGNORING_RESULT_OF_METHOD_CALL));
                }
                AnalysisStatus expectAnalysisStatus = d.iteration() <= 1 ? AnalysisStatus.PROGRESS : AnalysisStatus.DONE;
                Assert.assertEquals(d.toString(), expectAnalysisStatus, d.analysisStatus());
            }
        }
        // ERROR: Unused variable "a"
        // ERROR: useless assignment to "a" as well
        if ("UnusedLocalVariableChecks".equals(d.methodInfo().name) && "1".equals(d.statementId())) {
            Assert.assertEquals("ERROR in M:UnusedLocalVariableChecks:1: Unused local variable: a", d.haveError(Message.UNUSED_LOCAL_VARIABLE));
            Assert.assertEquals("ERROR in M:UnusedLocalVariableChecks:1: Useless assignment: a", d.haveError(Message.USELESS_ASSIGNMENT));

            AnalysisStatus expectAnalysisStatus = d.iteration() == 0 ? AnalysisStatus.PROGRESS : AnalysisStatus.DONE;
            Assert.assertEquals(d.toString(), expectAnalysisStatus, d.analysisStatus());
        }
        if ("checkArray2".equals(d.methodInfo().name) && "2".equals(d.statementId())) {
            Assert.assertEquals("ERROR in M:checkArray2:2: Useless assignment: integers[i]", d.haveError(Message.USELESS_ASSIGNMENT));

            // TODO prob wrong
            AnalysisStatus expectAnalysisStatus = d.iteration() <= 1 ? AnalysisStatus.PROGRESS : AnalysisStatus.DONE;
            Assert.assertEquals(d.toString(), expectAnalysisStatus, d.analysisStatus());
        }
        if ("checkForEach".equals(d.methodInfo().name) && "1.0.0".equals(d.statementId())) {
            Assert.assertEquals("ERROR in M:checkForEach:1.0.0: Unused local variable: loopVar", d.haveError(Message.UNUSED_LOCAL_VARIABLE));

            AnalysisStatus expectAnalysisStatus = d.iteration() <= 1 ? AnalysisStatus.PROGRESS : AnalysisStatus.DONE;
            Assert.assertEquals(d.toString(), expectAnalysisStatus, d.analysisStatus());
        }
    };

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("checkForEach".equals(d.methodInfo().name) && "1.0.0".equals(d.statementId()) && "integers".equals(d.variableName())) {
            Assert.assertEquals(2, d.properties().get(VariableProperty.READ));
        }
        if ("checkArray2".equals(d.methodInfo().name)) {
            int read = d.properties().getOrDefault(VariableProperty.READ, Level.DELAY);
            int assigned = d.properties().getOrDefault(VariableProperty.ASSIGNED, Level.DELAY);

            if ("0".equals(d.statementId()) && "integers".equals(d.variableName())) {
                Assert.assertEquals(Level.READ_ASSIGN_ONCE, assigned); // integers=, and integers[i]=
                Assert.assertEquals(Level.DELAY, read);
                Assert.assertEquals("{1,2,3}", d.currentValue().toString());
            }
            if ("1".equals(d.statementId()) && "i".equals(d.variableName())) {
                Assert.assertEquals(Level.READ_ASSIGN_ONCE, assigned); // integers=, and integers[i]=
                Assert.assertEquals(Level.DELAY, read);
                Assert.assertEquals("0", d.currentValue().toString());
            }
            if ("2".equals(d.statementId())) {
                if ("integers".equals(d.variableName())) {
                    Assert.assertEquals(Level.READ_ASSIGN_ONCE, assigned); // integers=, NOT integers[i]=
                    Assert.assertEquals(assigned + 1, read);
                    Assert.assertEquals("{1,2,3}", d.currentValue().toString());
                    Assert.assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL, d.getPropertyOfCurrentValue(VariableProperty.NOT_NULL)); // because in scope side
                } else if ("i".equals(d.variableName())) {
                    Assert.assertEquals(Level.READ_ASSIGN_ONCE, assigned);
                    Assert.assertEquals(assigned + 1, read);

                    // the standardized name is the evaluation value of expression and index, in this particular case, both constants
                } else if ("integers[i]".equals(d.variableName())) {
                    Assert.assertEquals(Level.READ_ASSIGN_ONCE, assigned);
                    Assert.assertTrue(read <= assigned);
                    Assert.assertEquals("3", d.currentValue().toString());
                } else Assert.fail("Variable named " + d.variableName());
            }
        }
    };

    EvaluationResultVisitor evaluationResultVisitor = d -> {
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
                        .addEvaluationResultVisitor(evaluationResultVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().setSkipTransformations(true).build());
    }

}
