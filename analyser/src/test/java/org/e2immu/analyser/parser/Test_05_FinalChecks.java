package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.expression.StringConcat;
import org.e2immu.analyser.testexample.FinalChecks;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

/*
 How does s4 the parameter of setS4 become nullable?
 In the order of the primary type analyser, field s4 is analysed before method setS4, which is analysed before the constructors.
 Order of analyser visitors is therefore, per iteration:
  - field analyser for s4 field,
  - statement analyser for setS4 method

 Iteration 0:
  - the field analyser ignores field s4
  - the parameter analyser ignores field s4, as the FINAL status of s3 is not known

 Iteration 1:
  - the field analyser sets field s4 to NULLABLE, MUTABLE
  - the parameter analyser ignores field s4, because the effectivelyFinal value of s1 is not known, s2's FINAL, ...

 Iteration 2:
  - the statement analyser sees that field s4 is NULLABLE, but still does not know about parameter s4
  - the parameter analyser sets s4 to NULLABLE

 Iteration 3:
  - the statement analyser sees that parameter s4 is nullable

 */
public class Test_05_FinalChecks extends CommonTestRunner {

    public Test_05_FinalChecks() {
        super(false);
    }

    private static final String FINAL_CHECKS = "FinalChecks";
    // there are 2 constructors, with different parameter lists
    private static final String FINAL_CHECKS_FQN = "org.e2immu.analyser.testexample.FinalChecks.FinalChecks(String,String)";

    private static final String S1 = FinalChecks.class.getCanonicalName() + ".s1";
    private static final String S1_P0 = FinalChecks.class.getCanonicalName() + ".FinalChecks(String,String):0:s1";
    private static final String S2 = FinalChecks.class.getCanonicalName() + ".s2";
    private static final String P4 = FinalChecks.class.getCanonicalName() + ".setS4(String):0:s4";
    private static final String S4 = FinalChecks.class.getCanonicalName() + ".s4";
    private static final String S5 = FinalChecks.class.getCanonicalName() + ".s5";

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("setS4".equals(d.methodInfo().name) && S4.equals(d.variableName())) {
            int expectNotNull = d.iteration() <= 2 ? Level.DELAY : MultiLevel.NULLABLE;
            Assert.assertEquals(expectNotNull, d.getProperty(VariableProperty.NOT_NULL)); // nothing that points to not null
        }
        if (d.methodInfo().name.equals("setS4") && P4.equals(d.variableName())) {
            if ("0".equals(d.statementId())) {
                Assert.assertFalse(d.hasProperty(VariableProperty.MODIFIED)); // no method was called on parameter s4
                Assert.assertEquals(Level.DELAY, d.getProperty(VariableProperty.NOT_NULL_DELAYS_RESOLVED)); // p4 never came in a not-null context

                Assert.assertEquals(1, d.getProperty(VariableProperty.READ)); // read 1x
                int expectNotNull = d.iteration() <= 2 ? Level.DELAY : MultiLevel.NULLABLE;
                Assert.assertEquals(expectNotNull, d.getProperty(VariableProperty.NOT_NULL)); // nothing that points to not null
            } else Assert.fail();
        }

