package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.MultiValue;
import org.e2immu.analyser.model.expression.StringConcat;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.visitor.*;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

import static org.e2immu.analyser.analyser.FlowData.Execution.*;

/*
 Aggregate test, interesting for delays in flowData.
 */
public class Test_05_FinalChecks extends CommonTestRunner {

    public Test_05_FinalChecks() {
        super(false);
    }

    private static final String TYPE = "org.e2immu.analyser.testexample.FinalChecks";

    private static final String FINAL_CHECKS = "FinalChecks";
    // there are 2 constructors, with different parameter lists
    private static final String FINAL_CHECKS_FQN = TYPE + ".FinalChecks(java.lang.String,java.lang.String)";

    private static final String S1 = TYPE + ".s1";
    private static final String S4 = TYPE + ".s4";
    private static final String S4_0 = TYPE + ".s4$0";
    private static final String S5 = TYPE + ".s5";
    private static final String THIS = TYPE + ".this";

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("setS4".equals(d.methodInfo().name)) {
            if (S4.equals(d.variableName())) {
                Assert.assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                Assert.assertEquals("s4", d.currentValue().debugOutput());
                Assert.assertEquals("", d.variableInfo().getLinkedVariables().toString());
            }
            if (d.variable() instanceof ParameterInfo pi && "s4".equals(pi.name)) {
                if ("0".equals(d.statementId())) {
                    Assert.assertFalse(d.hasProperty(VariableProperty.MODIFIED_VARIABLE)); // no method was called on parameter s4
                    Assert.assertEquals("", d.variableInfo().getLinkedVariables().toString());

                    // p4 never came in a not-null context
                    Assert.assertTrue(d.variableInfo().isRead());
                    int expectNotNull = d.iteration() <= 2 ? Level.DELAY : MultiLevel.NULLABLE;
                    Assert.assertEquals(expectNotNull, d.getProperty(VariableProperty.NOT_NULL_PARAMETER)); // nothing that points to not null
                } else Assert.fail();
            }
        }
        if ("toString".equals(d.methodInfo().name) && FINAL_CHECKS.equals(d.methodInfo().typeInfo.simpleName)) {
            if (S4_0.equals(d.variableName())) {
                Assert.assertTrue(d.iteration() > 0);
                String expectValue = d.iteration() == 1
                        ? "<v:org.e2immu.analyser.testexample.FinalChecks.s4$0>" : "nullable instance type String";
                Assert.assertEquals(expectValue, d.currentValue().toString());
            }
            if (d.variable() instanceof ReturnVariable) {
                String expectValue;
                if (d.iteration() == 0) {
                    expectValue = "<f:s1>+\" \"+<f:s2>+\" \"+<f:s3>+\" \"+<f:s4>";
                } else if (d.iteration() == 1) {
                    expectValue = "<f:s1>+\" \"+<f:s2>+\" \"+\"abc\"+\" \"+org.e2immu.analyser.testexample.FinalChecks.s4$0";
                } else {
                    expectValue = "s1+\" \"+s2+\" \"+\"abc\"+\" \"+org.e2immu.analyser.testexample.FinalChecks.s4$0";
                }
                Assert.assertEquals(expectValue, d.currentValue().toString());
                Assert.assertEquals(d.iteration() > 1, d.variableInfo().valueIsSet());
            }
        }

