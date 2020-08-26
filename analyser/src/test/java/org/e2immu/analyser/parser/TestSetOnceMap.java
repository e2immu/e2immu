
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

import org.e2immu.analyser.analyser.TransferValue;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.abstractvalue.PropertyWrapper;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

public class TestSetOnceMap extends CommonTestRunner {

    MethodAnalyserVisitor methodAnalyserVisitor = new MethodAnalyserVisitor() {
        @Override
        public void visit(int iteration, MethodInfo methodInfo) {
            if ("get".equals(methodInfo.name)) {
                Value srv = methodInfo.methodAnalysis.get().singleReturnValue.get();
                Assert.assertSame(UnknownValue.RETURN_VALUE, srv);

                TransferValue tv = methodInfo.methodAnalysis.get().returnStatementSummaries.get("1");
                Assert.assertNotNull(tv);
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, tv.properties.get(VariableProperty.NOT_NULL));
                Assert.assertTrue(tv.value.get() instanceof PropertyWrapper);
                Assert.assertEquals(MultiLevel.EFFECTIVE, MultiLevel.value(
                        methodInfo.methodAnalysis.get().getProperty(VariableProperty.NOT_NULL), MultiLevel.NOT_NULL));
            }
            if ("isEmpty".equals(methodInfo.name)) {
                TransferValue tv = methodInfo.methodAnalysis.get().returnStatementSummaries.get("0");
                Assert.assertNotNull(tv);
                Assert.assertEquals("0 == this.map.size(),?>=0", tv.value.get().toString());

                // there is no reason to have a @Size annotation on this expression
                Assert.assertEquals(Level.DELAY, tv.getProperty(VariableProperty.SIZE));

                Value srv = methodInfo.methodAnalysis.get().singleReturnValue.get();
                Assert.assertSame(UnknownValue.RETURN_VALUE, srv);
                // @Size(equals = 0)
                Assert.assertEquals(Level.SIZE_EMPTY, methodInfo.methodAnalysis.get().getProperty(VariableProperty.SIZE));
            }
            if ("stream".equals(methodInfo.name)) {
                TransferValue tv = methodInfo.methodAnalysis.get().returnStatementSummaries.get("0");
                Assert.assertNotNull(tv);
                Value stream = tv.value.get();
                Assert.assertEquals("this.map.entrySet().stream()", stream.toString());
                Assert.assertEquals(Level.SIZE_COPY_TRUE, stream.getPropertyOutsideContext(VariableProperty.SIZE_COPY));
            }
        }
    };

    // TODO: Accepting one error now: we have not inferred @Size(min = 1) for put (modifying method)
    // This is work for later

    @Test
    public void test() throws IOException {
        testUtilClass(List.of("Freezable", "SetOnceMap"), 1, 1, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

}
