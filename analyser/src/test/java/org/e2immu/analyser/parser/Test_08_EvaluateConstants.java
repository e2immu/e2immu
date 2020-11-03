package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.AnalysisStatus;
import org.e2immu.analyser.analyser.MethodLevelData;
import org.e2immu.analyser.analyser.StatementAnalyser;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.e2immu.analyser.model.value.ConstantValue;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class Test_08_EvaluateConstants extends CommonTestRunner {
    public Test_08_EvaluateConstants() {
        super(false);
    }

    EvaluationResultVisitor evaluationResultVisitor = d -> {
        if ("print2".equals(d.methodInfo().name)) {
            Assert.assertEquals(StatementAnalyser.STEP_4, d.step());
            Assert.assertTrue(d.evaluationResult().value.isInstanceOf(ConstantValue.class));
            Assert.assertEquals("b", d.evaluationResult().value.toString());
            Assert.assertEquals(4L, d.evaluationResult().getObjectFlowStream().count());
            ObjectFlow objectFlow = d.evaluationResult().getObjectFlowStream().findFirst().orElseThrow();
            // ee modified?
            if (d.iteration() > 0) {
                Assert.assertFalse(objectFlow.isDelayed());
            }
        }
    };

    /*
    Method ee() becomes @NotModified in iteration 1
    Only then, the internal object flows of print2 can be frozen; this happens during evaluation.

     */

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if ("print".equals(d.methodInfo().name)) {
            if ("0".equals(d.statementId()) && d.iteration() >= 1) {
                Assert.assertNotNull(d.haveError(Message.CONDITION_EVALUATES_TO_CONSTANT));
            }
            if ("0.0.0".equals(d.statementId())) {
                Assert.assertNull(d.haveError(Message.CONDITION_EVALUATES_TO_CONSTANT));
            }
        }
        if ("ee".equals(d.methodInfo().name)) {
            MethodLevelData methodLevelData = d.statementAnalysis().methodLevelData;
            if (d.iteration() > 0) {
                Assert.assertTrue(methodLevelData.variablesLinkedToFieldsAndParameters.isSet());
            }
        }
        if ("print2".equals(d.methodInfo().name)) {
            if ("0".equals(d.statementId())) {
                Assert.assertNotNull(d.haveError(Message.INLINE_CONDITION_EVALUATES_TO_CONSTANT));
                MethodLevelData methodLevelData = d.statementAnalysis().methodLevelData;
                AnalysisStatus expectStatus = d.iteration() == 0 ? AnalysisStatus.PROGRESS : AnalysisStatus.DONE;
                Assert.assertSame(expectStatus, d.result().analysisStatus);
                if (d.iteration() >= 1) {
                    Assert.assertTrue(methodLevelData.internalObjectFlows.isFrozen()); // by apply
                }
            }
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        if ("ee".equals(d.methodInfo().name)) {
            // we prove that ee() returns false
            Value srv = d.methodAnalysis().getSingleReturnValue();
            Assert.assertEquals("false", srv.toString());
            int modified = d.methodAnalysis().getProperty(VariableProperty.MODIFIED);
            int expectModified = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
            Assert.assertEquals(expectModified, modified);
        }
        if ("print".equals(d.methodInfo().name)) {
            Value srv = d.methodAnalysis().getSingleReturnValue();
            Assert.assertSame(UnknownValue.RETURN_VALUE, srv); // not constant, the ee() error is ignored
        }
        if ("print2".equals(d.methodInfo().name) && d.iteration() > 2) {
            MethodLevelData methodLevelData = d.methodAnalysis().methodLevelData();
            Assert.assertTrue(methodLevelData.internalObjectFlows.isFrozen());
            Value srv = d.methodAnalysis().getSingleReturnValue();
            // Assert.assertTrue(srv instanceof StringValue); // inline conditional works as advertised
        }
    };

    @Test
    public void test() throws IOException {
        testClass("EvaluateConstants", 3, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build(), new AnalyserConfiguration.Builder().setSkipTransformations(true).build());

        // second time, run with replacement

        testClass("EvaluateConstants", 2, 0, new DebugConfiguration.Builder()
                .build());
    }

}
