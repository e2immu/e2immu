package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.visitor.FieldAnalyserVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class Test_28_InnerClass extends CommonTestRunner {
    public Test_28_InnerClass() {
        super(false);
    }

    @Test
    public void test_0() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("InnerClass_0".equals(d.methodInfo().name) && "0.0.0".equals(d.statementId()) && "outerField".equals(d.variableName())) {
                assertTrue(d.variable() instanceof ParameterInfo);
                int notNull = d.properties().getOrDefault(VariableProperty.NOT_NULL_EXPRESSION, Level.DELAY);
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, notNull);
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("nonPrivateNonFinal".equals(d.fieldInfo().name)) {
                assertNotNull(d.haveError(Message.NON_PRIVATE_FIELD_NOT_FINAL));
            }
            if ("unusedInnerField".equals(d.fieldInfo().name)) {
                assertNotNull(d.haveError(Message.PRIVATE_FIELD_NOT_READ));
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("doAssignmentIntoNestedType".equals(d.methodInfo().name)) {
                assertNotNull(d.haveError(Message.METHOD_SHOULD_BE_MARKED_STATIC));
            }
        };


        testClass("InnerClass_0", 4, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }
}
