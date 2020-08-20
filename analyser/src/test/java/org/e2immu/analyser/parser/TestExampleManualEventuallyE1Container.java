package org.e2immu.analyser.parser;

import org.e2immu.analyser.config.DebugConfiguration;
import org.junit.Test;

import java.io.IOException;

public class TestExampleManualEventuallyE1Container extends CommonTestRunner {
    @Test
    public void test() throws IOException {
        testClass("ExampleManualEventuallyE1Container", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

}
