package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.MethodLevelData;
import org.e2immu.analyser.analyser.TransferValue;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;
import org.e2immu.annotation.AnnotationMode;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class TestExampleManualIterator1 extends CommonTestRunner {

    public TestExampleManualIterator1() {
        super(true);
    }

    MethodAnalyserVisitor methodAnalyserVisitor = (iteration, methodInfo) -> {
        MethodLevelData methodLevelData = methodInfo.methodAnalysis.get().methodLevelData();
        if ("iterator".equals(methodInfo.name)) {
            //  Assert.assertEquals(MultiLevel.EFFECTIVE, methodInfo.methodAnalysis.get().getProperty(VariableProperty.INDEPENDENT));
            Assert.assertTrue(methodLevelData.returnStatementSummaries.isSet("0"));
        }

        if (Set.of("hasNext", "next").contains(methodInfo.name) && "MyIteratorImpl".equals(methodInfo.typeInfo.simpleName)) {
            if (iteration > 0) {
                Assert.assertTrue(methodLevelData.variablesLinkedToFieldsAndParameters.isSet());
            }
        }
    };

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("hasNext".equals(d.methodInfo.name) && "MyIteratorImpl.this.list".equals(d.variableName)) {
            Assert.assertEquals(Level.FALSE, (int) d.properties.get(VariableProperty.MODIFIED));
        }
        if ("iterator".equals(d.methodInfo.name) && "ExampleManualIterator1.this.list".equals(d.variableName) && d.iteration > 1) {
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
            //MethodInfo hasNext = typeInfo.findUniqueMethod("hasNext", 0);
            Assert.assertSame(AnnotationMode.DEFENSIVE, typeInfo.typeInspection.getPotentiallyRun().annotationMode);
            // Assert.assertEquals(Level.TRUE, hasNext.methodAnalysis.get().getProperty(VariableProperty.MODIFIED));
        }
        if ("ExampleManualIterator1".equals(typeInfo.simpleName)) {
            Assert.assertEquals("E", typeInfo.typeAnalysis.get().implicitlyImmutableDataTypes.get()
                    .stream().map(ParameterizedType::detailedString).sorted().collect(Collectors.joining(";")));
        }
        if ("MyIteratorImpl".equals(typeInfo.simpleName)) {
            int container = typeInfo.typeAnalysis.get().getProperty(VariableProperty.CONTAINER);
            int expectContainer = iteration < 2 ? Level.DELAY : Level.TRUE;
            Assert.assertEquals(expectContainer, container);

            int independent = typeInfo.typeAnalysis.get().getProperty(VariableProperty.INDEPENDENT);
            int expectIndependent = iteration < 2 ? Level.DELAY : Level.TRUE;
            Assert.assertEquals(expectIndependent, independent);
        }
    };

    TypeContextVisitor typeContextVisitor = typeContext -> {
        TypeInfo list = typeContext.getFullyQualified(List.class);
        Assert.assertSame(AnnotationMode.DEFENSIVE, list.typeInspection.getPotentiallyRun().annotationMode);
        MethodInfo size = list.findUniqueMethod("size", 0);
        Assert.assertEquals(Level.FALSE, size.methodAnalysis.get().getProperty(VariableProperty.MODIFIED));
    };

    FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
        FieldInfo fieldInfo = d.fieldInfo();
        int iteration = d.iteration();
        if ("list".equals(fieldInfo.name) && "MyIteratorImpl".equals(fieldInfo.owner.simpleName)) {
            int modified = d.fieldAnalysis().getProperty(VariableProperty.MODIFIED);
            int expect = iteration <= 1 ? Level.DELAY : Level.FALSE;
            Assert.assertEquals(expect, modified);

            if (iteration > 0) {
                Assert.assertTrue(d.fieldAnalysis().variablesLinkedToMe.isSet());
                Assert.assertEquals("", d.fieldAnalysis().variablesLinkedToMe.get().toString());
            }
        }
        if ("list".equals(fieldInfo.name) && "ExampleManualIterator1".equals(fieldInfo.owner.simpleName)) {
            if (iteration > 0) {
                MethodInfo constructor = fieldInfo.owner.findConstructor(1);
                MethodLevelData methodLevelDataConstructor = constructor.methodAnalysis.get().methodLevelData();
                TransferValue constructorTv = methodLevelDataConstructor.fieldSummaries.get(fieldInfo);
                Assert.assertEquals(Level.TRUE, constructorTv.properties.get(VariableProperty.READ));
                Assert.assertEquals(Level.TRUE, constructorTv.properties.get(VariableProperty.MODIFIED));

                MethodInfo visit = fieldInfo.owner.findUniqueMethod("visit", 1);
                TransferValue visitTv = visit.methodAnalysis.get().methodLevelData().fieldSummaries.get(fieldInfo);
                Assert.assertEquals(Level.TRUE, visitTv.properties.get(VariableProperty.READ));
                Assert.assertEquals(Level.FALSE, visitTv.properties.get(VariableProperty.MODIFIED));

                MethodInfo iterator = fieldInfo.owner.findUniqueMethod("iterator", 0);
                TransferValue iteratorTv = iterator.methodAnalysis.get().methodLevelData().fieldSummaries.get(fieldInfo);
                Assert.assertEquals(Level.TRUE, iteratorTv.properties.get(VariableProperty.READ));

                if (iteration > 1) {
                    Assert.assertEquals(Level.FALSE, iteratorTv.properties.get(VariableProperty.MODIFIED));

                    // int modified = fieldInfo.fieldAnalysis.get().getProperty(VariableProperty.MODIFIED);
                    //    Assert.assertEquals(Level.FALSE, modified);
                }
            }
        }
    };

    // TODO we allow for one error at the moment, a transfer of @Size from Collections.addAll which has not yet been implemented
    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if ("visit".equals(d.methodInfo.name) && "0".equals(d.statementId) && d.iteration > 1) {
            Assert.assertTrue(d.statementAnalysis.errorFlags.errorValue.isSet());
        }
    };

    @Test
    public void test() throws IOException {
        testClass("ExampleManualIterator1", 1, 0, new DebugConfiguration.Builder()
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addTypeContextVisitor(typeContextVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

}
