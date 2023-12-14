package org.e2immu.analyser.parser.modification;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.modification.testexample.Modification_13;
import org.e2immu.analyser.parser.modification.testexample.Modification_13B;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.TypeAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Test_16_Modification_13 extends CommonTestRunner {

    public Test_16_Modification_13() {
        super(true);
    }

    @Test
    public void test() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            String INNER_THIS = Modification_13.class.getCanonicalName() + ".Inner.this";
            if ("clearIfExceeds".equals(d.methodInfo().name) && INNER_THIS.equals(d.variableName())) {
                if ("0".equals(d.statementId())) {
                    assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                }
                if ("0.0.0".equals(d.statementId())) {
                    assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                }
            }
            if ("Modification_13".equals(d.methodInfo().name)) {
                assertEquals("0", d.statementId());
                if (d.variable() instanceof ParameterInfo pi && "input".equals(pi.name)) {
                    assertEquals("", d.variableInfo().getLinkedVariables().toString());
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("clearIfExceeds".equals(d.methodInfo().name)) {
                assertDv(d, 1, DV.TRUE_DV, Property.MODIFIED_METHOD);
            }
            if ("Modification_13".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 1, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
                assertDv(d.p(0), 1, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
            }
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Modification_13".equals(d.typeInfo().simpleName)) {
                assertTrue(d.typeAnalysis().getHiddenContentTypes().isEmpty());
            }
        };
        testClass("Modification_13", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }


    @Test
    public void testB() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            String INNER_THIS = Modification_13B.class.getCanonicalName() + ".Inner.this";
            if ("clearIfExceeds".equals(d.methodInfo().name) && INNER_THIS.equals(d.variableName())) {
                if ("0".equals(d.statementId())) {
                    assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                }
                if ("0.0.0".equals(d.statementId())) {
                    assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                }
            }
            if ("Modification_13B".equals(d.methodInfo().name)) {
                assertEquals("0", d.statementId());
                if (d.variable() instanceof ParameterInfo pi && "input".equals(pi.name)) {
                    assertEquals("this.set:4,this:4", d.variableInfo().getLinkedVariables().toString());
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("clearIfExceeds".equals(d.methodInfo().name)) {
                assertDv(d, 1, DV.TRUE_DV, Property.MODIFIED_METHOD);
            }
            if ("Modification_13B".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 2, MultiLevel.INDEPENDENT_HC_DV, Property.INDEPENDENT);
                assertDv(d.p(0), 2, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
            }
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Modification_13B".equals(d.typeInfo().simpleName)) {
                assertEquals("T", d.typeAnalysis().getHiddenContentTypes().toString());
            }
        };
        testClass("Modification_13B", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }

}