        if (FINAL_CHECKS.equals(d.methodInfo().name) && d.methodInfo().methodInspection.get().getParameters().size() == 2) {
            if (S1.equals(d.variableName())) {
                Assert.assertEquals("s1+\"abc\"", d.currentValue().toString());
                Assert.assertEquals(MultiLevel.EFFECTIVE, MultiLevel.value(d.getPropertyOfCurrentValue(VariableProperty.NOT_NULL), MultiLevel.NOT_NULL));
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.NOT_NULL)); // nothing that points to not null
                Assert.assertTrue(d.currentValue().isInstanceOf(StringConcat.class));
            }
            if ("2".equals(d.statementId()) && S2.equals(d.variableName())) {
                Assert.assertSame(EmptyExpression.EMPTY_EXPRESSION, d.variableInfo().getStateOnAssignment());
            }
            if ("3".equals(d.statementId()) && S2.equals(d.variableName())) {
                // stateOnAssignment has to be copied from statement 1
                Assert.assertSame(EmptyExpression.EMPTY_EXPRESSION, d.variableInfo().getStateOnAssignment());
            }
            if (S5.equals(d.variableName())) {
                if ("0".equals(d.statementId())) {
                    VariableInfo vi1 = d.variableInfoContainer().best(VariableInfoContainer.LEVEL_1_INITIALISER);
                    Assert.assertEquals(Level.DELAY, vi1.getProperty(VariableProperty.IMMUTABLE));
                    VariableInfo vi4 = d.variableInfoContainer().best(VariableInfoContainer.LEVEL_4_SUMMARY);
                    Assert.assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE, vi4.getProperty(VariableProperty.IMMUTABLE));
                }
                if ("0.0.0".equals(d.statementId())) {
                    Assert.assertEquals("\"abc\"", d.currentValue().toString());
                    VariableInfo vi1 = d.variableInfoContainer().best(VariableInfoContainer.LEVEL_1_INITIALISER);
                    Assert.assertEquals(Level.DELAY, vi1.getProperty(VariableProperty.IMMUTABLE));
                    VariableInfo vi3 = d.variableInfoContainer().best(VariableInfoContainer.LEVEL_3_EVALUATION);
                    Assert.assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE, vi3.getProperty(VariableProperty.IMMUTABLE));
                }
            }
        }

    };

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if (FINAL_CHECKS_FQN.equals(d.methodInfo().fullyQualifiedName())) {
            if ("0.0.1".equals(d.statementId())) {
                Assert.fail(); // unreachable
            }
            if ("1.0.1".equals(d.statementId())) {
                Assert.fail(); // statement unreachable
            }
            if ("0".equals(d.statementId())) {
                Assert.assertTrue(d.statementAnalysis().navigationData.blocks.get().get(1).orElseThrow().flowData.isUnreachable());
            }
            if ("1".equals(d.statementId())) {
                Assert.assertTrue(d.statementAnalysis().navigationData.blocks.get().get(0).orElseThrow().flowData.isUnreachable());
            }
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        TypeInfo stringType = d.evaluationContext().getPrimitives().stringTypeInfo;
        Assert.assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE, stringType.typeAnalysis.get().getProperty(VariableProperty.IMMUTABLE));
        MethodInfo methodInfo = d.methodInfo();

        if ("setS4".equals(methodInfo.name)) {
            // @NotModified decided straight away, @Identity as well
            Assert.assertEquals(Level.FALSE, d.parameterAnalyses().get(0).getProperty(VariableProperty.MODIFIED));
            int expectNotNull = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
            Assert.assertEquals(expectNotNull, d.parameterAnalyses().get(0).getProperty(VariableProperty.NOT_NULL));
        }
    };

    FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
        if ("s4".equals(d.fieldInfo().name)) {
            int expectNotNull = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
            Assert.assertEquals(expectNotNull, d.fieldAnalysis().getProperty(VariableProperty.NOT_NULL));
        }
    };

    EvaluationResultVisitor evaluationResultVisitor = d -> {
        if (FINAL_CHECKS_FQN.equals(d.methodInfo().fullyQualifiedName()) && "0".equals(d.statementId())) {
            Assert.assertEquals(StatementAnalyser.STEP_3, d.step());
            Assert.assertEquals("true", d.evaluationResult().value.toString());
        }
        if (FINAL_CHECKS_FQN.equals(d.methodInfo().fullyQualifiedName()) && "2".equals(d.statementId())) {
            Assert.assertEquals(StatementAnalyser.STEP_3, d.step());
            EvaluationResult.ExpressionChangeData valueChangeData = d.findValueChange(S2);
            Assert.assertSame(EmptyExpression.EMPTY_EXPRESSION, valueChangeData.stateOnAssignment());
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
