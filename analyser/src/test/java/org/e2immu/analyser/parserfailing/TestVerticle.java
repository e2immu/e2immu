package org.e2immu.analyser.parserfailing;

import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.junit.Test;

import java.io.IOException;

public class TestVerticle extends CommonTestRunner {

    public TestVerticle() {
        super(true);
    }

    @Test
    public void test() throws IOException {
        testClass("Verticle", 0, 0, new DebugConfiguration.Builder()
                .build());
    }
}
