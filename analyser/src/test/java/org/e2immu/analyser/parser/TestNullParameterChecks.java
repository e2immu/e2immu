package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.e2immu.analyser.config.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.config.StatementAnalyserVisitor;
import org.e2immu.analyser.model.Level;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class TestNullParameterChecks extends WithAnnotatedAPIs {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestNullParameterChecks.class);

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

    StatementAnalyserVariableVisitor statementAnalyserVisitor = (iteration, methodInfo, statementId, variableName,
                                                                 variable, currentValue, properties) -> {
        if (methodInfo.name.equals("method2")) {

            if (!statementId.equals("0")) Assert.fail();

            if ("s".equals(variableName)) {
                LOGGER.info("Properties of s it iteration {} are {}, value {}", iteration, properties, currentValue);
                Assert.assertEquals(Level.TRUE, (int) properties.get(VariableProperty.READ));
                Assert.assertEquals(2, (int) properties.get(VariableProperty.CONTENT_MODIFIED)); //FALSE at level 1
                Assert.assertEquals(Level.compose(Level.TRUE, 1), (int) properties.get(VariableProperty.IN_NOT_NULL_CONTEXT));
                return;
            }
            if ("NullParameterChecks.this.s".equals(variableName)) {
                LOGGER.info("Properties of this.s it iteration {} are {}, value {}", iteration, properties, currentValue);
                return;
            }
            Assert.fail();
        }
        if ("method8Implicit".equals(methodInfo.name)) {
            if ("0.0.0".equals(statementId)) {
                Assert.assertEquals("NullParameterChecks.this.s", variableName);
                // the parameter "s" is not present in the variable properties at the 0.0.0 level; it is one higher
            } else if ("0".equals(statementId)) {
                if ("s".equals(variableName)) {
                    // we should know straight away (without delay) that the strip method on String is "safe"
                    Assert.assertEquals(Level.compose(Level.FALSE, 1), (int) properties.get(VariableProperty.CONTENT_MODIFIED));
                    Assert.assertEquals(Level.compose(Level.TRUE, 1), (int) properties.get(VariableProperty.READ));
                } else if ("NullParameterChecks.this.s".equals(variableName)) {
                    Assert.assertTrue(Level.haveTrueAt(properties.get(VariableProperty.ASSIGNED), 1));
                } else Assert.fail();
            }
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = (iteration, methodInfo) -> {
        if ("method9".equals(methodInfo.name) && iteration >= 1) {
            Assert.assertTrue(methodInfo.methodAnalysis.returnStatements.get().get(0).errorValue.get());
        }
    };

    @Test
    public void test() throws IOException {
        testClass("NullParameterChecks", 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

}
