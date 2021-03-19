
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
import org.e2immu.analyser.visitor.FieldAnalyserVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.TypeAnalyserVisitor;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class Test_Util_00_Pair extends CommonTestRunner {

    @Test
    public void test() throws IOException {

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("v".equals(d.fieldInfo().name)) {
                if (d.iteration() == 0) assertNull(d.fieldAnalysis().isOfImplicitlyImmutableDataType());
                else assertTrue(d.fieldAnalysis().isOfImplicitlyImmutableDataType());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("getV".equals(d.methodInfo().name)) {
                int expectIndependent = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVE;
                assertEquals(expectIndependent, d.methodAnalysis().getProperty(VariableProperty.INDEPENDENT));
            }
        };

        // fields k and v do not link to the constructor's parameters because they are implicitly immutable
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            int expectIndependent = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVE;
            assertEquals(expectIndependent, d.typeAnalysis().getProperty(VariableProperty.INDEPENDENT));
        };

        testUtilClass(List.of("Pair"), 0, 0, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .build());
    }

}
