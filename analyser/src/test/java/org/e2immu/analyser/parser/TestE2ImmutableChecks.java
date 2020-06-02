package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.NumberedStatement;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.config.StatementAnalyserVisitor;
import org.e2immu.analyser.config.TypeContextVisitor;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.ConstrainedNumericValue;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class TestE2ImmutableChecks extends CommonTestRunner {
    public TestE2ImmutableChecks() {
        super(true);
    }


    @Test
    public void test() throws IOException {
        testClass("E2ImmutableChecks", 0, 0, new DebugConfiguration.Builder()

                .build());
    }

}
