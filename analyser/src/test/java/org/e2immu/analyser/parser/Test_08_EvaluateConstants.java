package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.LinkedVariables;
import org.e2immu.analyser.analyser.MethodLevelData;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.expression.ConstantExpression;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.expression.StringConstant;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

import static org.e2immu.analyser.analyser.AnalysisStatus.DONE;

public class Test_08_EvaluateConstants extends CommonTestRunner {

    public Test_08_EvaluateConstants() {
        super(false);
    }

    EvaluationResultVisitor evaluationResultVisitor = d -> {
        if ("print".equals(d.methodInfo().name) && "0".equals(d.statementId())) {
            Assert.assertEquals("false", d.evaluationResult().value().toString());
        }
        if ("print2".equals(d.methodInfo().name)) {
            Assert.assertTrue(d.evaluationResult().value().isInstanceOf(ConstantExpression.class));
            Assert.assertEquals("\"b\"", d.evaluationResult().value().toString());
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
            // just says: return e; (e is a field, constant false (rather than linked to c and c, I'd say)
            if (d.iteration() > 0) {
                Assert.assertTrue(d.statementAnalysis().variableStream().filter(vi -> vi.variable() instanceof FieldReference)
                        .allMatch(VariableInfo::linkedVariablesIsSet));
            }
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
            int expectNotNull = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
            Assert.assertEquals(expectNotNull, vi.getProperty(VariableProperty.NOT_NULL));
            if (d.iteration() == 0) {
                Assert.assertSame(EmptyExpression.NO_VALUE, vi.getValue());
            } else if (d.iteration() == 1) {
                Assert.assertEquals("effectivelyFinal", vi.getValue().toString());
                Assert.assertSame(DONE, d.result().analysisStatus);
            } else Assert.fail();
        }
        if ("EvaluateConstants".equals(d.methodInfo().name)) {
            Assert.assertTrue(d.toString(), d.statementAnalysis().methodLevelData.linksHaveBeenEstablished.isSet());
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        if ("ee".equals(d.methodInfo().name)) {
            // we prove that ee() returns false
            Expression srv = d.methodAnalysis().getSingleReturnValue();
            Assert.assertEquals("false", srv.toString());
            int modified = d.methodAnalysis().getProperty(VariableProperty.MODIFIED);
            Assert.assertEquals(Level.FALSE, modified);
        }
        if ("print".equals(d.methodInfo().name)) {
            Expression srv = d.methodAnalysis().getSingleReturnValue();
            Assert.assertEquals("\"b\"", srv.toString());
        }
        if ("print2".equals(d.methodInfo().name) && d.iteration() > 2) {
            MethodLevelData methodLevelData = d.methodAnalysis().methodLevelData();
            Assert.assertTrue(methodLevelData.internalObjectFlows.isFrozen());
            Expression srv = d.methodAnalysis().getSingleReturnValue();
            Assert.assertTrue(srv instanceof StringConstant); // inline conditional works as advertised
        }
        if ("getEffectivelyFinal".equals(d.methodInfo().name)) {
            Expression srv = d.methodAnalysis().getSingleReturnValue();
            if (d.iteration() == 0) {
                Assert.assertNull(srv);
            } else {
                Assert.assertEquals("/* inline getEffectivelyFinal */this.effectivelyFinal",
                        srv.debugOutput());
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
                Assert.assertEquals("in", d.fieldAnalysis().getEffectivelyFinalValue().toString());
            }
        }
        if ("e".equals(d.fieldInfo().name)) {
            if (d.iteration() == 0) {
                Assert.assertSame(LinkedVariables.DELAY, d.fieldAnalysis().getLinkedVariables());
            } else {
                Assert.assertTrue(d.fieldAnalysis().getLinkedVariables().isEmpty());
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
    }

}
