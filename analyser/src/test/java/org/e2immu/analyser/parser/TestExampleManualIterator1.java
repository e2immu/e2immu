package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;
import org.e2immu.annotation.AnnotationMode;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

public class TestExampleManualIterator1 extends CommonTestRunner {

    public TestExampleManualIterator1() {
        super(true);
    }

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("hasNext".equals(d.methodInfo.name) && "MyIteratorImpl.this.list".equals(d.variableName)) {
            Assert.assertEquals(Level.FALSE, (int) d.properties.get(VariableProperty.MODIFIED));
        }
    };

    TypeAnalyserVisitor typeAnalyserVisitor = (iteration, typeInfo) -> {
        if ("MyConsumer".equals(typeInfo.simpleName)) {
            MethodInfo accept = typeInfo.findUniqueMethod("accept", 1);
            ParameterInfo param0 = accept.methodInspection.get().parameters.get(0);
            Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, param0.parameterAnalysis.get().getProperty(VariableProperty.NOT_NULL));
        }
        if ("MyIterator".equals(typeInfo.simpleName)) {
            MethodInfo hasNext = typeInfo.findUniqueMethod("hasNext", 0);
            Assert.assertSame(AnnotationMode.DEFENSIVE, typeInfo.typeInspection.get().annotationMode);
            // Assert.assertEquals(Level.TRUE, hasNext.methodAnalysis.get().getProperty(VariableProperty.MODIFIED));
        }
    };

    TypeContextVisitor typeContextVisitor = typeContext -> {
        TypeInfo list = typeContext.getFullyQualified(List.class);
        Assert.assertSame(AnnotationMode.DEFENSIVE, list.typeInspection.get().annotationMode);
        MethodInfo size = list.findUniqueMethod("size", 0);
        Assert.assertEquals(Level.FALSE, size.methodAnalysis.get().getProperty(VariableProperty.MODIFIED));
    };

    @Test
    public void test() throws IOException {
        testClass("ExampleManualIterator1", 0, 0, new DebugConfiguration.Builder()
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addTypeContextVisitor(typeContextVisitor)
                .build());
    }

}
