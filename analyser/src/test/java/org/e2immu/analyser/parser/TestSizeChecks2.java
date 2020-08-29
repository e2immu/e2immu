package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.TypeContextVisitor;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;

public class TestSizeChecks2 extends CommonTestRunner {
    public TestSizeChecks2() {
        super(true);
    }


    TypeContextVisitor typeContextVisitor = new TypeContextVisitor() {
        @Override
        public void visit(TypeContext typeContext) {
            TypeInfo collection = typeContext.getFullyQualified(Collection.class);

            TypeInfo hashSet = typeContext.getFullyQualified(HashSet.class);
            MethodInfo constructor1 = hashSet.typeInspection.get().constructors.stream()
                    .filter(m -> m.methodInspection.get().parameters.size() == 1)
                    .filter(m -> m.methodInspection.get().parameters.get(0).parameterizedType.typeInfo == collection)
                    .findAny().orElseThrow();
            ParameterInfo param1Constructor1 = constructor1.methodInspection.get().parameters.get(0);
            int size = param1Constructor1.parameterAnalysis.get().getProperty(VariableProperty.SIZE_COPY);
            Assert.assertEquals(Level.SIZE_COPY_TRUE, size);
        }
    };

    @Test
    public void test() throws IOException {
        testClass("SizeChecks2", 0, 1, new DebugConfiguration.Builder()
                .addTypeContextVisitor(typeContextVisitor)
                .build());
    }

}
