package org.e2immu.analyser.parser;

import org.e2immu.analyser.config.DebugConfiguration;
import org.junit.Test;

import java.io.IOException;

public class TestInlineMethods extends CommonTestRunner {
    public TestInlineMethods() {
        super(false);
    }


    @Test
    public void test() throws IOException {
        testClass("InlineMethods", 0, new DebugConfiguration.Builder()
                .build());
    }

}
