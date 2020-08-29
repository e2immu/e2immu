package org.e2immu.analyser.parser;

import org.e2immu.analyser.config.DebugConfiguration;
import org.junit.Test;

import java.io.IOException;

public class TestFunctionalInterfaceModified2 extends CommonTestRunner {


    @Test
    public void test() throws IOException {
        testClass("FunctionalInterfaceModified2", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

}
