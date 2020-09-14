package org.e2immu.analyser.parser;

import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.e2immu.analyser.model.MethodInfo;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Set;

public class TestFunctionalInterfaceModified3 extends CommonTestRunner {

    MethodAnalyserVisitor methodAnalyserVisitor = (iteration, methodInfo) -> {
        MethodInfo staticallyExposing = methodInfo.typeInfo.findUniqueMethod("staticallyExposing", 2);
        MethodInfo expose3 = methodInfo.typeInfo.findUniqueMethod("expose3", 1);
        if ("expose3".equals(methodInfo.name)) {
            Assert.assertTrue(methodInfo.methodAnalysis.get().copyModificationStatusFrom.isSet(staticallyExposing));
        }
        if ("expose4".equals(methodInfo.name)) {
            Assert.assertTrue(methodInfo.methodAnalysis.get().copyModificationStatusFrom.isSet(expose3));
        }
    };

    // two potential null pointer warnings

    @Test
    public void test() throws IOException {
        testClass("FunctionalInterfaceModified3", 0, 2, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

}
