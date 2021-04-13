
/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.FieldAnalysisImpl;
import org.e2immu.analyser.analyser.LinkedVariables;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MethodAnalysis;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterAnalysis;
import org.e2immu.analyser.model.expression.InlinedMethod;
import org.e2immu.analyser.model.expression.Negation;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class Test_Support_02_SetOnce extends CommonTestRunner {

    public Test_Support_02_SetOnce() {
        super(true);
    }

    @Test
    public void test() throws IOException {
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("SetOnce".equals(d.typeInfo().simpleName)) {
                assertEquals("[Type param T]", d.typeAnalysis().getImplicitlyImmutableDataTypes().toString());
                String expectE1 = d.iteration() <= 1 ? "{}" : "{t=null==t}";
                assertEquals(expectE1, d.typeAnalysis().getApprovedPreconditionsE1().toString());
                String expectE2 = d.iteration() <= 1 ? "{}" : "{t=null==t}";
                assertEquals(expectE2, d.typeAnalysis().getApprovedPreconditionsE2().toString());
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("t".equals(d.fieldInfo().name)) {
                assertEquals("<variable value>", d.fieldAnalysis().getEffectivelyFinalValue().toString());
                assertEquals(Level.FALSE, d.fieldAnalysis().getProperty(VariableProperty.FINAL));
                String expectValues = d.iteration() == 0 ? "[null,<s:T>]" : "[null,t]";
                assertEquals(expectValues,
                        ((FieldAnalysisImpl.Builder) d.fieldAnalysis()).getValues().toString());
                int expectImmu = d.iteration() <= 2 ? Level.DELAY : MultiLevel.MUTABLE;
                assertEquals(expectImmu, d.fieldAnalysis().getProperty(VariableProperty.IMMUTABLE));
                assertEquals(MultiLevel.NULLABLE, d.fieldAnalysis().getProperty(VariableProperty.EXTERNAL_NOT_NULL));

                assertEquals("", d.fieldAnalysis().getLinkedVariables().toString());
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("get".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertEquals(0, d.statementAnalysis().flowData.getTimeAfterSubBlocks());

                    assertEquals("true", d.statementAnalysis().stateData
                            .conditionManagerForNextStatement.get().state().toString());
                    assertTrue(d.statementAnalysis().stateData
                            .conditionManagerForNextStatement.get().precondition().isEmpty());
                }
                if ("1".equals(d.statementId())) {
                    assertEquals(0, d.statementAnalysis().flowData.getTimeAfterSubBlocks());
                }
            }
            if ("getOrElse".equals(d.methodInfo().name) && "0.0.0".equals(d.statementId())) {
                assertEquals(d.iteration() > 1,
                        d.statementAnalysis().methodLevelData.linksHaveBeenEstablished.isSet());
                String expectState = d.iteration() == 0 ? "<precondition>" : "true";
                assertEquals(expectState, d.statementAnalysis().stateData.getPrecondition().expression().toString());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            final String TYPE = "org.e2immu.support.SetOnce";
            final String T0 = TYPE + ".t$0";
            int n = d.methodInfo().methodInspection.get().getParameters().size();

            if ("get".equals(d.methodInfo().name) && n == 0) {
                if ("0".equals(d.statementId())) {
                    if (T0.equals(d.variableName())) {
                        assertTrue(d.iteration() > 0);
                        assertEquals("nullable instance type T", d.currentValue().toString());
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                    }
                }
                if ("1".equals(d.statementId())) {
                    if (d.variable() instanceof ReturnVariable) {
                        String expectValue = d.iteration() == 0 ? "<f:t>" : T0;
                        assertEquals(expectValue, d.currentValue().toString());
                        int expectCnn = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                        assertEquals(expectCnn, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                        //  assertEquals(expectCnn, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                    }
                    if (T0.equals(d.variableName())) {
                        assertTrue(d.iteration() > 0);
                        assertTrue(d.variableInfoContainer().isPrevious());
                        assertEquals("nullable instance type T", d.currentValue().toString());
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                    }
                }
            }
            if ("getOrElse".equals(d.methodInfo().name)) {
                if ("0.0.0".equals(d.statementId())) {
                    if (d.variable() instanceof ReturnVariable) {
                        String expectValue = switch (d.iteration()) {
                            case 0 -> "<m:get>";
                            case 1 -> "<f:t>";
                            default -> "org.e2immu.support.SetOnce.t$0/*@Dependent1*/";
                        };
                        assertEquals(expectValue, d.currentValue().toString());
                        int expectNne = d.iteration() <= 1 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                        assertEquals(expectNne, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                        int expectEnn = d.iteration() <= 1 ? Level.DELAY : MultiLevel.NOT_INVOLVED;
                        assertEquals(expectEnn, d.getProperty(VariableProperty.EXTERNAL_NOT_NULL));
                    }
                    if (d.variable() instanceof FieldReference fr && "t".equals(fr.fieldInfo.name)) {
                        assertTrue(d.iteration() > 0);
                        assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.EXTERNAL_NOT_NULL));
                        assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                        assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                    }
                    if (T0.equals(d.variableName())) {
                        assertTrue(d.iteration() > 1);
                        assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.EXTERNAL_NOT_NULL));
                        assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                        assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                    }
                }
                if ("0".equals(d.statementId())) {
                    if (d.variable() instanceof ReturnVariable) {
                        String expectValue = switch (d.iteration()) {
                            case 0 -> "<m:isSet>?<m:get>:<return value>";
                            case 1 -> "null==<f:t>?<return value>:<f:t>";
                            default -> "null==org.e2immu.support.SetOnce.t$0?<return value>:org.e2immu.support.SetOnce.t$0/*@Dependent1*/";
                        };
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                }
                if ("1".equals(d.statementId())) {
                    if (d.variable() instanceof ReturnVariable) {
                        String expectValue = switch (d.iteration()) {
                            case 0 -> "<m:isSet>?<m:get>:alternative";
                            case 1 -> "null==<f:t>?alternative:<f:t>";
                            default -> "null==org.e2immu.support.SetOnce.t$0?alternative:org.e2immu.support.SetOnce.t$0";
                        };
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("set".equals(d.methodInfo().name)) {
                ParameterAnalysis paramT = d.parameterAnalyses().get(0);
                // because not @Final, we get NOT_INVOLVED
                int expectEnn = d.iteration() == 0 ? Level.DELAY : MultiLevel.NOT_INVOLVED;
                assertEquals(expectEnn, paramT.getProperty(VariableProperty.EXTERNAL_NOT_NULL));

                if (d.iteration() == 0) {
                    assertNull(d.methodAnalysis().getPreconditionForEventual());
                } else {
                    assertEquals("null==t", d.methodAnalysis().getPreconditionForEventual().expression().toString());
                    assertEquals("null==t", d.methodAnalysis().getPrecondition().expression().toString());
                }
                assertEquals(Level.TRUE, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
                assertEquals(d.iteration() > 0, d.methodAnalysis().methodLevelData().linksHaveBeenEstablished.isSet());

                MethodAnalysis.Eventual eventual = d.methodAnalysis().getEventual();
                if (d.iteration() > 2) {
                    assertTrue(eventual.mark());
                } else {
                    assertSame(MethodAnalysis.DELAYED_EVENTUAL, eventual);
                }
            }

            if ("get".equals(d.methodInfo().name)) {
                if (d.iteration() == 0) {
                    assertNull(d.methodAnalysis().getPreconditionForEventual());
                    assertNull(d.methodAnalysis().getSingleReturnValue());
                } else {
                    assertEquals("null!=t", d.methodAnalysis().getPreconditionForEventual()
                            .expression().toString());
                    assertEquals("t", d.methodAnalysis().getSingleReturnValue().toString());
                }
                int expectModified = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                assertEquals(expectModified, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
                assertEquals(d.iteration() > 0, d.methodAnalysis().methodLevelData().linksHaveBeenEstablished.isSet());

                MethodAnalysis.Eventual eventual = d.methodAnalysis().getEventual();
                if (d.iteration() > 2) {
                    assertTrue(eventual.after());
                } else {
                    assertSame(MethodAnalysis.DELAYED_EVENTUAL, eventual);
                }
            }

            if ("copy".equals(d.methodInfo().name)) {
                if (d.iteration() <= 1) {
                    assertNull(d.methodAnalysis().getPreconditionForEventual());
                } else {
                    assertEquals("null==t", d.methodAnalysis().getPreconditionForEventual().expression().toString());
                }
                assertEquals(d.iteration() > 1, d.methodAnalysis().methodLevelData().linksHaveBeenEstablished.isSet());
                int expectModified = d.iteration() <= 1 ? Level.DELAY : Level.TRUE;
                assertEquals(expectModified, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));

                MethodAnalysis.Eventual eventual = d.methodAnalysis().getEventual();
                if (d.iteration() > 2) {
                    assertTrue(eventual.mark());
                } else {
                    assertSame(MethodAnalysis.DELAYED_EVENTUAL, eventual);
                }
            }

            if ("toString".equals(d.methodInfo().name)) {
                assertNull(d.methodAnalysis().getPreconditionForEventual());

                int expectModified = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                assertEquals(expectModified, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
            }

            if ("isSet".equals(d.methodInfo().name)) {
                assertNull(d.methodAnalysis().getPreconditionForEventual());
                if (d.iteration() > 0) {
                    assertEquals("null!=t", d.methodAnalysis().getSingleReturnValue().toString());
                    assertTrue(d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod im
                            && im.expression() instanceof Negation);
                }
                int expectModified = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                assertEquals(expectModified, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
            }

            if ("getOrElse".equals(d.methodInfo().name)) {
                int expectModified = d.iteration() <= 1 ? Level.DELAY : Level.FALSE;
                assertEquals(expectModified, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
                if (d.iteration() <= 1) {
                    assertNull(d.methodAnalysis().getSingleReturnValue());
                }
                ParameterAnalysis alternative = d.parameterAnalyses().get(0);
                // because not @Final, we get NOT_INVOLVED
                int expectEnn = d.iteration() == 0 ? Level.DELAY : MultiLevel.NOT_INVOLVED;
                assertEquals(expectEnn, alternative.getProperty(VariableProperty.EXTERNAL_NOT_NULL));
                int expectCnn = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
                assertEquals(expectCnn, alternative.getProperty(VariableProperty.CONTEXT_NOT_NULL));
            }
        };

        testSupportClass(List.of("SetOnce"), 0, 0, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

}
