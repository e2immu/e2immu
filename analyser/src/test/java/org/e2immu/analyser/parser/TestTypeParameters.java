package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.TypeContextVisitor;
import org.e2immu.analyser.model.*;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;

public class TestTypeParameters extends CommonTestRunner {
    public TestTypeParameters() {
        super(true);
    }

    TypeContextVisitor typeContextVisitor = new TypeContextVisitor() {
        @Override
        public void visit(TypeContext typeContext) {
            TypeInfo collection = typeContext.getFullyQualified(Collection.class);
            Assert.assertNotNull(collection);
            MethodInfo stream = collection.typeInspection.get().methods.stream().filter(m -> m.name.equals("stream")).findAny().orElseThrow();
            Assert.assertEquals(MultiLevel.compose(Level.TRUE, Level.NOT_NULL_1), stream.methodAnalysis.get().getProperty(VariableProperty.NOT_NULL));
        }
    };

    @Test
    public void test() throws IOException {
        testClass("TypeParameters", 0, 0, new DebugConfiguration.Builder()
                .addTypeContextVisitor(typeContextVisitor)
                .build());
    }

}
