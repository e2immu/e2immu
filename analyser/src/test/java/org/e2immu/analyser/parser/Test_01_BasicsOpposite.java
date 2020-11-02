
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

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.e2immu.analyser.model.abstractvalue.VariableValue;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

public class Test_01_BasicsOpposite extends CommonTestRunner {

    public static final String STRING_PARAMETER = "org.e2immu.analyser.testexample.BasicsOpposite.setString(String):0:string";
    public static final String STRING_FIELD = "org.e2immu.analyser.testexample.BasicsOpposite.string";
    private static final String METHOD_VALUE_ADD = "org.e2immu.analyser.testexample.BasicsOpposite.add(Collection<String>):0:collection" +
            ".add(org.e2immu.analyser.testexample.BasicsOpposite.string)";

    public Test_01_BasicsOpposite() {
        super(true);
    }

    FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
        if (d.iteration() == 0) {
            Map<AnalysisStatus, Set<String>> expect = Map.of(AnalysisStatus.DONE, Set.of(
                    FieldAnalyser.EVALUATE_INITIALISER,
                    FieldAnalyser.ANALYSE_NOT_MODIFIED_1));
            assertSubMap(expect, d.statuses());
        }
        if (d.iteration() == 1) {
            Map<AnalysisStatus, Set<String>> expect = Map.of(AnalysisStatus.DONE, Set.of(
                    FieldAnalyser.EVALUATE_INITIALISER,
                    FieldAnalyser.ANALYSE_SIZE,
                    FieldAnalyser.ANALYSE_NOT_MODIFIED_1,
                    FieldAnalyser.ANALYSE_FINAL,
                    FieldAnalyser.ANALYSE_FINAL_VALUE,
                    FieldAnalyser.ANALYSE_NOT_NULL));
            assertSubMap(expect, d.statuses());
        }
        if ("string".equals(d.fieldInfo().name)) {
            int expectFinal = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
            Assert.assertEquals(expectFinal, d.fieldAnalysis().getProperty(VariableProperty.FINAL));
            int expectNotNull = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
            Assert.assertEquals(expectNotNull, d.fieldAnalysis().getProperty(VariableProperty.NOT_NULL));
        }
    };

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("collection".equals(d.variableName()) && "add".equals(d.methodInfo().name) && "0".equals(d.statementId())) {
            Assert.assertTrue("Class is " + d.currentValue().getClass(), d.currentValue() instanceof VariableValue);
            Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.NOT_NULL));
            Assert.assertEquals(Level.TRUE, d.getProperty(VariableProperty.MODIFIED));
        }
        if (STRING_FIELD.equals(d.variableName()) && "setString".equals(d.methodInfo().name)) {
            Assert.assertEquals(Level.TRUE, d.getProperty(VariableProperty.ASSIGNED));
        }
        if (STRING_FIELD.equals(d.variableName()) && "getString".equals(d.methodInfo().name)) {
            Assert.assertEquals(Level.TRUE, d.getProperty(VariableProperty.READ));
            int expectNotNull = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
            Assert.assertEquals(expectNotNull, d.getProperty(VariableProperty.NOT_NULL));
            String expertValue = d.iteration() == 0 ? UnknownValue.NO_VALUE.toString() : STRING_FIELD;
            Assert.assertEquals(expertValue, d.currentValue().toString());
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        MethodLevelData methodLevelData = d.methodAnalysis().methodLevelData();
        FieldInfo string = d.methodInfo().typeInfo.getFieldByName("string", true);
        int modified = methodLevelData.fieldSummaries.get(string).getProperty(VariableProperty.MODIFIED);
        if ("getString".equals(d.methodInfo().name)) {
            int expectNotNull = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
            Assert.assertEquals(expectNotNull, d.methodAnalysis().getProperty(VariableProperty.NOT_NULL));
            Assert.assertEquals(Level.TRUE, methodLevelData.fieldSummaries.get(string).getProperty(VariableProperty.READ));
            int expectModified = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
            Assert.assertEquals(expectModified, modified);
        }
        if ("setString".equals(d.methodInfo().name)) {
            Assert.assertEquals(Level.TRUE, methodLevelData.fieldSummaries.get(string).getProperty(VariableProperty.ASSIGNED));
            int expectModified = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
            Assert.assertEquals(expectModified, modified);
        }
    };

    EvaluationResultVisitor evaluationResultVisitor = d -> {
        if (d.methodInfo().name.equals("setString") && "0".equals(d.statementId())) {
            Assert.assertEquals(d.evaluationResult().toString(), 4L, d.evaluationResult().getModificationStream().count());
            Assert.assertTrue(d.evaluationResult().toString(), d.haveMarkRead(STRING_PARAMETER));
            Assert.assertTrue(d.evaluationResult().toString(), d.haveMarkRead(
                    "org.e2immu.analyser.testexample.BasicsOpposite.this"));
            Assert.assertTrue(d.evaluationResult().toString(), d.haveMarkAssigned(STRING_FIELD));
            // 4th is the link

            // link to empty set, because String is E2Immutable
            Assert.assertTrue(d.evaluationResult().toString(), d.haveLinkVariable(STRING_FIELD, Set.of()));
            Assert.assertEquals(d.evaluationResult().toString(), STRING_PARAMETER, d.evaluationResult().value.toString());
        }
        if (d.methodInfo().name.equals("getString") && "0".equals(d.statementId()) && d.iteration() == 0) {
            Assert.assertEquals(d.evaluationResult().toString(), 1L, d.evaluationResult().getModificationStream().count());
            Assert.assertTrue(d.evaluationResult().toString(), d.haveMarkRead(STRING_FIELD));
        }
        if (d.methodInfo().name.equals("add") && "0".equals(d.statementId())) {
            String expectEvalString = d.iteration() == 0 ? UnknownValue.NO_VALUE.toString() : METHOD_VALUE_ADD;
            Assert.assertEquals(d.evaluationResult().toString(), expectEvalString, d.evaluationResult().value.toString());
        }
    };

    TypeContextVisitor typeContextVisitor = typeContext -> {
        // check that the XML annotations have been read properly, and copied into the correct place
        TypeInfo stringType = typeContext.getPrimitives().stringTypeInfo;
        Assert.assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE, stringType.typeAnalysis.get().getProperty(VariableProperty.IMMUTABLE));
        Assert.assertTrue(stringType.hasSize(typeContext.getPrimitives(), AnalysisProvider.DEFAULT_PROVIDER));
    };

    @Test
    public void test() throws IOException {
        testClass("BasicsOpposite", 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addTypeContextVisitor(typeContextVisitor)
                .build());
    }

}