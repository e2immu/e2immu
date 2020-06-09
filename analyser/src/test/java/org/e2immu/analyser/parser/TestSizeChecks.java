package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.NumberedStatement;
import org.e2immu.analyser.analyser.TransferValue;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.ConstrainedNumericValue;
import org.e2immu.analyser.model.abstractvalue.PrimitiveValue;
import org.e2immu.analyser.model.abstractvalue.VariableValue;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class TestSizeChecks extends CommonTestRunner {
    public TestSizeChecks() {
        super(true);
    }

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = new StatementAnalyserVariableVisitor() {
        @Override
        public void visit(int iteration, MethodInfo methodInfo, String statementId, String variableName, Variable variable, Value currentValue, Map<VariableProperty, Integer> properties) {
            if ("requireNotEmpty".equals(methodInfo.name) && "ts".equals(variableName)) {
                if ("1".equals(statementId)) {
                    // we check that the restriction has been passed on to the parameter
                    ParameterInfo parameterInfo = (ParameterInfo) variable;
                    Assert.assertEquals(Analysis.SIZE_NOT_EMPTY, parameterInfo.parameterAnalysis.get().getProperty(VariableProperty.SIZE));
                }
            }
            if ("method2".equals(methodInfo.name) && "size2".equals(variableName)) {
                if ("0".equals(statementId)) {
                    Assert.assertTrue(currentValue instanceof ConstrainedNumericValue);
                }
            }
            if ("method3".equals(methodInfo.name) && "size3".equals(variableName) && "0".equals(statementId)) {
                Assert.assertEquals("input3.size(),?>=0", currentValue.toString());
            }
        }
    };

    StatementAnalyserVisitor statementAnalyserVisitor = new StatementAnalyserVisitor() {
        @Override
        public void visit(int iteration, MethodInfo methodInfo, NumberedStatement numberedStatement, Value conditional) {
            if ("requireNotEmpty".equals(methodInfo.name) && "0.0.0".equals(numberedStatement.streamIndices())) {
                Assert.assertEquals("0 == ts.size(),?>=0", conditional.toString());
            }

            if ("method1".equals(methodInfo.name) && "2".equals(numberedStatement.streamIndices())) {
                Assert.assertTrue(numberedStatement.errorValue.isSet());
            }

            if ("method2".equals(methodInfo.name) && "1".equals(numberedStatement.streamIndices())) {
                Assert.assertEquals("Statement " + numberedStatement.streamIndices(), "((-1) + input2.size(),?>=0) >= 0", conditional.toString());
            }
            if ("method2".equals(methodInfo.name) && "2".equals(numberedStatement.streamIndices())) {
                Assert.assertEquals("Statement " + numberedStatement.streamIndices(), "((-3) + input2.size(),?>=0) >= 0", conditional.toString());
            }
            if ("method2".equals(methodInfo.name) && "3".equals(numberedStatement.streamIndices())) {
                Assert.assertTrue(numberedStatement.errorValue.isSet());
            }

            if ("method3".equals(methodInfo.name) && Set.of("1", "2.0.0").contains(numberedStatement.streamIndices())) {
                Assert.assertEquals("Statement " + numberedStatement.streamIndices(), "((-1) + input3.size(),?>=0) >= 0", conditional.toString());
            }
            if ("method3".equals(methodInfo.name) && "2".equals(numberedStatement.streamIndices())) {
                Assert.assertTrue(numberedStatement.errorValue.isSet());
            }

            if ("method4".equals(methodInfo.name) && Set.of("0.0.0", "0.0.1").contains(numberedStatement.streamIndices())) {
                Assert.assertEquals("((-1) + input4.size(),?>=0) >= 0", conditional.toString());
            }
            if ("method4".equals(methodInfo.name) && "0.0.1".equals(numberedStatement.streamIndices())) {
                Assert.assertTrue(numberedStatement.errorValue.isSet());
            }
        }
    };

    TypeContextVisitor typeContextVisitor = typeContext -> {
        TypeInfo collection = typeContext.getFullyQualified(Collection.class);
        MethodInfo isEmpty = collection.typeInspection.get().methods.stream().filter(m -> m.name.equals("isEmpty")).findAny().orElseThrow();
        int size = isEmpty.methodAnalysis.get().getProperty(VariableProperty.SIZE);
        Assert.assertEquals(Analysis.SIZE_EMPTY, size);

        TypeInfo map = typeContext.getFullyQualified(Map.class);
        MethodInfo entrySet = map.typeInspection.get().methods.stream().filter(m -> m.name.equals("entrySet")).findAny().orElseThrow();
        int sizeCopy = entrySet.methodAnalysis.get().getProperty(VariableProperty.SIZE_COPY);
        Assert.assertEquals(Level.TRUE_LEVEL_1, sizeCopy);
    };

    @Test
    public void test() throws IOException {
        testClass("SizeChecks", 4, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addTypeContextVisitor(typeContextVisitor)
                .build());
    }

}
