package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.StatementAnalyser;
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
    private static final String S1 = FinalChecks.class.getCanonicalName() + ".s1";
    private static final String S1_P0 = FinalChecks.class.getCanonicalName() + ".FinalChecks(String,String):0:s1";
    private static final String S2 = FinalChecks.class.getCanonicalName() + ".s2";
    private static final String P4 = FinalChecks.class.getCanonicalName() + ".setS4(String):0:s4";

    StatementAnalyserVariableVisitor statementAnalyserVisitor = d -> {
        if (d.methodInfo().name.equals("setS4") && P4.equals(d.variableName())) {
            if ("0".equals(d.statementId())) {
                Assert.assertFalse(d.hasProperty(VariableProperty.MODIFIED)); // no method was called on parameter s4
                Assert.assertEquals(1, d.getProperty(VariableProperty.READ)); // read 1x
                // there is an explicit @NotNull on the first parameter of debug
                Assert.assertFalse(d.properties().containsKey(VariableProperty.NOT_NULL)); // nothing that points to not null
            } else Assert.fail();
        }

        if (FINAL_CHECKS.equals(d.methodInfo().name) && d.methodInfo().methodInspection.get().parameters.size() == 2) {
            if (S1.equals(d.variableName())) {
                Assert.assertEquals(S1_P0 + " + abc", d.currentValue().toString());
                Assert.assertEquals(MultiLevel.EFFECTIVE, MultiLevel.value(d.getPropertyOfCurrentValue(VariableProperty.NOT_NULL), MultiLevel.NOT_NULL));
                Assert.assertFalse(d.hasProperty(VariableProperty.NOT_NULL));
                Assert.assertTrue(d.currentValue().isInstanceOf(StringConcat.class));
            }
            if ("1".equals(d.statementId()) && S2.equals(d.variableName())) {
                Assert.assertSame(UnknownValue.EMPTY, d.variableInfo().getStateOnAssignment());
            }
            if ("2".equals(d.statementId()) && S2.equals(d.variableName())) {
                // stateOnAssignment has to be copied from statement 1
                Assert.assertSame(UnknownValue.EMPTY, d.variableInfo().getStateOnAssignment());
            }
        }

    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        TypeInfo stringType = d.evaluationContext().getPrimitives().stringTypeInfo;
        Assert.assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE, stringType.typeAnalysis.get().getProperty(VariableProperty.IMMUTABLE));
        int iteration = d.iteration();
        MethodInfo methodInfo = d.methodInfo();

        if ("setS4".equals(methodInfo.name) && iteration >= 1) {
            // @NotModified decided straight away, @Identity as well
            Assert.assertEquals(Level.FALSE, d.parameterAnalyses().get(0).getProperty(VariableProperty.MODIFIED));
        }

        // there is no size restriction
        if (iteration > 0) {
            FieldInfo s1 = methodInfo.typeInfo.typeInspection.getPotentiallyRun().fields.stream().filter(f -> "s1".equals(f.name)).findFirst().orElseThrow();
            if ("toString".equals(methodInfo.name) || FINAL_CHECKS.equals(methodInfo.name)) {
                int notNull = d.getFieldAsVariable(s1).getProperty(VariableProperty.NOT_NULL);
                Assert.assertEquals(MultiLevel.MUTABLE, notNull);
            }
        }
    };


    EvaluationResultVisitor evaluationResultVisitor = d -> {
        if (FINAL_CHECKS.equals(d.methodInfo().name) && "1".equals(d.statementId())) {
            Assert.assertEquals(StatementAnalyser.STEP_4, d.step());
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

    @Test
    public void test2() {
        String s1 = null;
        String s2 = null;
        String s3 = s1 + s2;
        Assert.assertEquals("nullnull", s3);
    }
}
