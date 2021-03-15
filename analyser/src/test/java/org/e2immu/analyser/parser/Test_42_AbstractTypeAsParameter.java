package org.e2immu.analyser.parser;

import org.e2immu.analyser.config.DebugConfiguration;
import org.junit.Test;

import java.io.IOException;

public class Test_42_AbstractTypeAsParameter extends CommonTestRunner {

    public Test_42_AbstractTypeAsParameter() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        testClass("AbstractTypeAsParameter_0", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_1() throws IOException {
        testClass("AbstractTypeAsParameter_1", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_2() throws IOException {
        testClass("AbstractTypeAsParameter_2", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_3() throws IOException {
        testClass("AbstractTypeAsParameter_3", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_4() throws IOException {
        testClass("AbstractTypeAsParameter_4", 1, 0, new DebugConfiguration.Builder()
                .build());
    }
}
