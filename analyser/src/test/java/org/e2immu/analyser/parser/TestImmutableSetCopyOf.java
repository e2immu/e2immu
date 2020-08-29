package org.e2immu.analyser.parser;

import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.TypeContextVisitor;
import org.junit.Test;

import java.io.IOException;

public class TestImmutableSetCopyOf extends CommonTestRunner {
    public TestImmutableSetCopyOf() {
        super(true);
    }

    TypeContextVisitor typeContextVisitor = new TypeContextVisitor() {
        @Override
        public void visit(TypeContext typeContext) {

        }
    };

    @Test
    public void test() throws IOException {
        testClass("ImmutableSetCopyOf", 0, 0, new DebugConfiguration.Builder()
                .addTypeContextVisitor(typeContextVisitor)
                .build());
    }

}
