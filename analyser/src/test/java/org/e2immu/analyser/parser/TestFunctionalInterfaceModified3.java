package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.MethodLevelData;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.e2immu.analyser.model.MethodInfo;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class TestFunctionalInterfaceModified3 extends CommonTestRunner {

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        MethodInfo staticallyExposing = d.methodInfo().typeInfo.findUniqueMethod("staticallyExposing", 2);
        MethodInfo expose3 = d.methodInfo().typeInfo.findUniqueMethod("expose3", 1);
        MethodLevelData methodLevelData = d.methodAnalysis().methodLevelData();
        if ("expose3".equals(d.methodInfo().name)) {
            Assert.assertTrue(methodLevelData.copyModificationStatusFrom.isSet(staticallyExposing));
        }
        if ("expose4".equals(d.methodInfo().name)) {
            Assert.assertTrue(methodLevelData.copyModificationStatusFrom.isSet(expose3));
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
