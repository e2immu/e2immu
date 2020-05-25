
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

import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.e2immu.analyser.model.*;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Set;

public class TestSetOnce extends CommonTestRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestSetOnce.class);

    MethodAnalyserVisitor methodAnalyserVisitor = new MethodAnalyserVisitor() {
        @Override
        public void visit(int iteration, MethodInfo methodInfo) {
            MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
            if ("get".equals(methodInfo.name)) {
                if (iteration == 0) {
                    Assert.assertFalse(methodAnalysis.variablesLinkedToFieldsAndParameters.isSet());
                    Assert.assertFalse(methodAnalysis.variablesLinkedToMethodResult.isSet());
                } else {
                    Assert.assertTrue(methodAnalysis.variablesLinkedToFieldsAndParameters.isSet());

                    Set<Variable> set = methodAnalysis.variablesLinkedToMethodResult.get();
                    Assert.assertEquals(1, set.size());
                    // we expect it to contain the field!
                }
            }
        }
    };

    @Test
    public void test() throws IOException {
        tesUtilClass("SetOnce", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

}
