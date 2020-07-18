package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.TypeContextVisitor;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.TypeInfo;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.stream.Stream;

public class TestMethodMustBeStatic extends CommonTestRunner {
    public TestMethodMustBeStatic() {
        super(false);
    }

    TypeContextVisitor typeContextVisitor = typeContext -> {
        TypeInfo stream = typeContext.getFullyQualified(Stream.class);
        Assert.assertNotNull(stream);
        MethodInfo of = stream.typeInspection.get().methods.stream().filter(m -> m.name.equals("of")).findAny().orElseThrow();
        Assert.assertEquals(MultiLevel.NULLABLE, of.methodAnalysis.get().getProperty(VariableProperty.NOT_NULL));
    };

    @Test
    public void test() throws IOException {
        testClass("MethodMustBeStatic", 0, 1, new DebugConfiguration.Builder()
                .addTypeContextVisitor(typeContextVisitor)
                .build());
    }

}
