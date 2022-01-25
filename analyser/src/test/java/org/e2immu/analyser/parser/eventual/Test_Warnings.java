package org.e2immu.analyser.parser.eventual;

import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class Test_Warnings extends CommonTestRunner {

    // ensure that there is no warning emitted to access a modifying method on an E2Immutable type
    @Test
    public void test_0() throws IOException {
        testClass("Warnings_0", 0, 0, new DebugConfiguration.Builder()
                .build());
    }
}
