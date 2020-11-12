
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
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;
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
            if ("s1".equals(d.variableName())) {
                Assert.assertEquals("[" + P0 + "]", d.variableInfo().getLinkedVariables().toString());
            }
        }
        if (SIZE_COPY.equals(d.methodInfo().name) && "1".equals(d.statementId())) {
            if (FIELD1.equals(d.variableName())) {
                Assert.assertEquals(Level.TRUE, d.getProperty(VariableProperty.ASSIGNED));
                Assert.assertEquals("[" + P0 + "]", d.variableInfo().getLinkedVariables().toString());
            }
        }
        if ("getStream".equals(d.methodInfo().name)) {
            if (FIELD1.equals(d.variableName())) {
                Assert.assertEquals(Level.TRUE, d.getProperty(VariableProperty.READ));
                Assert.assertEquals("[]", d.variableInfo().getLinkedVariables().toString());
            }
            if (GET_STREAM_RETURN.equals(d.variableName())) {
                Assert.assertEquals(Level.TRUE, d.getProperty(VariableProperty.ASSIGNED));
                if (d.iteration() > 0) {
                    Assert.assertEquals("[" + FIELD1 + "]", d.variableInfo().getLinkedVariables().toString());
                }
            }
        }
    };

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if (SIZE_COPY.equals(d.methodInfo().name) && "1".equals(d.statementId())) {
            Assert.assertTrue(d.statementAnalysis().methodLevelData.linksHaveBeenEstablished.isSet());
        }
        if ("getF1".equals(d.methodInfo().name) && d.iteration() > 0) {
            Assert.assertTrue(d.statementAnalysis().methodLevelData.linksHaveBeenEstablished.isSet());
        }
    };

    FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
        if ("f1".equals(d.fieldInfo().name)) {
            if (d.iteration() > 0) {
                Assert.assertEquals(Level.TRUE, d.fieldAnalysis().getProperty(VariableProperty.FINAL));
                Assert.assertEquals(FIELD1, d.fieldAnalysis().getEffectivelyFinalValue().toString());
                Assert.assertEquals(Level.FALSE, d.fieldAnalysis().getProperty(VariableProperty.MODIFIED));
                Assert.assertEquals(Level.IS_A_SIZE, d.fieldAnalysis().getProperty(VariableProperty.SIZE));
            }
            if (d.iteration() > 1) {
                Assert.assertEquals("[" + P0 + "]", d.fieldAnalysis().getVariablesLinkedToMe().toString());
            }
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        if (SIZE_COPY.equals(d.methodInfo().name)) {
            ParameterAnalysis p0 = d.parameterAnalyses().get(0);
            Assert.assertEquals(Level.FALSE, p0.getProperty(VariableProperty.MODIFIED));
        }
        if ("getF1".equals(d.methodInfo().name) && d.iteration() > 0) {
            Assert.assertEquals(Level.FALSE, d.methodAnalysis().getProperty(VariableProperty.MODIFIED));
        }
    };

    TypeContextVisitor typeContextVisitor = typeContext -> {
        TypeInfo collection = typeContext.getFullyQualified(Collection.class);
        Assert.assertTrue(collection.hasSize(AnalysisProvider.DEFAULT_PROVIDER));
        MethodInfo stream = collection.findUniqueMethod("stream", 0);
        Assert.assertEquals(Level.SIZE_COPY_TRUE, stream.methodAnalysis.get().getProperty(VariableProperty.SIZE_COPY));
        MethodInfo addAll = collection.findUniqueMethod("addAll", 1);
        ParameterInfo param0 = addAll.methodInspection.get().parameters.get(0);
        Assert.assertEquals(Level.SIZE_COPY_MIN_TRUE, param0.parameterAnalysis.get().getProperty(VariableProperty.SIZE_COPY));

        TypeInfo set = typeContext.getFullyQualified(Set.class);
        Assert.assertTrue(set.hasSize(AnalysisProvider.DEFAULT_PROVIDER));
        MethodInfo addAllSet = set.findUniqueMethod("addAll", 1);

        Set<MethodAnalysis> overrides = addAllSet.methodAnalysis.get().getOverrides();
        Assert.assertEquals(1, overrides.size());

        ParameterInfo param0Set = addAllSet.methodInspection.get().parameters.get(0);
        Assert.assertEquals(Level.SIZE_COPY_MIN_TRUE, param0Set.parameterAnalysis.get().getProperty(VariableProperty.SIZE_COPY));

        TypeInfo streamType = typeContext.getFullyQualified(Stream.class);
        Assert.assertTrue(streamType.hasSize(AnalysisProvider.DEFAULT_PROVIDER));
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
