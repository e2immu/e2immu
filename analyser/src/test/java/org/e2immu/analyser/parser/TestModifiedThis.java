package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.MethodCall;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.statement.ExpressionAsStatement;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class TestModifiedThis extends CommonTestRunner {
    public TestModifiedThis() {
        super(true);
    }

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("clear".equals(d.methodInfo().name) && "ParentClass".equals(d.methodInfo().typeInfo.simpleName) && "ParentClass.this.set".equals(d.variableName())) {
            Assert.assertEquals(Level.TRUE, d.properties().get(VariableProperty.MODIFIED));
        }
        if ("clearAndLog".equals(d.methodInfo().name) && "ParentClass".equals(d.methodInfo().typeInfo.simpleName) && "0".equals(d.statementId())) {
            Assert.assertEquals("ParentClass.this", d.variableName());
            Assert.assertEquals(Level.TRUE, d.properties().get(VariableProperty.MODIFIED));
        }
        if ("clearAndLog".equals(d.methodInfo().name) && "ChildClass".equals(d.methodInfo().typeInfo.simpleName) && "0".equals(d.statementId())) {
            Assert.assertEquals("ChildClass.super", d.variableName());
            if (d.iteration() > 0) Assert.assertEquals(Level.TRUE, d.properties().get(VariableProperty.MODIFIED));
        }
        if ("clear".equals(d.methodInfo().name) && "InnerOfChild".equals(d.methodInfo().typeInfo.simpleName)) {
            Assert.assertEquals("ChildClass.super", d.variableName());
            if (d.iteration() > 0) Assert.assertEquals(Level.TRUE, d.properties().get(VariableProperty.MODIFIED));
        }
    };

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if ("clear".equals(d.methodInfo().name) && "InnerOfChild".equals(d.methodInfo().typeInfo.simpleName)) {
            Expression scope = ((MethodCall) ((ExpressionAsStatement) d.statementAnalysis().statement).expression).computedScope;
            VariableExpression variableExpression = (VariableExpression) scope;
            This t = (This) variableExpression.variable;
            Assert.assertTrue(t.explicitlyWriteType);
            Assert.assertTrue(t.writeSuper);
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        String name = d.methodInfo().name;
        if ("clear".equals(name) && "ParentClass".equals(d.methodInfo().typeInfo.simpleName) && d.iteration() > 0) {
            Assert.assertEquals(Level.TRUE, d.methodAnalysis().getProperty(VariableProperty.MODIFIED));
        }
        if ("clearAndAdd".equals(name) && "ChildClass".equals(d.methodInfo().typeInfo.simpleName) && d.iteration() > 0) {
            Assert.assertEquals(Level.TRUE, d.methodAnalysis().getProperty(VariableProperty.MODIFIED));
        }
        if ("clear".equals(name) && "InnerOfChild".equals(d.methodInfo().typeInfo.simpleName)) {
            if (d.iteration() > 0) {
                Assert.assertEquals(Level.TRUE, d.methodAnalysis().methodLevelData().
                        thisSummary.get().properties.get(VariableProperty.MODIFIED));
           //     Assert.assertEquals(Level.TRUE, methodInfo.methodAnalysis.get().getProperty(VariableProperty.MODIFIED));
            }
        }
    };


    TypeAnalyserVisitor typeAnalyserVisitor = d -> {
        TypeInfo typeInfo = d.typeInfo();
        if ("ParentClass".equals(typeInfo.simpleName)) {
            Assert.assertEquals("ModifiedThis", typeInfo.typeInspection.get().packageNameOrEnclosingType.getRight().simpleName);
        }
        if ("ChildClass".equals(typeInfo.simpleName)) {
            Assert.assertEquals("ModifiedThis", typeInfo.typeInspection.get().packageNameOrEnclosingType.getRight().simpleName);
        }
        if ("InnerOfChild".equals(typeInfo.simpleName)) {
            Assert.assertEquals("ChildClass", typeInfo.typeInspection.get().packageNameOrEnclosingType.getRight().simpleName);
        }
        if ("ModifiedThis".equals(typeInfo.simpleName)) {
            Assert.assertEquals("org.e2immu.analyser.testexample", typeInfo.typeInspection.get().packageNameOrEnclosingType.getLeft());
        }
    };

    @Test
    public void test() throws IOException {
        testClass("ModifiedThis", 0, 0, new DebugConfiguration.Builder()
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

}
