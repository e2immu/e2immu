package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.config.StatementAnalyserVisitor;
import org.e2immu.analyser.config.TypeContextVisitor;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.abstractvalue.ConstrainedNumericValue;
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

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("requireNotEmpty".equals(d.methodInfo().name) && "ts".equals(d.variableName())) {
            if ("1".equals(d.statementId())) {
                // we check that the restriction has been passed on to the parameter
                ParameterInfo parameterInfo = (ParameterInfo) d.variable();
                Assert.assertEquals(Level.SIZE_NOT_EMPTY, parameterInfo.parameterAnalysis.get().getProperty(VariableProperty.SIZE));
            }
        }
        if ("method2".equals(d.methodInfo().name) && "size2".equals(d.variableName())) {
            if ("0".equals(d.statementId())) {
                Assert.assertTrue(d.currentValue() instanceof ConstrainedNumericValue);
            }
        }
        if ("method3".equals(d.methodInfo().name) && "size3".equals(d.variableName()) && "0".equals(d.statementId())) {
            Assert.assertEquals("input3.size(),?>=0", d.currentValue().toString());
        }
    };

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if ("requireNotEmpty".equals(d.methodInfo().name) && "0.0.0".equals(d.statementId())) {
            Assert.assertEquals("0 == ts.size(),?>=0", d.condition().toString());
        }
        if ("method1".equals(d.methodInfo().name) && "0".equals(d.statementId())) {
            // the first clause, ((-1) + input1.size(),?>=0) >= 0, has gone because the 2nd is stronger
            Assert.assertEquals("((-1) + input1.size(),?>=0) >= 0", d.state().toString());
        }
        if ("method1".equals(d.methodInfo().name) && "1".equals(d.statementId())) {
            // the first clause, ((-1) + input1.size(),?>=0) >= 0, has gone because the 2nd is stronger
            Assert.assertEquals("((-3) + input1.size(),?>=0) >= 0", d.state().toString());
        }
        if ("method1".equals(d.methodInfo().name) && "2".equals(d.statementId())) {
            Assert.assertNotNull(d.haveError(Message.CONDITION_EVALUATES_TO_CONSTANT));
        }

        if ("method2".equals(d.methodInfo().name) && "1".equals(d.statementId())) {
            Assert.assertEquals("Statement " + d.statementId(), "((-1) + input2.size(),?>=0) >= 0", d.state().toString());
        }
        if ("method2".equals(d.methodInfo().name) && "2".equals(d.statementId())) {
            Assert.assertEquals("Statement " + d.statementId(), "((-3) + input2.size(),?>=0) >= 0", d.state().toString());
        }
        if ("method2".equals(d.methodInfo().name) && "3".equals(d.statementId())) {
            Assert.assertNotNull(d.haveError(Message.CONDITION_EVALUATES_TO_CONSTANT));
        }

        if ("method3".equals(d.methodInfo().name) && Set.of("1", "2.0.0").contains(d.statementId())) {
            Assert.assertEquals("Statement " + d.statementId(), "((-1) + input3.size(),?>=0) >= 0", d.state().toString());
        }
        if ("method3".equals(d.methodInfo().name) && "2".equals(d.statementId())) {
            Assert.assertNotNull(d.haveError(Message.CONDITION_EVALUATES_TO_CONSTANT));
        }

        if ("method4".equals(d.methodInfo().name) && Set.of("0.0.0", "0.0.1").contains(d.statementId())) {
            Assert.assertEquals("((-1) + input4.size(),?>=0) >= 0", d.condition().toString());
        }
        if ("method4".equals(d.methodInfo().name) && "0.0.1".equals(d.statementId())) {
            Assert.assertNotNull(d.haveError(Message.CONDITION_EVALUATES_TO_CONSTANT));
        }
    };

    TypeContextVisitor typeContextVisitor = typeContext -> {
        TypeInfo collection = typeContext.getFullyQualified(Collection.class);
        MethodInfo isEmpty = collection.typeInspection.getPotentiallyRun().methods.stream().filter(m -> m.name.equals("isEmpty")).findAny().orElseThrow();
        int size = isEmpty.methodAnalysis.get().getProperty(VariableProperty.SIZE);
        Assert.assertEquals(Level.SIZE_EMPTY, size);

        TypeInfo map = typeContext.getFullyQualified(Map.class);
        MethodInfo entrySet = map.typeInspection.getPotentiallyRun().methods.stream().filter(m -> m.name.equals("entrySet")).findAny().orElseThrow();
        int sizeCopy = entrySet.methodAnalysis.get().getProperty(VariableProperty.SIZE_COPY);
        Assert.assertEquals(Level.SIZE_COPY_TRUE, sizeCopy);
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
