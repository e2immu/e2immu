package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.TypeContextVisitor;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;

public class TestMethodReferences extends CommonTestRunner {
    public TestMethodReferences() {
        super(false);
    }

    TypeContextVisitor typeContextVisitor = new TypeContextVisitor() {
        @Override
        public void visit(TypeContext typeContext) {
            TypeInfo collection = typeContext.getFullyQualified(Collection.class);
            Assert.assertNotNull(collection);
            MethodInfo stream = collection.typeInspection.get().methods.stream().filter(m -> m.name.equals("stream")).findAny().orElseThrow();

            // NOTE: 0 because we do not parse the AnnotatedAPIs. This causes a warning!
            Assert.assertEquals(0, stream.methodAnalysis.get().getProperty(VariableProperty.NOT_NULL));
        }
    };

    @Test
    public void test() throws IOException {
        testClass("MethodReferences", 0, 1, new DebugConfiguration.Builder()
                .addTypeContextVisitor(typeContextVisitor)
                .build());
    }

}
