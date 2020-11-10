package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.MethodLevelData;
import org.e2immu.analyser.analyser.StatementAnalyser;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.e2immu.analyser.model.value.ConstantValue;
import org.e2immu.analyser.model.value.StringValue;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

import static org.e2immu.analyser.analyser.AnalysisStatus.DONE;

public class Test_08_EvaluateConstants extends CommonTestRunner {

    private static final String EFFECTIVELY_FINAL = "org.e2immu.analyser.testexample.EvaluateConstants.effectivelyFinal";

    public Test_08_EvaluateConstants() {
        super(false);
    }

    EvaluationResultVisitor evaluationResultVisitor = d -> {
        if ("print".equals(d.methodInfo().name) && "0".equals(d.statementId())) {
            Assert.assertEquals(StatementAnalyser.STEP_3, d.step());
            Assert.assertEquals("false", d.evaluationResult().value.toString());
        }
        if ("print2".equals(d.methodInfo().name)) {
            Assert.assertEquals(StatementAnalyser.STEP_3, d.step());
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
        MethodLevelData methodLevelData = d.statementAnalysis().methodLevelData;
        if ("print".equals(d.methodInfo().name)) {
            if ("0".equals(d.statementId()) && d.iteration() >= 1) {
                Assert.assertNotNull(d.haveError(Message.CONDITION_EVALUATES_TO_CONSTANT));
            }
            if ("0.0.0".equals(d.statementId())) {
                Assert.assertNull(d.haveError(Message.CONDITION_EVALUATES_TO_CONSTANT));
                if (d.iteration() >= 1) {
                    Assert.assertTrue(d.statementAnalysis().flowData.isUnreachable());
                }
            }
        }
        if ("ee".equals(d.methodInfo().name)) {
            Assert.assertTrue(methodLevelData.linksHaveBeenEstablished.isSet());
        }
        if ("print2".equals(d.methodInfo().name)) {
            if ("0".equals(d.statementId())) {
                Assert.assertNotNull(d.haveError(Message.INLINE_CONDITION_EVALUATES_TO_CONSTANT));
                Assert.assertSame(DONE, d.result().analysisStatus);
                Assert.assertTrue(methodLevelData.internalObjectFlows.isFrozen()); // by apply
            }
        }
        if ("getEffectivelyFinal".equals(d.methodInfo().name)) {
            VariableInfo vi = d.getReturnAsVariable();

            Assert.assertEquals(Level.DELAY, vi.getProperty(VariableProperty.NOT_NULL));
            if (d.iteration() == 0) {
                Assert.assertSame(UnknownValue.NO_VALUE, vi.getValue());
            } else if (d.iteration() == 1) {
                Assert.assertEquals(EFFECTIVELY_FINAL, vi.getValue().toString());
                Assert.assertSame(DONE, d.result().analysisStatus);
            } else Assert.fail();
        }
        if ("EvaluateConstants".equals(d.methodInfo().name)) {
            boolean expected = d.statementId().compareTo("1") < 0 || d.iteration() != 0;
            Assert.assertEquals(d.toString(), expected, d.statementAnalysis().methodLevelData.linksHaveBeenEstablished.isSet());
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        if ("ee".equals(d.methodInfo().name)) {
            // we prove that ee() returns false
            Value srv = d.methodAnalysis().getSingleReturnValue();
            Assert.assertEquals("false", srv.toString());
            int modified = d.methodAnalysis().getProperty(VariableProperty.MODIFIED);
            //int expectModified = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
            Assert.assertEquals(Level.FALSE, modified);
        }
        if ("print".equals(d.methodInfo().name)) {
            Value srv = d.methodAnalysis().getSingleReturnValue();
            Assert.assertEquals("b", srv.toString());
        }
        if ("print2".equals(d.methodInfo().name) && d.iteration() > 2) {
            MethodLevelData methodLevelData = d.methodAnalysis().methodLevelData();
            Assert.assertTrue(methodLevelData.internalObjectFlows.isFrozen());
            Value srv = d.methodAnalysis().getSingleReturnValue();
            Assert.assertTrue(srv instanceof StringValue); // inline conditional works as advertised
        }
        if ("getEffectivelyFinal".equals(d.methodInfo().name)) {
            Value srv = d.methodAnalysis().getSingleReturnValue();
            if (d.iteration() == 0) {
                Assert.assertNull(srv);
            } else {
                // TODO not sure this is so valuable (should we have an inline here??)
                Assert.assertEquals("inline getEffectivelyFinal on org.e2immu.analyser.testexample.EvaluateConstants.effectivelyFinal",
                        srv.toString());
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.methodAnalysis().getProperty(VariableProperty.NOT_NULL));
            }
        }
    };

    FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
        if ("effectivelyFinal".equals(d.fieldInfo().name)) {
            int effectivelyFinal = d.fieldAnalysis().getProperty(VariableProperty.FINAL);
            int expectFinal = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
            Assert.assertEquals(expectFinal, effectivelyFinal);
            int notNull = d.fieldAnalysis().getProperty(VariableProperty.NOT_NULL);
            int expectNotNull = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
            Assert.assertEquals(expectNotNull, notNull);

            if (d.iteration() == 0) {
                Assert.assertNull(d.fieldAnalysis().getEffectivelyFinalValue());
            } else {
                Assert.assertEquals(EFFECTIVELY_FINAL, d.fieldAnalysis().getEffectivelyFinalValue().toString());
            }
        }
    };

    @Test
    public void test() throws IOException {
        testClass("EvaluateConstants", 3, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build(), new AnalyserConfiguration.Builder().setSkipTransformations(true).build());

        // second time, run with replacement

        // TODO replacements are not implemented a t m
        //testClass("EvaluateConstants", 2, 0, new DebugConfiguration.Builder()
        //       .build());
    }

}
