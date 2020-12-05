package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MethodAnalysis;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.expression.PropertyWrapper;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class Test_10_IdentityChecks extends CommonTestRunner {
    public Test_10_IdentityChecks() {
        super(true);
    }

    private static final String IDEM = "org.e2immu.analyser.testexample.IdentityChecks.idem(String)";
    private static final String IDEM_S = IDEM + ":0:s";
    private static final String IDEM3 = "org.e2immu.analyser.testexample.IdentityChecks.idem3(String)";
    private static final String IDEM3_S = IDEM3 + ":0:s";

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if (d.methodInfo().name.equals("idem") && IDEM_S.equals(d.variableName())) {
            if ("0".equals(d.statementId())) {
                // strings are @NM by definition
                Assert.assertEquals(Level.FALSE, d.getProperty(VariableProperty.MODIFIED));
                Assert.assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE, d.getProperty(VariableProperty.IMMUTABLE));
                Assert.assertEquals(Level.TRUE, d.getProperty(VariableProperty.CONTAINER));

                Assert.assertEquals(Level.READ_ASSIGN_ONCE, d.getProperty(VariableProperty.READ)); // read 1x
                // there is an explicit @NotNull on the first parameter of debug
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getPropertyOfCurrentValue(VariableProperty.NOT_NULL));
            } else if ("1".equals(d.statementId())) {
                Assert.assertEquals(Level.FALSE, d.getProperty(VariableProperty.MODIFIED));
                Assert.assertEquals(Level.READ_ASSIGN_MULTIPLE_TIMES, d.getProperty(VariableProperty.READ)); // read 2x
                // there is an explicit @NotNull on the first parameter of debug
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getPropertyOfCurrentValue(VariableProperty.NOT_NULL));
            } else Assert.fail();
        }
        if (d.methodInfo().name.equals("idem3") && IDEM3_S.equals(d.variableName())) {
            // there is an explicit @NotNull on the first parameter of debug
            Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getPropertyOfCurrentValue(VariableProperty.NOT_NULL));
        }
    };

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if ("idem2".equals(d.methodInfo().name) && "1".equals(d.statementId())) {
            // false because static method
            Assert.assertEquals(Level.FALSE, d.getThisAsVariable().getProperty(VariableProperty.METHOD_CALLED));
        }
        if ("idem3".equals(d.methodInfo().name) && "1.0.0".equals(d.statementId())) {
            Expression value = d.statementAnalysis().stateData.valueOfExpression.get();
            Assert.assertTrue(value instanceof PropertyWrapper);
            Expression valueInside = ((PropertyWrapper) value).value;
            Assert.assertTrue(valueInside instanceof PropertyWrapper);
            Expression valueInside2 = ((PropertyWrapper) valueInside).value;
            Assert.assertTrue(valueInside2 instanceof VariableExpression);
            // check that isInstanceOf bypasses the wrappers
            Assert.assertTrue(value.isInstanceOf(VariableExpression.class));
            Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(value, VariableProperty.NOT_NULL));
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        MethodAnalysis methodAnalysis = d.methodAnalysis();

        if ("idem".equals(d.methodInfo().name)) {

            VariableInfo tv = d.getReturnAsVariable();
            Assert.assertFalse(tv.hasProperty(VariableProperty.MODIFIED));

            // @NotModified decided straight away, @Identity as well
            Assert.assertEquals(Level.FALSE, methodAnalysis.getProperty(VariableProperty.MODIFIED));
            Assert.assertEquals(Level.TRUE, methodAnalysis.getProperty(VariableProperty.IDENTITY));
        }

        if ("idem2".equals(d.methodInfo().name)) {
            // we do not need an extra iteration to find out about the modification status of idem,
            // because idem is processed before idem2
            Assert.assertEquals(Level.FALSE, methodAnalysis.getProperty(VariableProperty.MODIFIED));
            Assert.assertEquals(Level.TRUE, methodAnalysis.getProperty(VariableProperty.IDENTITY));
        }

        if ("idem3".equals(d.methodInfo().name)) {
            Assert.assertEquals(Level.TRUE, methodAnalysis.getProperty(VariableProperty.IDENTITY));

            Assert.assertEquals(Level.FALSE, methodAnalysis.getProperty(VariableProperty.MODIFIED));

            VariableInfo vi = d.getReturnAsVariable();
            Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, vi.getProperty(VariableProperty.NOT_NULL));

            // combining both, we obtain:
            //Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, methodAnalysis.getProperty(VariableProperty.NOT_NULL));
        }
        if ("idem4".equals(d.methodInfo().name)) {
            Assert.assertEquals(Level.TRUE, methodAnalysis.getProperty(VariableProperty.IDENTITY));
            Assert.assertEquals(Level.FALSE, methodAnalysis.getProperty(VariableProperty.MODIFIED));
        }
    };

    @Test
    public void test() throws IOException {
        testClass("IdentityChecks", 0, 0, new DebugConfiguration.Builder()
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().setSkipTransformations(true).build());
    }

}
