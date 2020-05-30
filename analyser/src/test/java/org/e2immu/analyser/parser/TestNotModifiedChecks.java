package org.e2immu.analyser.parser;

import org.e2immu.analyser.config.DebugConfiguration;
import org.junit.Test;

import java.io.IOException;

public class TestNotModifiedChecks extends CommonTestRunner {
    public TestNotModifiedChecks() {
        super(true);
    }


    @Test
    public void test() throws IOException {
        testClass("NotModifiedChecks", 0, 0, new DebugConfiguration.Builder()

                .build());
    }

}
