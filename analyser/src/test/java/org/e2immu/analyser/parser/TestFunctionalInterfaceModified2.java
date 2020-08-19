package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.FieldAnalyserVisitor;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.statement.ReturnStatement;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class TestFunctionalInterfaceModified2 extends CommonTestRunner {


    @Test
    public void test() throws IOException {
        testClass("FunctionalInterfaceModified2", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

}
