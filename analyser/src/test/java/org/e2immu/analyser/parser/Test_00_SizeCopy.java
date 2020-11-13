
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

import org.e2immu.analyser.analyser.AnalysisProvider;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.Instance;
import org.e2immu.analyser.model.abstractvalue.VariableValue;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public class Test_00_SizeCopy extends CommonTestRunner {

    private static final String TYPE = "org.e2immu.analyser.testexample.SizeCopy";
    private static final String P0 = TYPE + ".SizeCopy(Set<String>):0:p0";
    private static final String FIELD1 = TYPE + ".f1";
    private static final String GET_STREAM_RETURN = TYPE + ".getStream()";
    public static final String SIZE_COPY = "SizeCopy";

    public Test_00_SizeCopy() {
        super(true);
    }


    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if (SIZE_COPY.equals(d.methodInfo().name) && "0".equals(d.statementId())) {
            if (FIELD1.equals(d.variableName())) {
                // shows the property wrapper that sits around the initial value in the constructor
                Assert.assertEquals(FIELD1 + ",@Container,@NotNull", d.currentValue().toString());
            }
            if (P0.equals(d.variableName())) {
                Assert.assertEquals(Level.TRUE, d.getProperty(VariableProperty.READ));
            }
        }
    };

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {

    };

    FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
        if ("f1".equals(d.fieldInfo().name)) {
            Assert.assertEquals(FIELD1, d.fieldAnalysis().getEffectivelyFinalValue().toString());
            Assert.assertTrue(d.fieldAnalysis().getEffectivelyFinalValue() instanceof VariableValue);
            Assert.assertTrue(d.fieldAnalysis().getInitialValue() instanceof Instance);
            Assert.assertEquals(Level.TRUE, d.fieldAnalysis().getProperty(VariableProperty.FINAL));

            if (d.iteration() > 0) {
                Assert.assertEquals(Level.FALSE, d.fieldAnalysis().getProperty(VariableProperty.MODIFIED));
            }
            if (d.iteration() > 1) {
                Assert.assertEquals("[]", d.fieldAnalysis().getVariablesLinkedToMe().toString());
            }
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        if (SIZE_COPY.equals(d.methodInfo().name)) {
            ParameterAnalysis p0 = d.parameterAnalyses().get(0);
            Assert.assertEquals(Level.FALSE, p0.getProperty(VariableProperty.MODIFIED));
        }
    };

    TypeContextVisitor typeContextVisitor = typeContext -> {
        TypeInfo collection = typeContext.getFullyQualified(Collection.class);
        Assert.assertFalse(collection.doesNotNeedAnalysing());
        MethodInfo stream = collection.findUniqueMethod("stream", 0);
        MethodInfo addAll = collection.findUniqueMethod("addAll", 1);
        ParameterInfo param0 = addAll.methodInspection.get().parameters.get(0);

        TypeInfo set = typeContext.getFullyQualified(Set.class);
        MethodInfo addAllSet = set.findUniqueMethod("addAll", 1);

        Set<MethodAnalysis> overrides = addAllSet.methodAnalysis.get().getOverrides();
        Assert.assertEquals(1, overrides.size());

        ParameterInfo param0Set = addAllSet.methodInspection.get().parameters.get(0);

        TypeInfo streamType = typeContext.getFullyQualified(Stream.class);
    };

    @Test
    public void test() throws IOException {
        // two errors: two unused parameters
        testClass(SIZE_COPY, 0, 0, new DebugConfiguration.Builder()
                .addTypeContextVisitor(typeContextVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

}
