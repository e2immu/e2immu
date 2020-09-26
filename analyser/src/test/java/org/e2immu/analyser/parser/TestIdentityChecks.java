package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.TransferValue;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MethodAnalysis;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.abstractvalue.PropertyWrapper;
import org.e2immu.analyser.model.abstractvalue.VariableValue;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class TestIdentityChecks extends CommonTestRunner {
    public TestIdentityChecks() {
        super(true);
    }

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if (d.methodInfo.name.equals("idem") && "s".equals(d.variableName)) {
            if ("0".equals(d.statementId)) {
                // strings are @NM by definition
                Assert.assertEquals(Level.FALSE, (int) d.properties.get(VariableProperty.MODIFIED));
                Assert.assertEquals(Level.READ_ASSIGN_ONCE, (int) d.properties.get(VariableProperty.READ)); // read 1x
                // there is an explicit @NotNull on the first parameter of debug
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.currentValue.getPropertyOutsideContext(VariableProperty.NOT_NULL));
            } else if ("1".equals(d.statementId)) {
                Assert.assertEquals(Level.FALSE, (int) d.properties.get(VariableProperty.MODIFIED));
                Assert.assertEquals(Level.READ_ASSIGN_MULTIPLE_TIMES, (int) d.properties.get(VariableProperty.READ)); // read 2x
                // there is an explicit @NotNull on the first parameter of debug
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.currentValue.getPropertyOutsideContext(VariableProperty.NOT_NULL));
            } else Assert.fail();
        }
        if (d.methodInfo.name.equals("idem3") && "s".equals(d.variableName)) {
            // there is an explicit @NotNull on the first parameter of debug
            Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.currentValue.getPropertyOutsideContext(VariableProperty.NOT_NULL));
        }
    };

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if ("idem3".equals(d.methodInfo.name) && "1.0.0".equals(d.statementId)) {
            Value value = d.statementAnalysis.valueOfExpression.get();
            Assert.assertTrue(value instanceof PropertyWrapper);
            Value valueInside = ((PropertyWrapper) value).value;
            Assert.assertTrue(valueInside instanceof PropertyWrapper);
            Value valueInside2 = ((PropertyWrapper) valueInside).value;
            Assert.assertTrue(valueInside2 instanceof VariableValue);

            Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, value.getPropertyOutsideContext(VariableProperty.NOT_NULL));
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = (iteration, methodInfo) -> {
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        if ("idem".equals(methodInfo.name)) {

            TransferValue tv = methodAnalysis.returnStatementSummaries.get("1");
            Assert.assertFalse(tv.properties.isSet(VariableProperty.MODIFIED));

            // @NotModified decided straight away, @Identity as well
            Assert.assertEquals(Level.FALSE, methodAnalysis.getProperty(VariableProperty.MODIFIED));
            Assert.assertEquals(Level.TRUE, methodAnalysis.getProperty(VariableProperty.IDENTITY));
        }

        if ("idem3".equals(methodInfo.name)) {
            TransferValue tv1 = methodAnalysis.returnStatementSummaries.get("1.0.0");
            Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, tv1.getProperty(VariableProperty.NOT_NULL));
            TransferValue tv2 = methodAnalysis.returnStatementSummaries.get("1.1.0");
            Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, tv2.getProperty(VariableProperty.NOT_NULL));
            // combining both, we obtain:
            //Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, methodAnalysis.getProperty(VariableProperty.NOT_NULL));
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
