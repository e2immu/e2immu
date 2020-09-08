package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.TransferValue;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.NegatedValue;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class TestExampleManualEventuallyE1Container extends CommonTestRunner {

    MethodAnalyserVisitor methodAnalyserVisitor = (iteration, methodInfo) -> {
        if ("addIfGreater".equals(methodInfo.name)) {
            if (iteration > 0) {
                List<Value> preconditions = methodInfo.methodAnalysis.get().preconditionForMarkAndOnly.get();
                Assert.assertEquals(1, preconditions.size());
                Assert.assertEquals("this.j > 0", preconditions.get(0).toString());
                Assert.assertEquals("(-this.j) >= 0", NegatedValue.negate(preconditions.get(0)).toString());
            }
        }
        if ("setNegativeJ".equals(methodInfo.name)) {
            if (iteration > 0) {
                Assert.assertEquals("((-this.j) >= 0 and (-j) >= 0)", methodInfo.methodAnalysis.get().precondition.get().toString());
                Assert.assertEquals("[(-this.j) >= 0]", methodInfo.methodAnalysis.get().preconditionForMarkAndOnly.get().toString());

                FieldInfo fieldJ = methodInfo.typeInfo.typeInspection.get().fields.stream().filter(f -> "j".equals(f.name)).findAny().orElseThrow();
                TransferValue tv = methodInfo.methodAnalysis.get().fieldSummaries.get(fieldJ);
                Assert.assertNotNull(tv);
                Value value = tv.value.get();
                Assert.assertEquals("j", value.toString());
                Value state = tv.stateOnAssignment.get();
                Assert.assertEquals("(-this.j) >= 0", state.toString());
            }
        }
        if ("getIntegers".equals(methodInfo.name)) {
            if (iteration > 0) {
                TransferValue tv = methodInfo.methodAnalysis.get().returnStatementSummaries.get("0");
                Assert.assertEquals(1, tv.linkedVariables.get().size());
            }
            if (iteration > 1) {
                Set<Variable> variables = methodInfo.methodAnalysis.get().variablesLinkedToMethodResult.get();
                Assert.assertEquals(1, variables.size());
                int independent = methodInfo.methodAnalysis.get().getProperty(VariableProperty.INDEPENDENT);
                Assert.assertEquals(Level.FALSE, independent);
            }
        }
    };

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("setNegativeJ".equals(d.methodInfo.name) && "2".equals(d.statementId) && "ExampleManualEventuallyE1Container.this.j".equals(d.variableName)) {
            Assert.assertEquals("j", d.currentValue.toString());
            if (d.iteration > 0) {
                Assert.assertEquals("(-this.j) >= 0", d.stateOnAssignment.toString());
            }
        }
    };

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if ("setNegativeJ".equals(d.methodInfo.name)) {
            if ("0".equals(d.statementId)) {
                if (d.iteration <= 1) {
                    Assert.assertEquals("(-j) >= 0", d.state.toString());
                } else {
                    Assert.assertEquals("((-this.j) >= 0 and (-j) >= 0)", d.state.toString());
                }
            }
            if ("1".equals(d.statementId) && d.iteration > 0) {
                Assert.assertEquals("((-this.j) >= 0 and (-j) >= 0)", d.state.toString());
            }
        }
    };

    TypeAnalyserVisitor typeAnalyserVisitor = (iteration, typeInfo) -> {
        if (iteration > 0) {
            Assert.assertEquals(1, typeInfo.typeAnalysis.get().approvedPreconditions.size());
            Assert.assertEquals("j=(-this.j) >= 0", typeInfo.typeAnalysis.get().approvedPreconditions.stream()
                    .map(Object::toString)
                    .collect(Collectors.joining(";")));
        }
        Set<ParameterizedType> supportData = typeInfo.typeAnalysis.get().implicitlyImmutableDataTypes.get();
        Assert.assertEquals(1, supportData.size());
        ParameterizedType supportDataType = supportData.stream().findAny().orElseThrow();
        Assert.assertEquals("java.util.Set<java.lang.Integer>", supportDataType.detailedString());
    };

    @Test
    public void test() throws IOException {
        testClass("ExampleManualEventuallyE1Container", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .build());
    }

}
