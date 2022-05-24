
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

package org.e2immu.analyser.parser.own.support;

import org.e2immu.analyser.analyser.ConditionManager;
import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.impl.FieldAnalysisImpl;
import org.e2immu.analyser.analysis.impl.ValueAndPropertyProxy;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.expression.InlinedMethod;
import org.e2immu.analyser.model.expression.Negation;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
import org.e2immu.support.SetOnce;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.e2immu.analyser.analyser.Property.EXTERNAL_NOT_NULL;
import static org.e2immu.analyser.analyser.Property.NOT_NULL_EXPRESSION;
import static org.junit.jupiter.api.Assertions.*;

public class Test_Support_02_SetOnce extends CommonTestRunner {

    // cannot be set to true because there is a OrgE2ImmuSupport.java A API file which refers to this type.
    // we currently cannot have both at the same time
    public Test_Support_02_SetOnce() {
        super(false);
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
                assertEquals(DV.FALSE_DV, d.fieldAnalysis().getProperty(Property.FINAL));
                String expectValues = "[null, t]";
                if (d.iteration() > 0) {
                    assertEquals(expectValues,
                            ((FieldAnalysisImpl.Builder) d.fieldAnalysis()).getValues().stream()
                                    .map(ValueAndPropertyProxy::getValue).toList().toString());
                }
                assertDv(d, 3, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
                assertDv(d, 1, MultiLevel.NULLABLE_DV, EXTERNAL_NOT_NULL);

                assertEquals("t:0", d.fieldAnalysis().getLinkedVariables().toString());

                assertDv(d, 2, DV.FALSE_DV, Property.IDENTITY);
                assertDv(d, 1, MultiLevel.INDEPENDENT_1_DV, Property.INDEPENDENT);
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("get".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertEquals(0, d.statementAnalysis().flowData().getTimeAfterSubBlocks());
                    assertEquals("true", d.statementAnalysis().stateData()
                            .getConditionManagerForNextStatement().state().toString());
                    assertTrue(d.statementAnalysis().stateData()
                            .getConditionManagerForNextStatement().precondition().isEmpty());
                }
                if ("1".equals(d.statementId())) {
                    assertEquals(0, d.statementAnalysis().flowData().getTimeAfterSubBlocks());
                }
            }
            if ("getOrDefault".equals(d.methodInfo().name) && "0.0.0".equals(d.statementId())) {
                assertEquals(d.iteration() > 1, d.statementAnalysis().methodLevelData().linksHaveBeenEstablished());
                assertEquals("true", d.state().toString());
                String expectCondition = switch (d.iteration()) {
                    case 0, 1 -> "<m:isSet>";
                    default -> "null!=`t`";
                };
                assertEquals(expectCondition, d.condition().toString());
                String expectPrecondition = d.iteration() <= 1 ? "<precondition>" : "true";
                assertEquals(expectPrecondition, d.statementAnalysis().stateData().getPrecondition().expression().toString());
            }

            if ("getOrDefaultNull".equals(d.methodInfo().name)) {
                if ("0.0.0".equals(d.statementId())) {
                    ConditionManager cm = d.statementAnalysis().stateData().getConditionManagerForNextStatement();
                    String expectCondition = switch (d.iteration()) {
                        case 0, 1 -> "<m:isSet>";
                        default -> "null!=`t`";
                    };
                    assertEquals(expectCondition, cm.condition().toString());
                    assertEquals(d.iteration() <= 1, d.condition().isDelayed());
                }
                if ("1".equals(d.statementId())) {
                    assertEquals("true", d.statementAnalysis().stateData().getPrecondition().expression().toString());
                }
            }

            if ("copy".equals(d.methodInfo().name)) {
                if ("0.0.0".equals(d.statementId())) {
                    ConditionManager cm = d.statementAnalysis().stateData().getConditionManagerForNextStatement();
                    String expectCondition = switch (d.iteration()) {
                        case 0, 1 -> "<m:isSet>";
                        default -> "null!=`other.t`";
                    };
                    assertEquals(expectCondition, cm.condition().toString());
                    assertEquals(d.iteration() <= 1, d.condition().isDelayed());
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
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(Property.CONTEXT_NOT_NULL));
                    }
                }
                if ("1".equals(d.statementId())) {
                    if (d.variable() instanceof ReturnVariable) {
                        String expectValue = switch (d.iteration()) {
                            case 0 -> "<f:t>";
                            case 1 -> "<s:T>";
                            default -> T0;
                        };
                        assertEquals(expectValue, d.currentValue().toString());
                        assertDv(d, 2, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                    }
                    if (T0_FQN.equals(d.variableName())) {
                        assertTrue(d.iteration() > 0);
                        assertTrue(d.variableInfoContainer().isPrevious());
                        assertEquals("nullable instance type T", d.currentValue().toString());
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(Property.CONTEXT_NOT_NULL));
                    }
                }
            }
            if ("getOrDefaultNull".equals(d.methodInfo().name)) {
                if (d.variable() instanceof LocalVariableReference lvr && "t$0".equals(lvr.simpleName())) {
                    if ("1".equals(d.statementId())) {
                        assertDv(d, 2, MultiLevel.NULLABLE_DV, NOT_NULL_EXPRESSION);
                    }
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("0.0.0".equals(d.statementId())) {
                        // the return variable is not a parameter, so has no CONTEXT_DEPENDENT value
                        String expect = switch (d.iteration()) {
                            case 0, 1 -> "<m:get>";
                            default -> "`this.t`";
                        };
                        assertEquals(expect, d.currentValue().toString());

                        // identity is computed from the value, which is "<m:get>" in the first iteration
                        // then 't'
                        assertDv(d, 2, DV.FALSE_DV, Property.IDENTITY);
                        assertDv(d, 2, MultiLevel.INDEPENDENT_1_DV, Property.INDEPENDENT);
                    }
                    if ("0".equals(d.statementId())) {
                        String expect = switch (d.iteration()) {
                            case 0, 1 -> "<m:isSet>?<m:get>:<return value>";
                            default -> "null==`t`?<return value>:`t`";
                        };
                        assertEquals(expect, d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        String expect = switch (d.iteration()) {
                            case 0, 1 -> "<m:isSet>?<m:get>:null";
                            default -> "`this.t`";
                        };
                        assertEquals(expect, d.currentValue().toString());

                        assertDv(d, 2, MultiLevel.NULLABLE_DV, NOT_NULL_EXPRESSION);
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
                            case 0, 1 -> "<m:get>";
                            default -> "`this.t`";
                        };
                        assertEquals(expectValue, d.currentValue().toString());
                        assertDv(d, 2, MultiLevel.NULLABLE_DV, NOT_NULL_EXPRESSION);
                        assertDv(d, 1, MultiLevel.NOT_INVOLVED_DV, EXTERNAL_NOT_NULL);
                    }
                    if (d.variable() instanceof FieldReference fr && "t".equals(fr.fieldInfo.name)) {
                        assertTrue(d.iteration() > 0);
                        assertEquals(MultiLevel.NULLABLE_DV, d.getProperty(EXTERNAL_NOT_NULL));
                        assertEquals(MultiLevel.NULLABLE_DV, d.getProperty(Property.CONTEXT_NOT_NULL));
                        assertDv(d, 2, MultiLevel.NULLABLE_DV, NOT_NULL_EXPRESSION);
                    }
                    if (T0_FQN.equals(d.variableName())) {
                        assertTrue(d.iteration() > 1);
                        assertEquals(MultiLevel.NULLABLE_DV, d.getProperty(EXTERNAL_NOT_NULL));
                        assertEquals(MultiLevel.NULLABLE_DV, d.getProperty(Property.CONTEXT_NOT_NULL));
                        assertEquals(MultiLevel.NULLABLE_DV, d.getProperty(NOT_NULL_EXPRESSION));
                    }
                }
                if ("0".equals(d.statementId())) {
                    if (d.variable() instanceof ReturnVariable) {
                        String expectValue = switch (d.iteration()) {
                            case 0, 1 -> "<m:isSet>?<m:get>:<return value>";
                            default -> "null==`t`?<return value>:`t`";
                        };
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                }
                if ("1".equals(d.statementId())) {
                    if (d.variable() instanceof ReturnVariable) {
                        String expectValue = switch (d.iteration()) {
                            case 0, 1 -> "<m:isSet>?<m:get>:alternative/*@NotNull*/";
                            default -> "null==`t`?alternative/*@NotNull*/:`t`";
                        };
                        assertEquals(expectValue, d.currentValue().toString());
                        assertDv(d, 2, MultiLevel.EFFECTIVELY_NOT_NULL_DV, NOT_NULL_EXPRESSION);
                    }
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("set".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 2, MultiLevel.NULLABLE_DV, EXTERNAL_NOT_NULL);

                String expectPc = switch (d.iteration()) {
                    case 0 -> "<precondition>";
                    case 1 -> "!<null-check>";
                    default -> "null==t";
                };
                assertEquals(expectPc, d.methodAnalysis().getPreconditionForEventual().expression().toString());

                String expected = switch (d.iteration()) {
                    case 0 -> "<precondition>&&!<null-check>";
                    case 1 -> "!<null-check>";
                    default -> "null==t";
                };
                assertEquals(expected, d.methodAnalysis().getPrecondition().expression().toString());

                assertEquals(DV.TRUE_DV, d.methodAnalysis().getProperty(Property.MODIFIED_METHOD));
                assertEquals(d.iteration() > 0, d.methodAnalysis().methodLevelData().linksHaveBeenEstablished());

                MethodAnalysis.Eventual eventual = d.methodAnalysis().getEventual();
                if (d.iteration() >= 3) {
                    assertTrue(eventual.mark());
                } else {
                    assertTrue(eventual.causesOfDelay().isDelayed());
                }

                // void method, no return value
                assertEquals("<no return value>", d.methodAnalysis().getSingleReturnValue().toString());
            }

            // there are 2 get methods; one with a message parameter, the other without

            if ("get".equals(d.methodInfo().name) && d.methodInfo().methodInspection.get().getParameters().isEmpty()) {
                String expectedPc = switch (d.iteration()) {
                    case 0 -> "Precondition[expression=<precondition>, causes=[]]";
                    case 1 -> "Precondition[expression=!<null-check>, causes=[escape]]";
                    default -> "Precondition[expression=null!=t, causes=[escape]]";
                };
                assertEquals(expectedPc, d.methodAnalysis().getPreconditionForEventual().toString());
                String expectedSrv = d.iteration() <= 1 ? "<m:get>" : "/*inline get*/t$0";
                assertEquals(expectedSrv, d.methodAnalysis().getSingleReturnValue().toString());
                if (d.iteration() > 1) {
                    if (d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod inlinedMethod) {
                        assertTrue(inlinedMethod.containsVariableFields());
                    } else fail();
                }
                assertDv(d, 1, DV.FALSE_DV, Property.MODIFIED_METHOD);
                assertEquals(d.iteration() > 0, d.methodAnalysis().methodLevelData().linksHaveBeenEstablished());

                assertDv(d, DV.FALSE_DV, Property.IDENTITY);
                assertDv(d, 2, MultiLevel.INDEPENDENT_1_DV, Property.INDEPENDENT);


                MethodAnalysis.Eventual eventual = d.methodAnalysis().getEventual();
                if (d.iteration() >= 3) {
                    assertTrue(eventual.after());
                } else {
                    assertTrue(eventual.causesOfDelay().isDelayed());
                }
            }

            if ("copy".equals(d.methodInfo().name)) {
                String expectedPc = switch (d.iteration()) {
                    case 0 -> "Precondition[expression=<precondition>, causes=[]]";
                    case 1 -> "Precondition[expression=<precondition>&&<precondition>, causes=[]]";
                    default -> "Precondition[expression=null==t, causes=[methodCall:set]]";
                };
                assertEquals(expectedPc, d.methodAnalysis().getPreconditionForEventual().toString());

                String expected = switch (d.iteration()) {
                    case 0, 1 -> "<precondition>&&<precondition>";
                    default -> "null==t";
                };
                assertEquals(expected, d.methodAnalysis().getPrecondition().expression().toString());

                assertEquals(d.iteration() >= 2, d.methodAnalysis().methodLevelData().linksHaveBeenEstablished());
                assertDv(d, DV.TRUE_DV, Property.MODIFIED_METHOD);

                MethodAnalysis.Eventual eventual = d.methodAnalysis().getEventual();
                if (d.iteration() >= 3) {
                    assertTrue(eventual.mark());
                } else {
                    assertTrue(eventual.causesOfDelay().isDelayed());
                }
            }

            if ("toString".equals(d.methodInfo().name)) {
                String expectedPc = d.iteration() == 0 ? "Precondition[expression=<precondition>, causes=[]]"
                        : "Precondition[expression=true, causes=[]]";
                assertEquals(expectedPc, d.methodAnalysis().getPreconditionForEventual().toString());

                assertDv(d, 1, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }

            if ("isSet".equals(d.methodInfo().name)) {
                String expected = d.iteration() == 0
                        ? "Precondition[expression=<precondition>, causes=[]]"
                        : "Precondition[expression=true, causes=[]]";
                assertEquals(expected,
                        d.methodAnalysis().getPreconditionForEventual().toString());
                String srv = d.iteration() <= 1 ? "<m:isSet>" : "/*inline isSet*/null!=t$0";
                assertEquals(srv, d.methodAnalysis().getSingleReturnValue().toString());
                if (d.iteration() > 1) {
                    if (d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod im) {
                        assertTrue(im.expression() instanceof Negation);
                        assertTrue(im.containsVariableFields());
                    } else fail();
                }
                assertDv(d, 1, DV.FALSE_DV, Property.MODIFIED_METHOD);
                assertEquals("Precondition[expression=true, causes=[]]",
                        d.methodAnalysis().getPrecondition().toString());
            }

            if ("getOrDefault".equals(d.methodInfo().name)) {
                assertDv(d, 2, DV.FALSE_DV, Property.MODIFIED_METHOD);

                String expected = d.iteration() <= 1 ? "<m:getOrDefault>"
                        : "/*inline getOrDefault*/null==`t`?alternative/*@NotNull*/:`t`";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());

                // because not @Final, we get NOT_INVOLVED
                assertDv(d.p(0), 1, MultiLevel.NOT_INVOLVED_DV, EXTERNAL_NOT_NULL);
                assertDv(d.p(0), 3, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
            }

            if ("getOrDefaultNull".equals(d.methodInfo().name)) {

                // this should simply be t?
                String expected = d.iteration() <= 1 ? "<m:getOrDefaultNull>" : "/*inline getOrDefaultNull*/`t`";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());

                assertDv(d, 2, MultiLevel.NULLABLE_DV, NOT_NULL_EXPRESSION);
            }
        };

        testSupportAndUtilClasses(List.of(SetOnce.class), 0, 0, new DebugConfiguration.Builder()
           //     .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
           //     .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
           //     .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
           //     .addStatementAnalyserVisitor(statementAnalyserVisitor)
           //     .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

}
