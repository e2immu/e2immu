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
        super(true);
    }



    @Test
    public void test_0() throws IOException {
        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo collection = typeMap.get(Collection.class);
            Assert.assertNotNull(collection);
            MethodInfo stream = collection.findUniqueMethod("stream" ,0);

            Assert.assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL, stream.methodAnalysis.get().getProperty(VariableProperty.NOT_NULL_EXPRESSION));
        };

        testClass("MethodReferences_0", 0, 0, new DebugConfiguration.Builder()
                .addTypeMapVisitor(typeMapVisitor)
                .build());
    }

    @Test
    public void test_1() throws IOException {
        testClass("MethodReferences_1", 0, 2, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_2() throws IOException {
        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo map = typeMap.get(Map.class);
            MethodInfo put = map.findUniqueMethod("put", 2);
            Assert.assertEquals(Level.TRUE, put.methodAnalysis.get().getProperty(VariableProperty.MODIFIED_METHOD));
            MethodInfo forEach = map.findUniqueMethod("forEach", 1);
            Assert.assertEquals(Level.FALSE, forEach.methodAnalysis.get().getProperty(VariableProperty.MODIFIED_METHOD));
        };

        testClass("MethodReferences_2", 0, 2, new DebugConfiguration.Builder()
                .addTypeMapVisitor(typeMapVisitor)
                .build());
    }


    @Test
    public void test_3() throws IOException {
        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo map = typeMap.get(Map.class);
            MethodInfo put = map.findUniqueMethod("get", 1);
            Assert.assertEquals(Level.FALSE, put.methodAnalysis.get().getProperty(VariableProperty.MODIFIED_METHOD));
            MethodInfo forEach = map.findUniqueMethod("forEach", 1);
            Assert.assertEquals(Level.FALSE, forEach.methodAnalysis.get().getProperty(VariableProperty.MODIFIED_METHOD));
        };

        testClass("MethodReferences_3", 0, 0, new DebugConfiguration.Builder()
                .addTypeMapVisitor(typeMapVisitor)
                .build());
    }

}
