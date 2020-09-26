package org.e2immu.analyser.parser;

import org.e2immu.analyser.config.DebugConfiguration;
import org.junit.Test;
import java.io.IOException;

public class TestFunctionalInterfaceModified6 extends CommonTestRunner {

    public TestFunctionalInterfaceModified6() {
        super(true);
    }

    @Test
    public void test() throws IOException {
        testClass("FunctionalInterfaceModified6", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

}
