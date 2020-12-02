package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.MethodLevelData;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.value.UnknownValue;
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

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        MethodLevelData methodLevelData = d.methodAnalysis().methodLevelData();
        if ("iterator".equals(d.methodInfo().name)) {
            //  Assert.assertEquals(MultiLevel.EFFECTIVE, methodInfo.methodAnalysis.get().getProperty(VariableProperty.INDEPENDENT));
            VariableInfo variableInfo = d.getReturnAsVariable();
            Assert.assertNotSame(EmptyExpression.NO_VALUE, variableInfo.getValue());
        }

        if (Set.of("hasNext", "next").contains(d.methodInfo().name) && "MyIteratorImpl".equals(d.methodInfo().typeInfo.simpleName)) {
            if (d.iteration() > 0) {
                Assert.assertTrue(methodLevelData.linksHaveBeenEstablished.isSet());
            }
        }
    };

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("hasNext".equals(d.methodInfo().name) && "MyIteratorImpl.this.list".equals(d.variableName())) {
            Assert.assertEquals(Level.FALSE, d.getProperty(VariableProperty.MODIFIED));
        }
        if ("iterator".equals(d.methodInfo().name) && "ExampleManualIterator1.this.list".equals(d.variableName()) && d.iteration() > 1) {
            Assert.assertEquals(Level.FALSE, d.getProperty(VariableProperty.MODIFIED));
        }
    };

    TypeAnalyserVisitor typeAnalyserVisitor = d -> {
        TypeInfo typeInfo = d.typeInfo();
        if ("MyConsumer".equals(typeInfo.simpleName)) {
            MethodInfo accept = typeInfo.findUniqueMethod("accept", 1);
            ParameterInfo param0 = accept.methodInspection.get().getParameters().get(0);
            Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, param0.parameterAnalysis.get().getProperty(VariableProperty.NOT_NULL));
        }
        if ("MyIterator".equals(typeInfo.simpleName)) {
            //MethodInfo hasNext = typeInfo.findUniqueMethod("hasNext", 0);
            Assert.assertSame(AnnotationMode.DEFENSIVE, typeInfo.typeInspection.get().annotationMode());
            // Assert.assertEquals(Level.TRUE, hasNext.methodAnalysis.get().getProperty(VariableProperty.MODIFIED));
        }
        if ("ExampleManualIterator1".equals(typeInfo.simpleName)) {
            Assert.assertEquals("E", d.typeAnalysis().getImplicitlyImmutableDataTypes()
                    .stream().map(ParameterizedType::detailedString).sorted().collect(Collectors.joining(";")));
        }
        if ("MyIteratorImpl".equals(typeInfo.simpleName)) {
            int container = d.typeAnalysis().getProperty(VariableProperty.CONTAINER);
            int expectContainer = d.iteration() < 2 ? Level.DELAY : Level.TRUE;
            Assert.assertEquals(expectContainer, container);

            int independent = d.typeAnalysis().getProperty(VariableProperty.INDEPENDENT);
            int expectIndependent = d.iteration() < 2 ? Level.DELAY : Level.TRUE;
            Assert.assertEquals(expectIndependent, independent);
        }
    };

    TypeMapVisitor typeMapVisitor = typeMap -> {
        TypeInfo list = typeMap.get(List.class);
        Assert.assertSame(AnnotationMode.DEFENSIVE, list.typeInspection.get().annotationMode());
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
                Assert.assertEquals("", d.fieldAnalysis().getVariablesLinkedToMe().toString());
            }
        }
        if ("list".equals(fieldInfo.name) && "ExampleManualIterator1".equals(fieldInfo.owner.simpleName)) {
            if (iteration > 0) {
                MethodInfo constructor = fieldInfo.owner.findConstructor(1);
                MethodAnalysis constructorMa = d.evaluationContext().getAnalyserContext().getMethodAnalysis(constructor);
                VariableInfo constructorTv = constructorMa.getLastStatement().getLatestVariableInfo(fieldInfo.fullyQualifiedName());

                Assert.assertEquals(Level.TRUE, constructorTv.getProperty(VariableProperty.READ));
                Assert.assertEquals(Level.TRUE, constructorTv.getProperty(VariableProperty.MODIFIED));

                MethodInfo visit = fieldInfo.owner.findUniqueMethod("visit", 1);
                MethodAnalysis visitMa = d.evaluationContext().getAnalyserContext().getMethodAnalysis(visit);
                VariableInfo visitTv = visitMa.getLastStatement().getLatestVariableInfo(fieldInfo.fullyQualifiedName());

                Assert.assertEquals(Level.TRUE, visitTv.getProperty(VariableProperty.READ));
                Assert.assertEquals(Level.FALSE, visitTv.getProperty(VariableProperty.MODIFIED));

                MethodInfo iterator = fieldInfo.owner.findUniqueMethod("iterator", 0);
                MethodAnalysis iteratorMa = d.evaluationContext().getAnalyserContext().getMethodAnalysis(iterator);
                VariableInfo iteratorTv = iteratorMa.getLastStatement().getLatestVariableInfo(fieldInfo.fullyQualifiedName());

                Assert.assertEquals(Level.TRUE, iteratorTv.getProperty(VariableProperty.READ));

                if (iteration > 1) {
                    Assert.assertEquals(Level.FALSE, iteratorTv.getProperty(VariableProperty.MODIFIED));

                    // int modified = fieldInfo.fieldAnalysis.get().getProperty(VariableProperty.MODIFIED);
                    //    Assert.assertEquals(Level.FALSE, modified);
                }
            }
        }
    };

    // TODO we allow for one error at the moment, a transfer of @Size from Collections.addAll which has not yet been implemented
    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if ("visit".equals(d.methodInfo().name) && "0".equals(d.statementId()) && d.iteration() > 1) {
            Assert.assertNotNull(d.haveError(Message.CONDITION_EVALUATES_TO_CONSTANT)); // TODO change message
        }
    };

    @Test
    public void test() throws IOException {
        testClass("ExampleManualIterator1", 1, 0, new DebugConfiguration.Builder()
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addTypeContextVisitor(typeMapVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

}
