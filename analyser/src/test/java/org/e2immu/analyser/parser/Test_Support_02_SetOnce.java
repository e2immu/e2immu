
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

import org.e2immu.analyser.analyser.ConditionManager;
import org.e2immu.analyser.analyser.FieldAnalysisImpl;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MethodAnalysis;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterAnalysis;
import org.e2immu.analyser.model.expression.InlinedMethod;
import org.e2immu.analyser.model.expression.Negation;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.visitor.*;
import org.e2immu.support.SetOnce;
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
                assertEquals("Type param T", d.typeAnalysis().getTransparentTypes().toString());
                String expectE1 = d.iteration() <= 1 ? "{}" : "{t=null==t}";
                assertEquals(expectE1, d.typeAnalysis().getApprovedPreconditionsE1().toString());
                String expectE2 = d.iteration() <= 1 ? "{}" : "{t=null==t}";
                assertEquals(expectE2, d.typeAnalysis().getApprovedPreconditionsE2().toString());
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("t".equals(d.fieldInfo().name)) {
                assertEquals("<variable value>", d.fieldAnalysis().getValue().toString());
                assertEquals(Level.FALSE, d.fieldAnalysis().getProperty(VariableProperty.FINAL));
                String expectValues = d.iteration() == 0 ? "[null, <s:T>]" : "[null, t]";
                assertEquals(expectValues,
                        ((FieldAnalysisImpl.Builder) d.fieldAnalysis()).getValues().stream()
                                .map(FieldAnalysisImpl.ValueAndPropertyProxy::getValue).toList().toString());
                int expectImm = d.iteration() <= 2 ? Level.DELAY : MultiLevel.MUTABLE;
                assertEquals(expectImm, d.fieldAnalysis().getProperty(VariableProperty.IMMUTABLE));
                assertEquals(MultiLevel.NULLABLE, d.fieldAnalysis().getProperty(VariableProperty.EXTERNAL_NOT_NULL));

                String expectLinked = "setOnce.t:0,t:0";
                assertEquals(expectLinked, d.fieldAnalysis().getLinkedVariables().toString());

                int expectIdentity = d.iteration() <= 1 ? Level.DELAY : Level.FALSE;
                assertEquals(expectIdentity, d.fieldAnalysis().getProperty(VariableProperty.IDENTITY));

                int expectIndependent = d.iteration() == 0 ? Level.DELAY : MultiLevel.INDEPENDENT_1;
                assertEquals(expectIndependent, d.fieldAnalysis().getProperty(VariableProperty.INDEPENDENT));
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
            if ("getOrDefault".equals(d.methodInfo().name) && "0.0.0".equals(d.statementId())) {
                assertEquals(d.iteration() >= 1,
                        d.statementAnalysis().methodLevelData.linksHaveBeenEstablished.isSet());
                assertEquals("true", d.state().toString());
                String expectCondition = switch (d.iteration()) {
                    case 0 -> "<m:isSet>";
                    case 1 -> "null!=<f:t>";
                    default -> "null!=t";
                };
                assertEquals(expectCondition, d.condition().toString());
                String expectPrecondition = d.iteration() <= 1 ? "<precondition>" : "true";
                assertEquals(expectPrecondition, d.statementAnalysis().stateData.getPrecondition().expression().toString());
            }

            if ("getOrDefaultNull".equals(d.methodInfo().name)) {
                if ("0.0.0".equals(d.statementId())) {
                    ConditionManager cm = d.statementAnalysis().stateData.conditionManagerForNextStatement.get();
                    String expectCondition = switch (d.iteration()) {
                        case 0 -> "<m:isSet>";
                        case 1 -> "null!=<f:t>";
                        default -> "null!=t";
                    };
                    assertEquals(expectCondition, cm.condition().toString());
                    assertEquals(d.iteration() <= 1, d.condition().isDelayed(d.evaluationContext()));
                }
                if ("1".equals(d.statementId())) {
                    assertEquals("true", d.statementAnalysis().stateData.getPrecondition().expression().toString());
                }
            }

            if ("copy".equals(d.methodInfo().name)) {
                if ("0.0.0".equals(d.statementId())) {
                    ConditionManager cm = d.statementAnalysis().stateData.conditionManagerForNextStatement.get();
                    String expectCondition = switch (d.iteration()) {
                        case 0 -> "<m:isSet>";
                        case 1 -> "null!=<f:t>";
                        default -> "null!=other.t";
                    };
                    assertEquals(expectCondition, cm.condition().toString());
                    assertEquals(d.iteration() <= 1, d.condition().isDelayed(d.evaluationContext()));
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            final String TYPE = "org.e2immu.support.SetOnce";
            final String T0_FQN = TYPE + ".t$0";
            final String T0 = "t$0";
            int n = d.methodInfo().methodInspection.get().getParameters().size();

            if ("get".equals(d.methodInfo().name) && n == 0) {
                if ("0".equals(d.statementId())) {
                    if (T0_FQN.equals(d.variableName())) {
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
                    if (T0_FQN.equals(d.variableName())) {
                        assertTrue(d.iteration() > 0);
                        assertTrue(d.variableInfoContainer().isPrevious());
                        assertEquals("nullable instance type T", d.currentValue().toString());
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                    }
                }
            }
            if ("getOrDefaultNull".equals(d.methodInfo().name)) {
                if (d.variable() instanceof LocalVariableReference lvr && "t$0".equals(lvr.simpleName())) {
                    if ("1".equals(d.statementId())) {

                        int expectNne = d.iteration() <= 1 ? Level.DELAY : MultiLevel.NULLABLE;
                        assertEquals(expectNne, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                    }
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("0.0.0".equals(d.statementId())) {
                        // the return variable is not a parameter, so has no CONTEXT_DEPENDENT value
                        String expect = switch (d.iteration()) {
                            case 0 -> "<m:get>";
                            case 1 -> "<f:t>";
                            default -> "t";
                        };
                        assertEquals(expect, d.currentValue().toString());

                        // identity is computed from the value, which is "<m:get>" in the first iteration
                        // then 't'
                        int expectIdentity = d.iteration() <= 1 ? Level.DELAY : Level.FALSE; // calling the get method without params
                        assertEquals(expectIdentity, d.getProperty(VariableProperty.IDENTITY));

                        int expectIndependent = d.iteration() <= 1 ? Level.DELAY : MultiLevel.INDEPENDENT_1;
                        assertEquals(expectIndependent, d.getProperty(VariableProperty.INDEPENDENT));
                    }
                    if ("0".equals(d.statementId())) {
                        String expect = switch (d.iteration()) {
                            case 0 -> "<m:isSet>?<m:get>:<return value>";
                            case 1 -> "null==<f:t>?<return value>:<f:t>";
                            default -> "null==t?<return value>:t";
                        };
                        assertEquals(expect, d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        String expect = switch (d.iteration()) {
                            case 0 -> "<m:isSet>?<m:get>:null";
                            case 1 -> "null==<f:t>?null:<f:t>";
                            default -> "t$0"; // nice summary!
                        };
                        assertEquals(expect, d.currentValue().toString());

                        int expectNne = d.iteration() <= 1 ? Level.DELAY : MultiLevel.NULLABLE;
                        assertEquals(expectNne, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                    }
                }
            }
            if ("getOrDefault".equals(d.methodInfo().name)) {
                if (d.variable() instanceof This) {
                    assertEquals("org.e2immu.support.SetOnce.this", d.variable().fullyQualifiedName());
                }

                if ("0.0.0".equals(d.statementId())) {
                    if (d.variable() instanceof ReturnVariable) {
                        String expectValue = switch (d.iteration()) {
                            case 0 -> "<m:get>";
                            case 1 -> "<f:t>";
                            default -> "t";
                        };
                        assertEquals(expectValue, d.currentValue().toString());
                        int expectNne = d.iteration() <= 1 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                        assertEquals(expectNne, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                        int expectEnn = d.iteration() == 0 ? Level.DELAY : MultiLevel.NOT_INVOLVED;
                        assertEquals(expectEnn, d.getProperty(VariableProperty.EXTERNAL_NOT_NULL));
                    }
                    if (d.variable() instanceof FieldReference fr && "t".equals(fr.fieldInfo.name)) {
                        assertTrue(d.iteration() > 0);
                        assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.EXTERNAL_NOT_NULL));
                        assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                        int expectNne = d.iteration() == 1 ? Level.DELAY : MultiLevel.NULLABLE;
                        assertEquals(expectNne, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                    }
                    if (T0_FQN.equals(d.variableName())) {
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
                            default -> "null==t?<return value>:t";
                        };
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                }
                if ("1".equals(d.statementId())) {
                    if (d.variable() instanceof ReturnVariable) {
                        String expectValue = switch (d.iteration()) {
                            case 0 -> "<m:isSet>?<m:get>:alternative/*@NotNull*/";
                            case 1 -> "null==<f:t>?alternative/*@NotNull*/:<f:t>";
                            default -> "null==t$0?alternative/*@NotNull*/:t$0";
                        };
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("set".equals(d.methodInfo().name)) {
                ParameterAnalysis paramT = d.parameterAnalyses().get(0);
                int expectEnn = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
                assertEquals(expectEnn, paramT.getProperty(VariableProperty.EXTERNAL_NOT_NULL));

                if (d.iteration() == 0) {
                    assertNull(d.methodAnalysis().getPreconditionForEventual());
                } else {
                    assertEquals("null==t", d.methodAnalysis().getPreconditionForEventual().expression().toString());
                    assertEquals("null==t", d.methodAnalysis().getPrecondition().expression().toString());
                }
                assertEquals(Level.TRUE, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
                assertTrue(d.methodAnalysis().methodLevelData().linksHaveBeenEstablished.isSet());

                MethodAnalysis.Eventual eventual = d.methodAnalysis().getEventual();
                if (d.iteration() > 2) {
                    assertTrue(eventual.mark());
                } else {
                    assertSame(MethodAnalysis.DELAYED_EVENTUAL, eventual);
                }

                // void method, no return value
                assertNull(d.methodAnalysis().getSingleReturnValue());
            }

            // there are 2 get methods; one with a message parameter, the other without

            if ("get".equals(d.methodInfo().name)) {
                boolean hasParameter = d.methodInfo().methodInspection.get().getParameters().size() == 1;

                if (d.iteration() == 0) {
                    assertNull(d.methodAnalysis().getPreconditionForEventual());
                    assertNull(d.methodAnalysis().getSingleReturnValue());
                } else {
                    assertEquals("null!=t", d.methodAnalysis().getPreconditionForEventual()
                            .expression().toString());
                    assertEquals("t$0", d.methodAnalysis().getSingleReturnValue().toString());
                    if (d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod inlinedMethod) {
                        assertTrue(inlinedMethod.containsVariableFields());
                    } else fail();
                }
                assertEquals(Level.FALSE, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
                assertTrue(d.methodAnalysis().methodLevelData().linksHaveBeenEstablished.isSet());

                int expectIdentity = d.iteration() == 0 && hasParameter ? Level.DELAY : Level.FALSE;
                assertEquals(expectIdentity, d.methodAnalysis().getProperty(VariableProperty.IDENTITY));

                int expectIndependent = d.iteration() <= 1 ? Level.DELAY : MultiLevel.INDEPENDENT_1;
                assertEquals(expectIndependent, d.methodAnalysis().getProperty(VariableProperty.INDEPENDENT));

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
                    assertEquals("null==t",
                            d.methodAnalysis().getPreconditionForEventual().expression().toString());
                }
                if (d.iteration() <= 1) {
                    assertNull(d.methodAnalysis().getPrecondition());
                } else {
                    assertEquals("null==t||null==other.t",
                            d.methodAnalysis().getPrecondition().expression().toString());
                }
                assertEquals(d.iteration() >= 1,
                        d.methodAnalysis().methodLevelData().linksHaveBeenEstablished.isSet());
                int expectModified = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                assertEquals(expectModified, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));

                MethodAnalysis.Eventual eventual = d.methodAnalysis().getEventual();
                if (d.iteration() >= 3) {
                    assertTrue(eventual.mark());
                } else {
                    assertSame(MethodAnalysis.DELAYED_EVENTUAL, eventual);
                }
            }


            if ("toString".equals(d.methodInfo().name)) {
                assertNull(d.methodAnalysis().getPreconditionForEventual());

                assertEquals(Level.FALSE, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
            }

            if ("isSet".equals(d.methodInfo().name)) {
                assertNull(d.methodAnalysis().getPreconditionForEventual());
                if (d.iteration() > 0) {
                    assertEquals("null!=t$0", d.methodAnalysis().getSingleReturnValue().toString());
                    if (d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod im) {
                        assertTrue(im.expression() instanceof Negation);
                        assertTrue(im.containsVariableFields());
                    }
                }
                assertEquals(Level.FALSE, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
                assertEquals("Precondition[expression=true, causes=[]]",
                        d.methodAnalysis().getPrecondition().toString());
            }

            if ("getOrDefault".equals(d.methodInfo().name)) {
                int expectModified = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                assertEquals(expectModified, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
                if (d.iteration() <= 1) {
                    assertNull(d.methodAnalysis().getSingleReturnValue());
                }
                ParameterAnalysis alternative = d.parameterAnalyses().get(0);
                // because not @Final, we get NOT_INVOLVED
                int expectEnn = d.iteration() == 0 ? Level.DELAY : MultiLevel.NOT_INVOLVED;
                assertEquals(expectEnn, alternative.getProperty(VariableProperty.EXTERNAL_NOT_NULL));
                int expectCnn = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                assertEquals(expectCnn, alternative.getProperty(VariableProperty.CONTEXT_NOT_NULL));
            }

            if ("getOrDefaultNull".equals(d.methodInfo().name)) {
                if (d.iteration() <= 1) {
                    assertNull(d.methodAnalysis().getSingleReturnValue());
                } else {
                    // this should simply be t?
                    assertEquals("t$0", d.methodAnalysis().getSingleReturnValue().toString());
                }
                int expectNne = d.iteration() <= 1 ? Level.DELAY : MultiLevel.NULLABLE;
                assertEquals(expectNne, d.methodAnalysis().getProperty(VariableProperty.NOT_NULL_EXPRESSION));
            }
        };

        testSupportAndUtilClasses(List.of(SetOnce.class), 0, 0, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

}
