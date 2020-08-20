package org.e2immu.analyser.parser;

import org.e2immu.analyser.config.DebugConfiguration;
import org.junit.Test;

import java.io.IOException;

public class TestExampleManualVariableFinal extends CommonTestRunner {
    @Test
    public void test() throws IOException {
        testClass("ExampleManualVariableFinal", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

}
