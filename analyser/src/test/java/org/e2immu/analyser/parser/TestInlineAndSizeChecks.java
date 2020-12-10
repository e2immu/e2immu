package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.e2immu.analyser.config.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.config.TypeMapVisitor;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class TestInlineAndSizeChecks extends CommonTestRunner {
    public TestInlineAndSizeChecks() {
        super(true);
    }

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("method1".equals(d.methodInfo().name) && "1".equals(d.statementId()) && "l1".equals(d.variableName()) && d.iteration() > 0) {
            Assert.assertEquals("in1.length(),?>=0", d.currentValue().toString());
        }
        // TODO for now, in2.toLowerCase().length() is not reduced to in2.length()
        if ("method2".equals(d.methodInfo().name) && "0".equals(d.statementId()) && "l2".equals(d.variableName()) && d.iteration() > 0) {
            Assert.assertEquals("in2.toLowerCase().length(),?>=0", d.currentValue().toString());
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        if ("len".equals(d.methodInfo().name) && d.iteration() > 0) {
            Assert.assertEquals("inline len on null == s?(-1):s.length(),?>=0", d.methodAnalysis()
                    .getSingleReturnValue().toString());
        }
    };

    TypeMapVisitor typeMapVisitor = typeMap -> {
        TypeInfo stringTypeInfo = typeMap.get(String.class);
        Assert.assertSame(stringTypeInfo, typeMap.getPrimitives().stringTypeInfo);
        MethodInfo length = stringTypeInfo.findUniqueMethod("length", 0);
        int modified = length.methodAnalysis.get().getProperty(VariableProperty.MODIFIED);
        Assert.assertEquals(0, modified);
    };

    @Test
    public void test() throws IOException {
        testClass("InlineAndSizeChecks", 2, 0, new DebugConfiguration.Builder()
                .addTypeMapVisitor(typeMapVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

}
