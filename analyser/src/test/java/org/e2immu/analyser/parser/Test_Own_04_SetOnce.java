
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
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
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
                String expectE1 = d.iteration() == 0 ? "{}" : "{t=null==t}";
                Assert.assertEquals(expectE1, d.typeAnalysis().getApprovedPreconditionsE1().toString());
                Assert.assertEquals(expectE1, d.typeAnalysis().getApprovedPreconditionsE2().toString());
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("t".equals(d.fieldInfo().name)) {
                Assert.assertNull(d.fieldAnalysis().getEffectivelyFinalValue());
                Assert.assertEquals(Level.FALSE, d.fieldAnalysis().getProperty(VariableProperty.FINAL));
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("get".equals(d.methodInfo().name) && "1".equals(d.statementId())) {
                Assert.assertEquals(d.iteration() == 0,
                        d.statementAnalysis().methodLevelData.internalObjectFlowNotYetFrozen());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("set".equals(d.methodInfo().name)) {
                if (d.iteration() == 0) {
                    Assert.assertNull(d.methodAnalysis().getPreconditionForMarkAndOnly());
                } else {
                    Assert.assertEquals("[null==t]", d.methodAnalysis().getPreconditionForMarkAndOnly().toString());
                    Assert.assertEquals("null==t", d.methodAnalysis().getPrecondition().toString());
                }
                Assert.assertEquals(Level.TRUE, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
                Assert.assertEquals(d.iteration() > 0, d.methodAnalysis().methodLevelData().linksHaveBeenEstablished.isSet());
            }
            if ("get".equals(d.methodInfo().name)) {
                if (d.iteration() == 0) {
                    Assert.assertNull(d.methodAnalysis().getPreconditionForMarkAndOnly());
                    Assert.assertNull(d.methodAnalysis().getSingleReturnValue());
                } else {
                    Assert.assertEquals("[null!=t]", d.methodAnalysis().getPreconditionForMarkAndOnly().toString());
                    Assert.assertEquals("org.e2immu.analyser.util.SetOnce.t$0", d.methodAnalysis().getSingleReturnValue().toString());
                }
                int expectModified = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                Assert.assertEquals(expectModified, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
                Assert.assertEquals(d.iteration() > 0, d.methodAnalysis().methodLevelData().linksHaveBeenEstablished.isSet());

            }
            if ("copy".equals(d.methodInfo().name)) {
                if (d.iteration() == 0) {
                    Assert.assertNull(d.methodAnalysis().getPreconditionForMarkAndOnly());
                } else {
                    Assert.assertEquals("[null==t]",
                            d.methodAnalysis().getPreconditionForMarkAndOnly().toString());
                }
                Assert.assertEquals(d.iteration() > 0, d.methodAnalysis().methodLevelData().linksHaveBeenEstablished.isSet());
                int expectModified = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                Assert.assertEquals(expectModified, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
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
                    Assert.assertEquals("[]", d.methodAnalysis().getPreconditionForMarkAndOnly().toString());
                }
                int expectModified = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                Assert.assertEquals(expectModified, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
            }
        };

        testUtilClass(List.of("SetOnce"), 0, 0, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

}
