
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
import org.e2immu.analyser.model.Analysis;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.abstractvalue.MethodValue;
import org.e2immu.analyser.model.abstractvalue.PropertyWrapper;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class TestSetOnceMap extends CommonTestRunner {

    MethodAnalyserVisitor methodAnalyserVisitor = new MethodAnalyserVisitor() {
        @Override
        public void visit(int iteration, MethodInfo methodInfo) {
            if ("get".equals(methodInfo.name)) {
                Value srv = methodInfo.methodAnalysis.get().singleReturnValue.get();
                Assert.assertEquals("org.e2immu.analyser.util.SetOnceMap<K, V>.get()", srv.toString());
                Assert.assertTrue("Have " + srv.getClass(), srv instanceof MethodValue);
                Assert.assertEquals(Level.TRUE, Level.value(srv.getPropertyOutsideContext(VariableProperty.NOT_NULL), Level.NOT_NULL));

                TransferValue tv = methodInfo.methodAnalysis.get().returnStatementSummaries.get("1");
                Assert.assertNotNull(tv);
                Assert.assertEquals(Level.TRUE, tv.properties.get(VariableProperty.NOT_NULL));
                Assert.assertTrue(tv.value.get() instanceof PropertyWrapper);
                Assert.assertEquals(Level.TRUE, Level.value(methodInfo.methodAnalysis.get().getProperty(VariableProperty.NOT_NULL), Level.NOT_NULL));
            }
            if ("isEmpty".equals(methodInfo.name)) {
                TransferValue tv = methodInfo.methodAnalysis.get().returnStatementSummaries.get("0");
                Assert.assertNotNull(tv);
                Assert.assertEquals("0 == map.size(),?>=0", tv.value.get().toString());

                // there is no reason to have a @Size annotation on this expression
                Assert.assertEquals(Level.DELAY, tv.properties.getOtherwise(VariableProperty.SIZE, Level.DELAY));

                Value srv = methodInfo.methodAnalysis.get().singleReturnValue.get();
                Assert.assertEquals("org.e2immu.analyser.util.SetOnceMap<K, V>.isEmpty()", srv.toString());
                Assert.assertTrue("Have " + srv.getClass(), srv instanceof MethodValue);
                // @Size(equals = 0)
                Assert.assertEquals(Analysis.SIZE_EMPTY, methodInfo.methodAnalysis.get().getProperty(VariableProperty.SIZE));
            }
            if ("stream".equals(methodInfo.name)) {
                TransferValue tv = methodInfo.methodAnalysis.get().returnStatementSummaries.get("0");
                Assert.assertNotNull(tv);
                Value stream = tv.value.get();
                Assert.assertEquals("map.entrySet().stream()", stream.toString());
                Assert.assertEquals(Level.TRUE_LEVEL_1, stream.getPropertyOutsideContext(VariableProperty.SIZE_COPY));
            }
        }
    };


    @Test
    public void test() throws IOException {
        testUtilClass("SetOnceMap", 0, 1, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

}
