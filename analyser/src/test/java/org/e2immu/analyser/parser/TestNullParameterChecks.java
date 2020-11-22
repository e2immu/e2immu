package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.config.StatementAnalyserVisitor;
import org.e2immu.analyser.config.TypeMapVisitor;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.abstractvalue.InlineValue;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class TestNullParameterChecks extends CommonTestRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestNullParameterChecks.class);

    public TestNullParameterChecks() {
        super(true);
    }
    //     public void method2(@NotNull String s) {
    //        this.s = Objects.requireNonNull(s);
    //    }

    //  public void method8Implicit(@NotNull(type = AnnotationType.VERIFY_ABSENT) String s) {
    //    if (s != null) { // 0
    //        this.s = s.strip(); // 0.0.0
    //   } else {
    //        this.s = "abc";
    //    }
    //}

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if (d.methodInfo().name.equals("method2")) {

            if (!d.statementId().equals("0")) Assert.fail();

            if ("s".equals(d.variableName())) {
                LOGGER.info("Properties of s it iteration {} are {}, value {}", d.iteration(), d.properties(), d.currentValue());
                Assert.assertEquals(Level.READ_ASSIGN_ONCE, d.getProperty(VariableProperty.READ));
                Assert.assertEquals(Level.FALSE, d.getProperty(VariableProperty.MODIFIED)); //FALSE at level 1
                return;
            }
            if ("NullParameterChecks.this.s".equals(d.variableName())) {
                int assigned = d.getProperty(VariableProperty.ASSIGNED);
                int read = d.getProperty(VariableProperty.READ);
                Assert.assertEquals(Level.READ_ASSIGN_ONCE, assigned);
                Assert.assertEquals(Level.TRUE, d.getProperty(VariableProperty.LAST_ASSIGNMENT_GUARANTEED_TO_BE_REACHED));
                Assert.assertTrue(assigned > read);
                return;
            }
        }
        if ("method8Implicit".equals(d.methodInfo().name)) {
            // the parameter "s" is not present in the variable d.properties at the 0.0.0 level; it is one higher
            if ("0".equals(d.statementId())) {
                if ("s".equals(d.variableName())) {
                    // we should know straight away (without delay) that the strip method on String is "safe"
                    Assert.assertEquals(Level.FALSE, d.getProperty(VariableProperty.MODIFIED));
                    Assert.assertEquals(Level.READ_ASSIGN_MULTIPLE_TIMES, d.getProperty(VariableProperty.READ));
                } else if ("NullParameterChecks.this.s".equals(d.variableName())) {
                    // we do NOT have assigned 2x here, because the if-statement blocks are not guaranteed to be executed
                    Assert.assertEquals(Level.READ_ASSIGN_ONCE, d.getProperty(VariableProperty.ASSIGNED));
                }
            }
        }
        if (("method11Lambda".equals(d.methodInfo().name) || "method12Lambda".equals(d.methodInfo().name))
                && "supplier".equals(d.variableName()) && "0".equals(d.statementId())) {
            if (d.iteration() == 0) {
                Assert.assertSame(UnknownValue.NO_VALUE, d.currentValue());
            } else {
                Assert.assertTrue("Have " + d.currentValue().getClass(), d.currentValue() instanceof InlineValue);
                Assert.assertEquals("inline get on t.trim() + .", d.currentValue().toString());
                InlineValue inlineValue = (InlineValue) d.currentValue();
                Assert.assertEquals(Level.FALSE, inlineValue.methodInfo.methodAnalysis.get().getProperty(VariableProperty.MODIFIED));
            }
        }

    };


    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if ("method9".equals(d.methodInfo().name) && d.iteration() > 0) {
            Assert.assertNotNull(d.haveError(Message.CONDITION_EVALUATES_TO_CONSTANT)); // TODO
        }
    };

    TypeMapVisitor typeMapVisitor = typeMap -> {
        TypeInfo objects = typeMap.get("java.util.Objects");
        MethodInfo requireNonNull = objects.typeInspection.get()
                .methods().stream().filter(mi -> "requireNonNull".equals(mi.name) &&
                        1 == mi.methodInspection.get().getParameters().size()).findFirst().orElseThrow();
        Assert.assertEquals(Level.TRUE, requireNonNull.methodAnalysis.get().getProperty(VariableProperty.IDENTITY));
        Assert.assertEquals(Level.FALSE, requireNonNull.methodAnalysis.get().getProperty(VariableProperty.MODIFIED));

    };

    @Test
    public void test() throws IOException {
        testClass("NullParameterChecks", 0, 1, new DebugConfiguration.Builder()
                .addTypeContextVisitor(typeMapVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

}
