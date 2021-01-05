package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.config.StatementAnalyserVisitor;
import org.e2immu.analyser.config.TypeMapVisitor;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.expression.InlinedMethod;
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

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if (d.methodInfo().name.equals("method2")) {

            if (!d.statementId().equals("0")) Assert.fail();

            if ("s".equals(d.variableName())) {
                LOGGER.info("Properties of s it iteration {} are {}, value {}", d.iteration(), d.properties(), d.currentValue());
                Assert.assertTrue(d.variableInfo().isRead());
                Assert.assertEquals(Level.FALSE, d.getProperty(VariableProperty.MODIFIED)); //FALSE at level 1
                return;
            }
            if ("NullParameterChecks.this.s".equals(d.variableName())) {
                Assert.assertTrue(d.variableInfo().isAssigned());
                Assert.assertEquals(Level.TRUE, d.getProperty(VariableProperty.LAST_ASSIGNMENT_GUARANTEED_TO_BE_REACHED));
                Assert.assertTrue(d.variableInfo().getAssignmentId().compareTo(d.variableInfo().getReadId()) > 0);
                return;
            }
        }
        if ("method8Implicit".equals(d.methodInfo().name)) {
            // the parameter "s" is not present in the variable d.properties at the 0.0.0 level; it is one higher
            if ("0".equals(d.statementId())) {
                if ("s".equals(d.variableName())) {
                    // we should know straight away (without delay) that the strip method on String is "safe"
                    Assert.assertEquals(Level.FALSE, d.getProperty(VariableProperty.MODIFIED));
                    Assert.assertTrue(d.variableInfo().isRead());
                } else if ("NullParameterChecks.this.s".equals(d.variableName())) {
                    // we do NOT have assigned 2x here, because the if-statement blocks are not guaranteed to be executed
                    Assert.assertTrue(d.variableInfo().isAssigned());
                }
            }
        }
        if (("method11Lambda".equals(d.methodInfo().name) || "method12Lambda".equals(d.methodInfo().name))
                && "supplier".equals(d.variableName()) && "0".equals(d.statementId())) {
            if (d.iteration() == 0) {
                Assert.assertSame(EmptyExpression.NO_VALUE, d.currentValue());
            } else {
                Assert.assertTrue("Have " + d.currentValue().getClass(), d.currentValue() instanceof InlinedMethod);
                Assert.assertEquals("inline get on t.trim() + .", d.currentValue().toString());
                InlinedMethod inlineValue = (InlinedMethod) d.currentValue();
                Assert.assertEquals(Level.FALSE, inlineValue.methodInfo().methodAnalysis.get().getProperty(VariableProperty.MODIFIED));
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
                .addTypeMapVisitor(typeMapVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

}
