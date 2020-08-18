package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.e2immu.analyser.model.MethodInfo;
import org.junit.Test;

import java.io.IOException;

public class TestNotModified1Checks extends CommonTestRunner {

    public TestNotModified1Checks() {
        super(true);
    }

    MethodAnalyserVisitor methodAnalyserVisitor = (iteration, methodInfo) -> {
        if ("accept".equals(methodInfo.name)) {
            int modified = methodInfo.methodAnalysis.get().getProperty(VariableProperty.MODIFIED);
        }
    };

    @Test
    public void test() throws IOException {
        testClass("NotModified1Checks", 2, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

}
