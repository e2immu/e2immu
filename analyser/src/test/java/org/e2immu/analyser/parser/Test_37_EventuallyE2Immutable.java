
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

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.e2immu.analyser.config.StatementAnalyserVisitor;
import org.e2immu.analyser.config.TypeAnalyserVisitor;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

public class Test_37_EventuallyE2Immutable extends CommonTestRunner {

    @Test
    public void test_0() throws IOException {

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("setT".equals(d.methodInfo().name)) {
                if ("0.0.0".equals(d.statementId()) || "0".equals(d.statementId())) {
                    Assert.assertEquals("true", d.statementAnalysis().stateData.getPrecondition().toString());
                }
                if ("1.0.0".equals(d.statementId())) {
                    if (d.iteration() == 0) {
                        Assert.assertNull(d.statementAnalysis().stateData.getPrecondition());
                    } else {
                        Assert.assertEquals("null==t", d.statementAnalysis().stateData.getPrecondition().toString());
                        Assert.assertEquals("null==t", d.statementAnalysis().methodLevelData
                                .getCombinedPrecondition().toString());
                    }
                }
                if ("1".equals(d.statementId())) {
                    if (d.iteration() == 0) {
                        Assert.assertNull(d.statementAnalysis().stateData.getPrecondition());
                    } else {
                        Assert.assertEquals("true", d.statementAnalysis().stateData.getPrecondition().toString());
                        Assert.assertEquals("null==t", d.statementAnalysis().methodLevelData
                                .getCombinedPrecondition().toString());
                    }
                }
            }
            if ("set2".equals(d.methodInfo().name)) {
                String expectPrecondition = switch (d.iteration()) {
                    case 0 -> "<precondition>";
                    case 1 -> "null==<f:t>";
                    default -> "null==t";
                };
                Assert.assertEquals(expectPrecondition, d.statementAnalysis().stateData.getPrecondition().toString());
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("EventuallyE2Immutable_0".equals(d.typeInfo().simpleName)) {
                String expect = d.iteration() <= 1 ? "{}" : "{t=null==t}";
                Assert.assertEquals(expect, d.typeAnalysis().getApprovedPreconditionsE2().toString());
                Assert.assertEquals(d.iteration() > 1, d.typeAnalysis().approvedPreconditionsIsFrozen(true));

                int expectImmutable = d.iteration() <= 1 ? Level.DELAY : MultiLevel.EVENTUALLY_E2IMMUTABLE;
                Assert.assertEquals(expectImmutable, d.typeAnalysis().getProperty(VariableProperty.IMMUTABLE));
            }
        };


        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("getT".equals(d.methodInfo().name)) {
                if (d.iteration() == 0) {
                    Assert.assertNull(d.methodAnalysis().getSingleReturnValue());
                } else {
                    Assert.assertEquals("t", d.methodAnalysis().getSingleReturnValue().toString());
                }
            }
        };

        testClass("EventuallyE2Immutable_0", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test_1() throws IOException {

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("EventuallyE2Immutable_1".equals(d.typeInfo().simpleName)) {
                String expect = d.iteration() <= 1 ? "{}" : "{t=null==t}";
                Assert.assertEquals(expect, d.typeAnalysis().getApprovedPreconditionsE1().toString());
                Assert.assertEquals("{}", d.typeAnalysis().getApprovedPreconditionsE2().toString());
                Assert.assertEquals(d.iteration() > 1, d.typeAnalysis().approvedPreconditionsIsFrozen(true));

                int expectImmutable = d.iteration() <= 1 ? Level.DELAY : MultiLevel.EVENTUALLY_E2IMMUTABLE;
                Assert.assertEquals(expectImmutable, d.typeAnalysis().getProperty(VariableProperty.IMMUTABLE));
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("error".equals(d.methodInfo().name)) {
                String expectPrecondition = switch (d.iteration()) {
                    case 0 -> "<precondition>&&<precondition>";
                    case 1 -> "null==<f:t>&&null!=<f:t>";
                    default -> "false";
                };
                Assert.assertEquals(expectPrecondition, d.statementAnalysis().stateData.getPrecondition().toString());
                Assert.assertEquals(d.iteration() <= 1, d.statementAnalysis().stateData.preconditionIsDelayed());

                Assert.assertEquals(d.iteration() <= 1 ? "true" : "false", d.statementAnalysis().methodLevelData
                        .getCombinedPreconditionOrDelay().toString());
                Assert.assertEquals(d.iteration() <= 1, d.statementAnalysis().methodLevelData
                        .combinedPreconditionIsDelayed());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("error".equals(d.methodInfo().name)) {
                if (d.iteration() <= 1) {
                    Assert.assertNull(d.methodAnalysis().getPreconditionForMarkAndOnly());
                } else {
                    List<Expression> precondition = d.methodAnalysis().getPreconditionForMarkAndOnly();
                    Assert.assertEquals("[]", precondition.toString());
                }
            }
        };

        testClass("EventuallyE2Immutable_1", 1, 0, new DebugConfiguration.Builder()
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

}
