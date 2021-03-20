/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.parserfailing;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.TypeMapVisitor;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.TypeInfo;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class Test_39_MethodReferences extends CommonTestRunner {
    public Test_39_MethodReferences() {
        super(true);
    }



    @Test
    public void test_0() throws IOException {
        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo collection = typeMap.get(Collection.class);
            assertNotNull(collection);
            MethodInfo stream = collection.findUniqueMethod("stream" ,0);

            assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL, stream.methodAnalysis.get().getProperty(VariableProperty.NOT_NULL_EXPRESSION));
        };

        testClass("MethodReferences_0", 0, 0, new DebugConfiguration.Builder()
                .addTypeMapVisitor(typeMapVisitor)
                .build());
    }

    @Test
    public void test_1() throws IOException {
        testClass("MethodReferences_1", 0, 2, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_2() throws IOException {
        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo map = typeMap.get(Map.class);
            MethodInfo put = map.findUniqueMethod("put", 2);
            assertEquals(Level.TRUE, put.methodAnalysis.get().getProperty(VariableProperty.MODIFIED_METHOD));
            MethodInfo forEach = map.findUniqueMethod("forEach", 1);
            assertEquals(Level.FALSE, forEach.methodAnalysis.get().getProperty(VariableProperty.MODIFIED_METHOD));
        };

        testClass("MethodReferences_2", 0, 2, new DebugConfiguration.Builder()
                .addTypeMapVisitor(typeMapVisitor)
                .build());
    }


    @Test
    public void test_3() throws IOException {
        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo map = typeMap.get(Map.class);
            MethodInfo put = map.findUniqueMethod("get", 1);
            assertEquals(Level.FALSE, put.methodAnalysis.get().getProperty(VariableProperty.MODIFIED_METHOD));
            MethodInfo forEach = map.findUniqueMethod("forEach", 1);
            assertEquals(Level.FALSE, forEach.methodAnalysis.get().getProperty(VariableProperty.MODIFIED_METHOD));
        };

        testClass("MethodReferences_3", 0, 0, new DebugConfiguration.Builder()
                .addTypeMapVisitor(typeMapVisitor)
                .build());
    }


    @Test
    public void test_4() throws IOException {
        testClass("MethodReferences_4", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

}
