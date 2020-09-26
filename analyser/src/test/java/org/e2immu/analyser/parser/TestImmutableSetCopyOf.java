package org.e2immu.analyser.parser;

import org.e2immu.analyser.config.DebugConfiguration;
import org.junit.Test;

import java.io.IOException;

public class TestImmutableSetCopyOf extends CommonTestRunner {
    public TestImmutableSetCopyOf() {
        super(true);
    }

    @Test
    public void test() throws IOException {
        testClass("ImmutableSetCopyOf", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

}
