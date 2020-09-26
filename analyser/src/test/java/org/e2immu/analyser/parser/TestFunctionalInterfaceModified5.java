package org.e2immu.analyser.parser;

import org.e2immu.analyser.config.DebugConfiguration;
import org.junit.Test;

import java.io.IOException;

public class TestFunctionalInterfaceModified5 extends CommonTestRunner {

    public TestFunctionalInterfaceModified5() {
        super(true);
    }

    @Test
    public void test() throws IOException {
        testClass("FunctionalInterfaceModified5", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

}
