package org.e2immu.analyser.parser;

import org.e2immu.analyser.config.DebugConfiguration;
import org.junit.Test;

import java.io.IOException;

public class TestInlineAndSizeChecks extends CommonTestRunner {
    public TestInlineAndSizeChecks() {
        super(true);
    }


    @Test
    public void test() throws IOException {
        testClass("InlineAndSizeChecks", 0, new DebugConfiguration.Builder()
                .build());
    }

}
