
/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.FieldAnalysisImpl;
import org.e2immu.analyser.analyser.LinkedVariables;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MethodAnalysis;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterAnalysis;
import org.e2immu.analyser.model.expression.InlinedMethod;
import org.e2immu.analyser.model.expression.Negation;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

public class Test_Own_04_SetOnce extends CommonTestRunner {

    public Test_Own_04_SetOnce() {
        super(true);
    }

    @Test
    public void test() throws IOException {
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("SetOnce".equals(d.typeInfo().simpleName)) {
                Assert.assertEquals("[Type param T]", d.typeAnalysis().getImplicitlyImmutableDataTypes().toString());
                String expectE1 = d.iteration() <= 1 ? "{}" : "{t=null==t}";
                Assert.assertEquals(expectE1, d.typeAnalysis().getApprovedPreconditionsE1().toString());
                String expectE2 = d.iteration() <= 1 ? "{}" : "{t=null==t}";
                Assert.assertEquals(expectE2, d.typeAnalysis().getApprovedPreconditionsE2().toString());
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("t".equals(d.fieldInfo().name)) {
                Assert.assertNull(d.fieldAnalysis().getEffectivelyFinalValue());
                Assert.assertEquals(Level.FALSE, d.fieldAnalysis().getProperty(VariableProperty.FINAL));
                String expectValues = d.iteration() == 0 ? "[null,<s:T>]" : "[null,t]";
                Assert.assertEquals(expectValues,
                        ((FieldAnalysisImpl.Builder) d.fieldAnalysis()).getValues().toString());
                int expectImmu = d.iteration() <= 1 ? Level.DELAY : MultiLevel.MUTABLE;
                Assert.assertEquals(expectImmu, d.fieldAnalysis().getProperty(VariableProperty.IMMUTABLE));
                Assert.assertEquals(MultiLevel.NULLABLE, d.fieldAnalysis().getProperty(VariableProperty.EXTERNAL_NOT_NULL));

                String expectLinked = d.iteration() == 0  ? LinkedVariables.DELAY_STRING : "";
                Assert.assertEquals(expectLinked, d.fieldAnalysis().getLinkedVariables().toString());
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("get".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    Assert.assertEquals(0, d.statementAnalysis().flowData.getTimeAfterSubBlocks());

                    Assert.assertEquals("true", d.statementAnalysis().stateData
                            .getConditionManagerForNextStatement().state().toString());
                    Assert.assertEquals("true", d.statementAnalysis().stateData
                            .getConditionManagerForNextStatement().precondition().toString());
                }
                if ("1".equals(d.statementId())) {
                    Assert.assertEquals(d.iteration() == 0,
                            d.statementAnalysis().methodLevelData.internalObjectFlowNotYetFrozen());
                    Assert.assertEquals(0, d.statementAnalysis().flowData.getTimeAfterSubBlocks());
                }
            }
            if ("getOrElse".equals(d.methodInfo().name) && "0.0.0".equals(d.statementId())) {
                Assert.assertEquals(d.iteration() > 1,
                        d.statementAnalysis().methodLevelData.linksHaveBeenEstablished.isSet());
                String expectState = d.iteration() == 0 ? "<precondition>" : "true";
                Assert.assertEquals(expectState, d.statementAnalysis().stateData.getPrecondition().toString());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            final String TYPE = "org.e2immu.analyser.util.SetOnce";
            final String T0 = TYPE + ".t$0";
            int n = d.methodInfo().methodInspection.get().getParameters().size();

            if ("get".equals(d.methodInfo().name) && n == 0) {
                if ("0".equals(d.statementId())) {
                    if (T0.equals(d.variableName())) {
                        Assert.assertTrue(d.iteration() > 0);
                        Assert.assertEquals("nullable instance type T", d.currentValue().toString());
                        Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                    }
                }
                if ("1".equals(d.statementId())) {
                    if (d.variable() instanceof ReturnVariable) {
                        String expectValue = d.iteration() == 0 ? "<f:t>" : T0;
                        Assert.assertEquals(expectValue, d.currentValue().toString());
                        int expectCnn = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                        Assert.assertEquals(expectCnn, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                        //  Assert.assertEquals(expectCnn, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                    }
                    if (T0.equals(d.variableName())) {
                        Assert.assertTrue(d.iteration() > 0);
                        Assert.assertTrue(d.variableInfoContainer().isPrevious());
                        Assert.assertEquals("nullable instance type T", d.currentValue().toString());
                        Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                    }
                }
            }
            if ("getOrElse".equals(d.methodInfo().name)) {
                if ("0.0.0".equals(d.statementId())) {
                    if (d.variable() instanceof ReturnVariable) {
                        String expectValue = switch (d.iteration()) {
                            case 0 -> "<m:get>";
                            case 1 -> "<f:t>";
                            default -> "org.e2immu.analyser.util.SetOnce.t$0";
                        };
                        Assert.assertEquals(expectValue, d.currentValue().toString());
                        int expectNne = d.iteration() <= 1 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                        Assert.assertEquals(expectNne, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                        int expectEnn = d.iteration() <= 1 ? Level.DELAY : MultiLevel.NULLABLE;
                        Assert.assertEquals(expectEnn, d.getProperty(VariableProperty.EXTERNAL_NOT_NULL));
                    }
                    if (d.variable() instanceof FieldReference fr && "t".equals(fr.fieldInfo.name)) {
                        Assert.assertTrue(d.iteration() > 0);
                        Assert.assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.EXTERNAL_NOT_NULL));
                        Assert.assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                        Assert.assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                    }
                    if (T0.equals(d.variableName())) {
                        Assert.assertTrue(d.iteration() > 1);
                        Assert.assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.EXTERNAL_NOT_NULL));
                        Assert.assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                        Assert.assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                    }
                }
                if ("0".equals(d.statementId())) {
                    if (d.variable() instanceof ReturnVariable) {
                        String expectValue = switch (d.iteration()) {
                            case 0 -> "<m:isSet>?<m:get>:<return value>";
                            case 1 -> "null==<f:t>?<return value>:<f:t>";
                            default -> "null==org.e2immu.analyser.util.SetOnce.t$0?<return value>:org.e2immu.analyser.util.SetOnce.t$0";
                        };
                        Assert.assertEquals(expectValue, d.currentValue().toString());
                    }
                }
                if ("1".equals(d.statementId())) {
                    if (d.variable() instanceof ReturnVariable) {
                        String expectValue = switch (d.iteration()) {
                            case 0 -> "<m:isSet>?<m:get>:alternative";
                            case 1 -> "null==<f:t>?alternative:<f:t>";
                            default -> "null==org.e2immu.analyser.util.SetOnce.t$0?alternative:org.e2immu.analyser.util.SetOnce.t$0";
                        };
                        Assert.assertEquals(expectValue, d.currentValue().toString());
                    }
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("set".equals(d.methodInfo().name)) {
                ParameterAnalysis paramT = d.parameterAnalyses().get(0);
                // because not @Final, we get NOT_INVOLVED
                int expectEnn = d.iteration() == 0 ? Level.DELAY : MultiLevel.NOT_INVOLVED;
                Assert.assertEquals(expectEnn, paramT.getProperty(VariableProperty.EXTERNAL_NOT_NULL));

                if (d.iteration() == 0) {
                    Assert.assertNull(d.methodAnalysis().getPreconditionForMarkAndOnly());
                } else {
                    Assert.assertEquals("[null==t]", d.methodAnalysis().getPreconditionForMarkAndOnly().toString());
                    Assert.assertEquals("null==t", d.methodAnalysis().getPrecondition().toString());
                }
                Assert.assertEquals(Level.TRUE, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
                Assert.assertEquals(d.iteration() > 0, d.methodAnalysis().methodLevelData().linksHaveBeenEstablished.isSet());

                MethodAnalysis.MarkAndOnly markAndOnly = d.methodAnalysis().getMarkAndOnly();
                if (d.iteration() > 2) {
                    Assert.assertTrue(markAndOnly.mark());
                } else {
                    Assert.assertNull(markAndOnly);
                }
            }

            if ("get".equals(d.methodInfo().name)) {
                if (d.iteration() == 0) {
                    Assert.assertNull(d.methodAnalysis().getPreconditionForMarkAndOnly());
                    Assert.assertNull(d.methodAnalysis().getSingleReturnValue());
                } else {
                    Assert.assertEquals("[null!=t]", d.methodAnalysis().getPreconditionForMarkAndOnly().toString());
                    Assert.assertEquals("t", d.methodAnalysis().getSingleReturnValue().toString());
                }
                int expectModified = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                Assert.assertEquals(expectModified, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
                Assert.assertEquals(d.iteration() > 0, d.methodAnalysis().methodLevelData().linksHaveBeenEstablished.isSet());

                MethodAnalysis.MarkAndOnly markAndOnly = d.methodAnalysis().getMarkAndOnly();
                if (d.iteration() > 2) {
                    Assert.assertTrue(markAndOnly.after());
                } else {
                    Assert.assertNull(markAndOnly);
                }
            }

            if ("copy".equals(d.methodInfo().name)) {
                if (d.iteration() <= 1) {
                    Assert.assertNull(d.methodAnalysis().getPreconditionForMarkAndOnly());
                } else {
                    Assert.assertEquals("[null==t]",
                            d.methodAnalysis().getPreconditionForMarkAndOnly().toString());
                }
                Assert.assertEquals(d.iteration() > 1, d.methodAnalysis().methodLevelData().linksHaveBeenEstablished.isSet());
                int expectModified = d.iteration() <= 1 ? Level.DELAY : Level.TRUE;
                Assert.assertEquals(expectModified, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));

                MethodAnalysis.MarkAndOnly markAndOnly = d.methodAnalysis().getMarkAndOnly();
                if (d.iteration() > 2) {
                    Assert.assertTrue(markAndOnly.mark());
                } else {
                    Assert.assertNull(markAndOnly);
                }
            }

            if ("toString".equals(d.methodInfo().name)) {
                if (d.iteration() == 0) {
                    Assert.assertNull(d.methodAnalysis().getPreconditionForMarkAndOnly());
                } else {
                    Assert.assertEquals("[]", d.methodAnalysis().getPreconditionForMarkAndOnly().toString());
                }
                int expectModified = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                Assert.assertEquals(expectModified, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
            }

            if ("isSet".equals(d.methodInfo().name)) {
                if (d.iteration() == 0) {
                    Assert.assertNull(d.methodAnalysis().getPreconditionForMarkAndOnly());
                } else {
                    Assert.assertEquals("null!=t", d.methodAnalysis().getSingleReturnValue().toString());
                    Assert.assertTrue(d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod im
                            && im.expression() instanceof Negation);
                    Assert.assertEquals("[]", d.methodAnalysis().getPreconditionForMarkAndOnly().toString());
                }
                int expectModified = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                Assert.assertEquals(expectModified, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
            }

            if ("getOrElse".equals(d.methodInfo().name)) {
                int expectModified = d.iteration() <= 1 ? Level.DELAY : Level.FALSE;
                Assert.assertEquals(expectModified, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
                if (d.iteration() <= 1) {
                    Assert.assertNull(d.methodAnalysis().getSingleReturnValue());
                } else {
                    //      Assert.assertEquals("null==t?alternative:t", d.methodAnalysis().getSingleReturnValue().toString());
                }

                ParameterAnalysis alternative = d.parameterAnalyses().get(0);
                // because not @Final, we get NOT_INVOLVED
                int expectEnn = d.iteration() == 0 ? Level.DELAY : MultiLevel.NOT_INVOLVED;
                Assert.assertEquals(expectEnn, alternative.getProperty(VariableProperty.EXTERNAL_NOT_NULL));
                int expectCnn = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
                Assert.assertEquals(expectCnn, alternative.getProperty(VariableProperty.CONTEXT_NOT_NULL));
            }
        };

        testWithUtilClasses(List.of(), List.of("SetOnce"), 0, 0, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

}
