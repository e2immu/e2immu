
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

import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;

public class TestImmutabilityAnnotations extends CommonTestRunner {
    public TestImmutabilityAnnotations() {
        super(true);
    }

    TypeMapVisitor typeMapVisitor = typeMap -> {
        TypeInfo set = typeMap.get(Set.class);
        MethodInfo setOf2 = set.findUniqueMethod("of", 2);
        Assert.assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL, setOf2.methodAnalysis.get().getProperty(VariableProperty.NOT_NULL));

        TypeInfo list = typeMap.get(List.class);
        MethodInfo listOf2 = list.findUniqueMethod("of", 2);
        Assert.assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL, listOf2.methodAnalysis.get().getProperty(VariableProperty.NOT_NULL));
    };

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("generateBefore".equals(d.methodInfo().name) && "0".equals(d.statementId()) && "list".equals(d.variableName())) {
            Assert.assertEquals("java.util.List.of(a, b)", d.currentValue().toString());
            int notNull = d.getPropertyOfCurrentValue(VariableProperty.NOT_NULL);
            Assert.assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL, notNull);
        }
        if ("setFirst".equals(d.methodInfo().name) && "ManyTs.this.ts2".equals(d.variableName())) {
            Assert.assertEquals(Level.TRUE, d.getProperty(VariableProperty.MODIFIED));
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        if ("setFirst".equals(d.methodInfo().name)) {
            FieldInfo ts2 = d.methodInfo().typeInfo.getFieldByName("ts2", true);
            VariableInfo tv = d.getFieldAsVariable(ts2);
            if (d.iteration() > 0) {
                Assert.assertEquals(Level.TRUE, tv.getProperty(VariableProperty.MODIFIED));
            }
        }
    };

    FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
        if ("ts2".equals(d.fieldInfo().name) && d.iteration() > 1) {
            Assert.assertEquals(Level.TRUE, d.fieldAnalysis().getProperty(VariableProperty.MODIFIED));
        }
    };

    @Test
    public void test() throws IOException {
        testClass("ImmutabilityAnnotations", 0, 1, new DebugConfiguration.Builder()
                .addTypeMapVisitor(typeMapVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());

    }

}
