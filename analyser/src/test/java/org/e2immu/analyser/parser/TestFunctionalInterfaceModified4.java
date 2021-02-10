package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableInfo;
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
        if ("visit3".equals(d.methodInfo().name) && "FunctionalInterfaceModified4.this.ts".equals(d.variableName())) {
            Assert.assertEquals(Level.FALSE, d.getProperty(VariableProperty.MODIFIED_VARIABLE));
            // if(d.iteration>0) Assert.assertEquals(Level.FALSE, (int) d.properties().get(VariableProperty.METHOD_DELAY));
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        int iteration = d.iteration();
        String name = d.methodInfo().name;
        int modified = d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD);

        if ("doTheVisiting".equals(name)) {
            Assert.assertEquals(Level.FALSE, modified);
            ParameterInfo set = d.methodInfo().methodInspection.get().getParameters().get(1);
            Assert.assertEquals("set", set.name);
            Assert.assertEquals(Level.FALSE, set.parameterAnalysis.get().getProperty(VariableProperty.MODIFIED_VARIABLE));
            //    Assert.assertEquals(Level.IS_A_SIZE, set.parameterAnalysis.get().getProperty(VariableProperty.SIZE));
        }
        if ("visit2".equals(name) && iteration > 0) {
            Assert.assertEquals(Level.FALSE, modified);
        }
        if ("visit3".equals(name)) {
            if (iteration > 0) {
                Assert.assertEquals(Level.FALSE, modified);
            }
            FieldInfo ts = d.methodInfo().typeInfo.getFieldByName("ts", true);
            VariableInfo vi = d.getFieldAsVariable(ts);
            assert vi != null;
            Assert.assertTrue(vi.isRead());
            if (iteration > 1) {
                Assert.assertEquals(Level.FALSE, vi.getProperty(VariableProperty.MODIFIED_VARIABLE));
            }
            MethodInfo doTheVisiting = d.methodInfo().typeInfo.findUniqueMethod("doTheVisiting", 2);
            Assert.assertTrue(d.methodAnalysis().methodLevelData().copyModificationStatusFrom.isSet(doTheVisiting));
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
