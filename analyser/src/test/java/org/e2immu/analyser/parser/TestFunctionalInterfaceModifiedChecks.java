package org.e2immu.analyser.parser;

import org.e2immu.analyser.config.DebugConfiguration;
import org.junit.Test;

import java.io.IOException;

public class TestFunctionalInterfaceModifiedChecks extends CommonTestRunner {

    @Test
    public void test() throws IOException {
        testClass("FunctionalInterfaceModifiedChecks", 2 ,0, new DebugConfiguration.Builder()
                 .build());
    }

}
