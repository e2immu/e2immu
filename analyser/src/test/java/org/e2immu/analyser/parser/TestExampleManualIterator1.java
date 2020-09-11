package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.TransferValue;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;
import org.e2immu.annotation.AnnotationMode;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class TestExampleManualIterator1 extends CommonTestRunner {

    public TestExampleManualIterator1() {
        super(true);
    }

    MethodAnalyserVisitor methodAnalyserVisitor = (iteration, methodInfo) -> {
        if ("iterator".equals(methodInfo.name)) {
            Assert.assertEquals(MultiLevel.EFFECTIVE, methodInfo.methodAnalysis.get().getProperty(VariableProperty.INDEPENDENT));
            Assert.assertTrue(methodInfo.methodAnalysis.get().returnStatementSummaries.isSet("0"));
        }
    };

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("hasNext".equals(d.methodInfo.name) && "MyIteratorImpl.this.list".equals(d.variableName)) {
            Assert.assertEquals(Level.FALSE, (int) d.properties.get(VariableProperty.MODIFIED));
        }
        if ("iterator".equals(d.methodInfo.name) && "ExampleManualIterator1.this.list".equals(d.variableName)) {
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
        if ("ExampleManualIterator1".equals(typeInfo.simpleName)) {
            Assert.assertEquals("E", typeInfo.typeAnalysis.get().implicitlyImmutableDataTypes.get()
                    .stream().map(ParameterizedType::detailedString).sorted().collect(Collectors.joining(";")));
        }
    };

    TypeContextVisitor typeContextVisitor = typeContext -> {
        TypeInfo list = typeContext.getFullyQualified(List.class);
        Assert.assertSame(AnnotationMode.DEFENSIVE, list.typeInspection.get().annotationMode);
        MethodInfo size = list.findUniqueMethod("size", 0);
        Assert.assertEquals(Level.FALSE, size.methodAnalysis.get().getProperty(VariableProperty.MODIFIED));
    };

    FieldAnalyserVisitor fieldAnalyserVisitor = (iteration, fieldInfo) -> {
        if ("list".equals(fieldInfo.name) && "ExampleManualIterator1".equals(fieldInfo.owner.simpleName)) {
            if (iteration > 0) {
                MethodInfo constructor = fieldInfo.owner.findConstructor(1);
                TransferValue constructorTv = constructor.methodAnalysis.get().fieldSummaries.get(fieldInfo);
                Assert.assertEquals(Level.TRUE, constructorTv.properties.get(VariableProperty.READ));
                Assert.assertEquals(Level.TRUE, constructorTv.properties.get(VariableProperty.MODIFIED));

                MethodInfo visit = fieldInfo.owner.findUniqueMethod("visit", 1);
                TransferValue visitTv = visit.methodAnalysis.get().fieldSummaries.get(fieldInfo);
                Assert.assertEquals(Level.TRUE, visitTv.properties.get(VariableProperty.READ));
                Assert.assertEquals(Level.FALSE, visitTv.properties.get(VariableProperty.MODIFIED));

                MethodInfo iterator = fieldInfo.owner.findUniqueMethod("iterator", 0);
                TransferValue iteratorTv = iterator.methodAnalysis.get().fieldSummaries.get(fieldInfo);
                Assert.assertEquals(Level.TRUE, iteratorTv.properties.get(VariableProperty.READ));
                Assert.assertEquals(Level.FALSE, iteratorTv.properties.get(VariableProperty.MODIFIED));

                int modified = fieldInfo.fieldAnalysis.get().getProperty(VariableProperty.MODIFIED);
                Assert.assertEquals(Level.FALSE, modified);
            }
        }
    };

    @Test
    public void test() throws IOException {
        testClass("ExampleManualIterator1", 0, 0, new DebugConfiguration.Builder()
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addTypeContextVisitor(typeContextVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }

}
