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

public class TestTypeParameters extends CommonTestRunner {
    public TestTypeParameters() {
        super(true);
    }

    TypeMapVisitor typeMapVisitor = typeContext -> {
        TypeInfo collection = typeContext.getFullyQualified(Collection.class);
        Assert.assertNotNull(collection);
        MethodInfo stream = collection.typeInspection.get().methods().stream().filter(m -> m.name.equals("stream")).findAny().orElseThrow();
        Assert.assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL, stream.methodAnalysis.get().getProperty(VariableProperty.NOT_NULL));
    };

    @Test
    public void test() throws IOException {
        testClass("TypeParameters", 0, 0, new DebugConfiguration.Builder()
                .addTypeContextVisitor(typeMapVisitor)
                .build());
    }

}
