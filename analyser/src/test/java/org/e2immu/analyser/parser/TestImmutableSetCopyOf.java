package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.TypeContextVisitor;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;

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
