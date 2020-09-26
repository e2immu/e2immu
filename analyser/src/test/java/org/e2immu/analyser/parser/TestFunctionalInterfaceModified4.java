package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.TransferValue;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.e2immu.analyser.config.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.ParameterInfo;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class TestFunctionalInterfaceModified4 extends CommonTestRunner {

    public TestFunctionalInterfaceModified4() {
        super(true);
    }

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("visit3".equals(d.methodInfo.name) && "FunctionalInterfaceModified4.this.ts".equals(d.variableName)) {
            Assert.assertEquals(Level.FALSE, (int) d.properties.get(VariableProperty.MODIFIED));
            // if(d.iteration>0) Assert.assertEquals(Level.FALSE, (int) d.properties.get(VariableProperty.METHOD_DELAY));
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = (iteration, methodInfo) -> {
        int modified = methodInfo.methodAnalysis.get().getProperty(VariableProperty.MODIFIED);

        if ("doTheVisiting".equals(methodInfo.name)) {
            Assert.assertEquals(Level.FALSE, modified);
            ParameterInfo set = methodInfo.methodInspection.get().parameters.get(1);
            Assert.assertEquals("set", set.name);
            Assert.assertEquals(Level.FALSE, set.parameterAnalysis.get().getProperty(VariableProperty.MODIFIED));
            //    Assert.assertEquals(Level.IS_A_SIZE, set.parameterAnalysis.get().getProperty(VariableProperty.SIZE));
        }
        if ("visit2".equals(methodInfo.name) && iteration > 0) {
            Assert.assertEquals(Level.FALSE, modified);
        }
        if ("visit3".equals(methodInfo.name)) {
            if (iteration > 0) {
                Assert.assertEquals(Level.FALSE, modified);
            }
            FieldInfo ts = methodInfo.typeInfo.getFieldByName("ts", true);
            TransferValue tv = methodInfo.methodAnalysis.get().methodLevelData().fieldSummaries.get(ts);
            Assert.assertEquals(Level.TRUE, tv.properties.get(VariableProperty.READ));
            if (iteration > 1) {
                Assert.assertEquals(Level.FALSE, tv.properties.get(VariableProperty.MODIFIED));
            }
            MethodInfo doTheVisiting = methodInfo.typeInfo.findUniqueMethod("doTheVisiting", 2);
            Assert.assertTrue(methodInfo.methodAnalysis.get().methodLevelData().copyModificationStatusFrom.isSet(doTheVisiting));
        }
    };

    @Test
    public void test() throws IOException {
        testClass("FunctionalInterfaceModified4", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

}
