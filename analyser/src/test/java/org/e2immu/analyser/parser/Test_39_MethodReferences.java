package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.TypeMapVisitor;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.TypeInfo;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

public class Test_39_MethodReferences extends CommonTestRunner {
    public Test_39_MethodReferences() {
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
    public void test_0() throws IOException {
        testClass("MethodReferences_0", 0, 2, new DebugConfiguration.Builder()
                .addTypeMapVisitor(typeMapVisitor)
                .build());
    }

    @Test
    public void test_1() throws IOException {
        testClass("MethodReferences_1", 0, 2, new DebugConfiguration.Builder()
                .addTypeMapVisitor(typeMapVisitor)
                .build());
    }

    @Test
    public void test_2() throws IOException {
        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo map = typeMap.get(Map.class);
            MethodInfo put = map.findUniqueMethod("put", 2);
            Assert.assertEquals(Level.TRUE, put.methodAnalysis.get().getProperty(VariableProperty.MODIFIED_METHOD));
        };
        
        testClass("MethodReferences_2", 0, 2, new DebugConfiguration.Builder()
                .addTypeMapVisitor(typeMapVisitor)
                .build());
    }

}
