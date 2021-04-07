
/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Test_00_Basics_2 extends CommonTestRunner {

    private static final String TYPE = "org.e2immu.analyser.testexample.Basics_2";
    private static final String STRING_PARAMETER = TYPE + ".setString(java.lang.String):0:string";
    private static final String STRING_FIELD = TYPE + ".string";
    private static final String STRING_0 = TYPE + ".string$0";

    private static final String THIS = TYPE + ".this";
    private static final String COLLECTION = TYPE + ".add(java.util.Collection<java.lang.String>):0:collection";
    private static final String METHOD_VALUE_ADD = "collection.add(" + STRING_0 + ")";
    private static final String ADD = TYPE + ".add(java.util.Collection<java.lang.String>)";

    public Test_00_Basics_2() {
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
                    FieldAnalyser.ANALYSE_NOT_MODIFIED_1,
                    FieldAnalyser.ANALYSE_FINAL,
                    FieldAnalyser.ANALYSE_FINAL_VALUE,
                    FieldAnalyser.ANALYSE_NOT_NULL));
            assertSubMap(expect, d.statuses());
        }
        if ("string".equals(d.fieldInfo().name)) {
            assertEquals(Level.FALSE, d.fieldAnalysis().getProperty(VariableProperty.FINAL));
            assertEquals(MultiLevel.NULLABLE, d.fieldAnalysis().getProperty(VariableProperty.EXTERNAL_NOT_NULL));
        }
    };

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("add".equals(d.methodInfo().name)) {
            if (d.variable() instanceof ParameterInfo c && "collection".equals(c.simpleName()) && "0".equals(d.statementId())) {
                assertEquals(COLLECTION, d.variableName());

                if (d.iteration() == 0) {
                    assertEquals("0" + VariableInfoContainer.Level.EVALUATION, d.variableInfo().getReadId());
                    assertTrue(d.variableInfoContainer().hasEvaluation());
                    assertEquals("<p:collection>", d.currentValue().toString());
                    assertTrue(d.currentValueIsDelayed());
                } else {
                    assertEquals("instance type Collection<String>/*this.contains(" + STRING_0 + ")*/",
                            d.currentValue().toString());
                }
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                int expectCm = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                assertEquals(expectCm, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
            }
            if (STRING_FIELD.equals(d.variableName())) {
                String expectValue = d.iteration() == 0 ? "<f:string>" : "nullable instance type String";
                assertEquals(expectValue, d.currentValue().toString());
                // string occurs in a not-null context, even if its value is delayed
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                int expectCm = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                assertEquals(expectCm, d.getProperty(VariableProperty.CONTEXT_MODIFIED));

                int expectEnn = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
                assertEquals(expectEnn, d.getProperty(VariableProperty.EXTERNAL_NOT_NULL));
            }
            if (STRING_0.equals(d.variableName())) {
                assertTrue(d.iteration() > 0);

                assertEquals("nullable instance type String", d.currentValue().toString());
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                assertEquals(Level.FALSE, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.EXTERNAL_NOT_NULL));
            }
        }
        if ("setString".equals(d.methodInfo().name)) {
            if (STRING_FIELD.equals(d.variableName())) {
                assertTrue(d.variableInfo().isAssigned());
            }
        }
        if ("getString".equals(d.methodInfo().name)) {
            if (THIS.equals(d.variableName())) {
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
            }
            if (STRING_FIELD.equals(d.variableName())) {
                assertTrue(d.variableInfo().isRead());
                String expectValue = d.iteration() == 0 ? "<f:string>" : "nullable instance type String";
                assertEquals(expectValue, d.currentValue().toString());
                assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                int expectNNE = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
                assertEquals(expectNNE, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
            }
            if (STRING_0.equals(d.variableName())) {
                assertTrue(d.iteration() > 0);
                assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
            }
            if (d.variable() instanceof ReturnVariable) {
                String expectValue = d.iteration() == 0 ? "<f:string>" : STRING_0;
                assertEquals(expectValue, d.currentValue().toString());
                assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                int expectNotNull = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
                assertEquals(expectNotNull, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
            }
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        if (TYPE.equals(d.methodInfo().typeInfo.fullyQualifiedName)) {
            FieldInfo string = d.methodInfo().typeInfo.getFieldByName("string", true);
            VariableInfo fieldAsVariable = d.getFieldAsVariable(string);

            if ("getString".equals(d.methodInfo().name)) {
                int expectNotNull = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
                assertEquals(expectNotNull, d.methodAnalysis().getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                assert fieldAsVariable != null;
                assertTrue(fieldAsVariable.isRead());
                assertEquals(Level.FALSE, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));

                // property of the field as variable info in the method
                assertEquals(Level.FALSE, fieldAsVariable.getProperty(VariableProperty.CONTEXT_MODIFIED));
            }
            if ("setString".equals(d.methodInfo().name)) {
                assert fieldAsVariable != null;
                assertTrue(fieldAsVariable.isAssigned());
                int expectFieldModified = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                //  assertEquals(expectFieldModified, fieldAsVariable.getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD));
                assertEquals(Level.FALSE, fieldAsVariable.getProperty(VariableProperty.CONTEXT_MODIFIED));
            }
            if ("add".equals(d.methodInfo().name)) {
                ParameterAnalysis parameterAnalysis = d.parameterAnalyses().get(0);
                int expectCnn = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                assertEquals(expectCnn, parameterAnalysis.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                int expectMethodModified = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                assertEquals(expectMethodModified, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
            }
        }
    };

    EvaluationResultVisitor evaluationResultVisitor = d -> {
        if (d.methodInfo().name.equals("setString") && "0".equals(d.statementId())) {
            assertTrue(d.haveMarkRead(STRING_PARAMETER), d.evaluationResult().toString());
            assertTrue(d.haveMarkRead(THIS));

            EvaluationResult.ChangeData expressionChange = d.findValueChange(STRING_FIELD);
            assertEquals("string", expressionChange.value().debugOutput());

            // link to empty set, because String is E2Immutable
            assertTrue(d.haveLinkVariable(STRING_FIELD, Set.of()));
            assertEquals("string", d.evaluationResult().value().debugOutput());
        }
        if (d.methodInfo().name.equals("getString") && "0".equals(d.statementId()) && d.iteration() == 0) {
            assertTrue(d.haveMarkRead(STRING_FIELD));
            assertTrue(d.haveMarkRead(THIS));
        }
        if (d.methodInfo().name.equals("add") && "0".equals(d.statementId())) {
            String expectEvalString = d.iteration() == 0 ? "<m:add>" : METHOD_VALUE_ADD;
            assertEquals(expectEvalString, d.evaluationResult().value().toString());
        }
    };

    TypeMapVisitor typeMapVisitor = typeMap -> {
        // check that the XML annotations have been read properly, and copied into the correct place
        TypeInfo stringType = typeMap.getPrimitives().stringTypeInfo;
        assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE, stringType.typeAnalysis.get().getProperty(VariableProperty.IMMUTABLE));

        TypeInfo collection = typeMap.get(Collection.class);
        MethodInfo add = collection.findUniqueMethod("add", 1);
        ParameterInfo p0 = add.methodInspection.get().getParameters().get(0);
        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL,
                p0.parameterAnalysis.get().getProperty(VariableProperty.NOT_NULL_PARAMETER));
        assertEquals(Level.FALSE, p0.parameterAnalysis.get().getProperty(VariableProperty.MODIFIED_VARIABLE));
        assertEquals(Level.TRUE, add.methodAnalysis.get().getProperty(VariableProperty.MODIFIED_METHOD));
    };

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if (ADD.equals(d.methodInfo().fullyQualifiedName)) {
            assertTrue(d.state().isBoolValueTrue());
        }
    };

    @Test
    public void test() throws IOException {
        testClass("Basics_2", 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .build());
    }

}
