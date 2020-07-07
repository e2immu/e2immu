package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.TransferValue;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.e2immu.analyser.config.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.config.StatementAnalyserVisitor;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MethodAnalysis;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class TestIdentityChecks extends CommonTestRunner {
    public TestIdentityChecks() {
        super(true);
    }

    StatementAnalyserVariableVisitor statementAnalyserVisitor = d -> {
        if (d.methodInfo.name.equals("idem") && "s".equals(d.variableName)) {
            if ("0".equals(d.statementId)) {
                // strings are @NM by definition
                Assert.assertEquals(Level.FALSE, (int) d.properties.get(VariableProperty.MODIFIED));
                Assert.assertEquals(1, (int) d.properties.get(VariableProperty.READ)); // read 1x
                // there is an explicit @NotNull on the first parameter of debug
                Assert.assertEquals(1, (int) d.currentValue.getPropertyOutsideContext(VariableProperty.NOT_NULL));
            } else if ("1".equals(d.statementId)) {
                Assert.assertEquals(Level.FALSE, (int) d.properties.get(VariableProperty.MODIFIED));
                Assert.assertEquals(3, (int) d.properties.get(VariableProperty.READ)); // read 2x
                // there is an explicit @NotNull on the first parameter of debug
                Assert.assertEquals(1, (int) d.currentValue.getPropertyOutsideContext(VariableProperty.NOT_NULL));
            } else Assert.fail();
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = (iteration, methodInfo) -> {
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        if ("idem".equals(methodInfo.name)) {

            TransferValue tv = methodAnalysis.returnStatementSummaries.get("1");
            Assert.assertFalse(tv.properties.isSet(VariableProperty.MODIFIED));

            // @NotModified decided straight away, @Identity as well
            Assert.assertEquals(Level.FALSE, methodAnalysis.getProperty(VariableProperty.MODIFIED));
            Assert.assertEquals(1, methodAnalysis.getProperty(VariableProperty.IDENTITY));
        }
    };

    @Test
    public void test() throws IOException {
        testClass("IdentityChecks", 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

}