        if (FINAL_CHECKS.equals(d.methodInfo().name) && d.methodInfo().methodInspection.get().getParameters().size() == 2) {
            Assert.assertEquals(FINAL_CHECKS_FQN, d.methodInfo().fullyQualifiedName);
            if (THIS.equals(d.variableName())) {
                if ("0".equals(d.statementId())) {
                    Assert.assertEquals("0:M", d.variableInfo().getReadId());
                    Assert.assertEquals("instance type FinalChecks", d.currentValue().toString());
                }
                if ("1".equals(d.statementId())) {
                    Assert.assertEquals("1-E", d.variableInfo().getReadId());
                    Assert.assertEquals("instance type FinalChecks", d.currentValue().toString());
                }
            }
            if (S1.equals(d.variableName())) {
                String expectValue = d.iteration() == 0 ? "s1+<f:s3>" : "s1+\"abc\"";
                Assert.assertEquals(expectValue, d.currentValue().toString());
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getPropertyOfCurrentValue(VariableProperty.NOT_NULL_EXPRESSION));
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION)); // nothing that points to not null

                if (d.iteration() > 0) {
                    Assert.assertTrue(d.currentValue().isInstanceOf(StringConcat.class));
                }
            }
            if (S5.equals(d.variableName())) {
                if ("0.0.0".equals(d.statementId())) {
                    Assert.assertEquals(Level.DELAY, d.getProperty(VariableProperty.CONTEXT_NOT_NULL_FOR_PARENT_DELAY));
                }

                if ("0".equals(d.statementId())) {
                    String expectValue = d.iteration() == 0 ? "null==<f:s5>?<s:String>:<s:>" : "\"abc\"";
                    Assert.assertEquals(expectValue, d.currentValue().toString());
                    VariableInfo viC = d.variableInfoContainer().getPreviousOrInitial();
                    int expectC = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_E2IMMUTABLE;
                    Assert.assertEquals(expectC, viC.getProperty(VariableProperty.IMMUTABLE));
                    VariableInfo viM = d.variableInfoContainer().current();
                    int expectM = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_E2IMMUTABLE;
                    Assert.assertEquals(expectM, viM.getProperty(VariableProperty.IMMUTABLE));
                }
                if ("0.0.0".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? "<s:String>" : "\"abc\"";
                    Assert.assertEquals(expect, d.currentValue().toString());
                    VariableInfo viC = d.variableInfoContainer().getPreviousOrInitial();
                    int expectImmutable = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_E2IMMUTABLE;
                    Assert.assertEquals(expectImmutable, viC.getProperty(VariableProperty.IMMUTABLE));
                    VariableInfo viE = d.variableInfoContainer().best(VariableInfoContainer.Level.EVALUATION);
                    int expectEval = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_E2IMMUTABLE;
                    Assert.assertEquals(expectEval, viE.getProperty(VariableProperty.IMMUTABLE));
                }
            }
        }

    };

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if (FINAL_CHECKS_FQN.equals(d.methodInfo().fullyQualifiedName())) {
            Assert.assertTrue(d.statementAnalysis().inSyncBlock);
            if ("0.0.1".equals(d.statementId())) {
                Assert.fail(); // unreachable
            }
            if ("1.0.1".equals(d.statementId())) {
                Assert.fail(); // statement unreachable
            }
            if ("0".equals(d.statementId())) {
                FlowData fd0 = d.statementAnalysis().navigationData.blocks.get().get(0).orElseThrow().flowData;
                FlowData fd1 = d.statementAnalysis().navigationData.blocks.get().get(1).orElseThrow().flowData;
                if (d.iteration() == 0) {
                    Assert.assertEquals(DELAYED_EXECUTION, fd0.getGuaranteedToBeReachedInMethod());
                    Assert.assertEquals(DELAYED_EXECUTION, fd1.getGuaranteedToBeReachedInMethod());
                    Assert.assertEquals(ALWAYS, fd0.getGuaranteedToBeReachedInCurrentBlock());
                    Assert.assertEquals(ALWAYS, fd1.getGuaranteedToBeReachedInCurrentBlock());
                } else {
                    Assert.assertEquals(ALWAYS, fd0.getGuaranteedToBeReachedInMethod());
                    Assert.assertEquals(NEVER, fd1.getGuaranteedToBeReachedInMethod());
                    Assert.assertTrue(fd1.isUnreachable());
                }
            }
            if ("1".equals(d.statementId())) {
                Assert.assertEquals(d.iteration() > 0,
                        d.statementAnalysis().navigationData.blocks.get().get(0).orElseThrow().flowData.isUnreachable());
            }
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        TypeInfo stringType = d.evaluationContext().getPrimitives().stringTypeInfo;
        Assert.assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE,
                stringType.typeAnalysis.get().getProperty(VariableProperty.IMMUTABLE));
        MethodInfo methodInfo = d.methodInfo();

        if ("setS4".equals(methodInfo.name)) {
            // @NotModified decided straight away, @Identity as well
            //     Assert.assertEquals(Level.FALSE, d.parameterAnalyses().get(0).getProperty(VariableProperty.MODIFIED_METHOD));
            ParameterAnalysis param = d.parameterAnalyses().get(0);
            int expectCnn = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
            Assert.assertEquals(expectCnn, param.getProperty(VariableProperty.CONTEXT_NOT_NULL));
        }
    };

    FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
        if ("s1".equals(d.fieldInfo().name)) {
            if (d.iteration() > 0) {
                // cannot properly be assigned/linked to one parameter
                Assert.assertEquals("[s1+\"abc\",s1]", d.fieldAnalysis().getEffectivelyFinalValue().debugOutput());
            } else {
                Assert.assertNull(d.fieldAnalysis().getEffectivelyFinalValue());
            }
            Assert.assertEquals("", d.fieldAnalysis().getLinkedVariables().toString());
        }
        if ("s2".equals(d.fieldInfo().name)) {
            if (d.iteration() > 0) {
                Assert.assertEquals("[null,s2]", d.fieldAnalysis().getEffectivelyFinalValue().debugOutput());
                Assert.assertTrue(d.fieldAnalysis().getEffectivelyFinalValue() instanceof MultiValue);
            } else {
                Assert.assertNull(d.fieldAnalysis().getEffectivelyFinalValue());
            }
            Assert.assertEquals("", d.fieldAnalysis().getLinkedVariables().toString());
        }
        if ("s4".equals(d.fieldInfo().name)) {
            Assert.assertEquals(MultiLevel.NULLABLE, d.fieldAnalysis().getProperty(VariableProperty.EXTERNAL_NOT_NULL));
        }
        if ("s5".equals(d.fieldInfo().name)) {
            Assert.assertEquals(Level.TRUE, d.fieldAnalysis().getProperty(VariableProperty.FINAL));
        }
    };

    EvaluationResultVisitor evaluationResultVisitor = d -> {
        if (FINAL_CHECKS_FQN.equals(d.methodInfo().fullyQualifiedName())) {
            if ("0".equals(d.statementId())) {
                // this.s5 == null

                // first, show that THIS is read
                EvaluationResult.ChangeData changeData = d.findValueChange(THIS);
                Assert.assertEquals("[0]", changeData.readAtStatementTime().toString());

                // null==s5 should become true because initially, s5 in the constructor IS null
                String expect = d.iteration() == 0 ? "null==<f:s5>" : "true";
                Assert.assertEquals(expect, d.evaluationResult().value().toString());
            }
            if ("3".equals(d.statementId())) {
                String expect = d.iteration() == 0 ? "s1+<f:s3>" : "s1+\"abc\"";
                Assert.assertEquals(expect, d.evaluationResult().value().toString());
            }
        }
    };

    @Test
    public void test() throws IOException {
        testClass(FINAL_CHECKS, 5, 0, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build());
    }
}
