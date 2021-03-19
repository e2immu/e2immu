package org.e2immu.analyser.parser;

import org.e2immu.analyser.config.DebugConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class Test_XX_SwitchExpression extends CommonTestRunner {
    public Test_XX_SwitchExpression() {
        super(false);
    }

    @Test
    public void test_0() throws IOException {
        testClass("SwitchExpression_0", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_1() throws IOException {
        testClass("SwitchExpression_1", 0, 1, new DebugConfiguration.Builder()
                .build());
    }

}
