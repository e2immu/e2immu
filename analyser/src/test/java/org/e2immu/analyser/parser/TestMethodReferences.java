package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.TypeMapVisitor;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.TypeInfo;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;

public class TestMethodReferences extends CommonTestRunner {
    public TestMethodReferences() {
        super(false);
    }

    TypeMapVisitor typeMapVisitor = typeMap -> {
        TypeInfo collection = typeMap.get(Collection.class);
        Assert.assertNotNull(collection);
        MethodInfo stream = collection.typeInspection.get().methods().stream().filter(m -> m.name.equals("stream")).findAny().orElseThrow();

        // NOTE: 0 because we do not parse the AnnotatedAPIs. This causes a warning!
        Assert.assertEquals(MultiLevel.FALSE, stream.methodAnalysis.get().getProperty(VariableProperty.NOT_NULL_EXPRESSION));
    };

    @Test
    public void test() throws IOException {
        testClass("MethodReferences", 0, 1, new DebugConfiguration.Builder()
                .addTypeMapVisitor(typeMapVisitor)
                .build());
    }

}
