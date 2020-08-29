package org.e2immu.analyser.parser;

import org.e2immu.analyser.config.DebugConfiguration;
import org.junit.Test;

import java.io.IOException;

public class TestSimpleNotModified1 extends CommonTestRunner {
    public TestSimpleNotModified1() {
        super(true);
    }

    @Test
    public void test() throws IOException {
        // Both ERROR and WARN in Example2bis
        testClass("SimpleNotModified1", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

}
