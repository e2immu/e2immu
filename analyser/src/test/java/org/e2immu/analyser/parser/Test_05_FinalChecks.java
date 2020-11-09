package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.StatementAnalyser;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analyser.VariableInfoContainer;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.EvaluationResultVisitor;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.e2immu.analyser.config.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.StringConcat;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.e2immu.analyser.testexample.FinalChecks;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

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
    private static final String S5 = FinalChecks.class.getCanonicalName() + ".s5";

    StatementAnalyserVariableVisitor statementAnalyserVisitor = d -> {
        if (d.methodInfo().name.equals("setS4") && P4.equals(d.variableName())) {
            if ("0".equals(d.statementId())) {
                Assert.assertFalse(d.hasProperty(VariableProperty.MODIFIED)); // no method was called on parameter s4
                Assert.assertEquals(1, d.getProperty(VariableProperty.READ)); // read 1x
                // there is an explicit @NotNull on the first parameter of debug
                int expectNotNull = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
                Assert.assertEquals(expectNotNull, d.getProperty(VariableProperty.NOT_NULL)); // nothing that points to not null
            } else Assert.fail();
        }

        if (FINAL_CHECKS.equals(d.methodInfo().name) && d.methodInfo().methodInspection.get().parameters.size() == 2) {
            if (S1.equals(d.variableName())) {
                Assert.assertEquals(S1_P0 + " + abc", d.currentValue().toString());
                Assert.assertEquals(MultiLevel.EFFECTIVE, MultiLevel.value(d.getPropertyOfCurrentValue(VariableProperty.NOT_NULL), MultiLevel.NOT_NULL));
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.NOT_NULL)); // nothing that points to not null
                Assert.assertTrue(d.currentValue().isInstanceOf(StringConcat.class));
            }
            if ("2".equals(d.statementId()) && S2.equals(d.variableName())) {
                Assert.assertSame(UnknownValue.EMPTY, d.variableInfo().getStateOnAssignment());
            }
            if ("3".equals(d.statementId()) && S2.equals(d.variableName())) {
                // stateOnAssignment has to be copied from statement 1
                Assert.assertSame(UnknownValue.EMPTY, d.variableInfo().getStateOnAssignment());
            }
            if (S5.equals(d.variableName())) {
                if ("0".equals(d.statementId())) {
                    VariableInfo vi1 = d.variableInfoContainer().best(VariableInfoContainer.LEVEL_1_INITIALISER);
                    Assert.assertEquals(Level.DELAY, vi1.getProperty(VariableProperty.IMMUTABLE));
                    VariableInfo vi4 = d.variableInfoContainer().best(VariableInfoContainer.LEVEL_4_SUMMARY);
                    Assert.assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE, vi4.getProperty(VariableProperty.IMMUTABLE));
                }
                if ("0.0.0".equals(d.statementId())) {
                    Assert.assertEquals("abc", d.currentValue().toString());
                    VariableInfo vi1 = d.variableInfoContainer().best(VariableInfoContainer.LEVEL_1_INITIALISER);
                    Assert.assertEquals(Level.DELAY, vi1.getProperty(VariableProperty.IMMUTABLE));
                    VariableInfo vi3 = d.variableInfoContainer().best(VariableInfoContainer.LEVEL_3_EVALUATION);
                    Assert.assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE, vi3.getProperty(VariableProperty.IMMUTABLE));
                }
            }
        }

    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        TypeInfo stringType = d.evaluationContext().getPrimitives().stringTypeInfo;
        Assert.assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE, stringType.typeAnalysis.get().getProperty(VariableProperty.IMMUTABLE));
        int iteration = d.iteration();
        MethodInfo methodInfo = d.methodInfo();

        if ("setS4".equals(methodInfo.name)) {
            // @NotModified decided straight away, @Identity as well
            Assert.assertEquals(Level.FALSE, d.parameterAnalyses().get(0).getProperty(VariableProperty.MODIFIED));
            int expectNotNull = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
            Assert.assertEquals(expectNotNull, d.parameterAnalyses().get(0).getProperty(VariableProperty.NOT_NULL));
        }

        // there is no size restriction
        if (iteration > 0) {
            FieldInfo s1 = methodInfo.typeInfo.typeInspection.getPotentiallyRun().fields.stream().filter(f -> "s1".equals(f.name)).findFirst().orElseThrow();
            if ("toString".equals(methodInfo.name) || FINAL_CHECKS_FQN.equals(methodInfo.fullyQualifiedName())) {
                int notNull = d.getFieldAsVariable(s1).getProperty(VariableProperty.NOT_NULL);
                Assert.assertEquals(MultiLevel.MUTABLE, notNull);
            }
        }
    };


    EvaluationResultVisitor evaluationResultVisitor = d -> {
        if (FINAL_CHECKS_FQN.equals(d.methodInfo().fullyQualifiedName()) && "0".equals(d.statementId())) {
            Assert.assertEquals(StatementAnalyser.STEP_3, d.step());
            Assert.assertEquals("true", d.evaluationResult().value.toString());
        }
        if (FINAL_CHECKS_FQN.equals(d.methodInfo().fullyQualifiedName()) && "2".equals(d.statementId())) {
            Assert.assertEquals(StatementAnalyser.STEP_3, d.step());
            d.findMarkAssigned(S2);
            StatementAnalyser.SetStateOnAssignment ssa = d.findSetStateOnAssignment(S2);
            Assert.assertSame(UnknownValue.EMPTY, ssa.state);
        }
    };

    @Test
    public void test() throws IOException {
        testClass(FINAL_CHECKS, 2, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build());
    }
}
