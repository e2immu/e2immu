package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.annotation.AnnotationMode;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Set;

public class TestSimpleNotModified2 extends CommonTestRunner {
    public TestSimpleNotModified2() {
        super(true);
    }

    @Test
    public void test() throws IOException {
        // Both ERROR and WARN in Example2bis
        testClass("SimpleNotModified2", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

}
