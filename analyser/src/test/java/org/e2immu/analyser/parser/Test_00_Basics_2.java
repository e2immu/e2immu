
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
import org.e2immu.analyser.model.expression.PropertyWrapper;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import static org.e2immu.analyser.analyser.Property.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Test_00_Basics_2 extends CommonTestRunner {

    private static final String TYPE = "org.e2immu.analyser.testexample.Basics_2";
    private static final String STRING_PARAMETER = TYPE + ".setString(java.lang.String):0:string";
    private static final String STRING_FIELD = TYPE + ".string";
    private static final String STRING_0 = "string$0";

    private static final String THIS = TYPE + ".this";
    private static final String COLLECTION = TYPE + ".add(java.util.Collection<java.lang.String>):0:collection";
    private static final String ADD = TYPE + ".add(java.util.Collection<java.lang.String>)";

    public Test_00_Basics_2() {
        super(true);
    }

    FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
        if (d.iteration() == 0) {
            Map<AnalysisStatus, Set<String>> expect = Map.of(AnalysisStatus.DONE, Set.of(
                    FieldAnalyser.EVALUATE_INITIALISER,
                    FieldAnalyser.ANALYSE_CONTAINER));
            assertSubMap(expect, d.statuses());
        }
        if (d.iteration() == 1) {
            Map<AnalysisStatus, Set<String>> expect = Map.of(AnalysisStatus.DONE, Set.of(
                    FieldAnalyser.EVALUATE_INITIALISER,
                    FieldAnalyser.ANALYSE_CONTAINER,
                    FieldAnalyser.ANALYSE_FINAL,
                    FieldAnalyser.ANALYSE_FINAL_VALUE,
                    FieldAnalyser.ANALYSE_NOT_NULL));
            assertSubMap(expect, d.statuses());
        }
        if ("string".equals(d.fieldInfo().name)) {
            assertEquals(Level.FALSE_DV, d.fieldAnalysis().getProperty(FINAL));
            assertEquals(MultiLevel.NULLABLE_DV, d.fieldAnalysis().getProperty(EXTERNAL_NOT_NULL));

            assertEquals("string:0", d.fieldAnalysis().getLinkedVariables().toString());
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
                    assertTrue(d.currentValue().isDelayed());
                    assertEquals("nullable instance type Collection<String>/*@Identity*/",
                            d.variableInfoContainer().getPreviousOrInitial().getValue().toString());
                } else {
                    assertTrue(d.currentValue() instanceof PropertyWrapper);
                    assertEquals("nullable instance type Collection<String>/*@Identity*//*this.contains(string$0)*/",
                            d.currentValue().toString());
                }
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(CONTEXT_NOT_NULL));
                assertEquals(Level.TRUE_DV, d.getProperty(CONTEXT_MODIFIED));

                // cannot be content linked to string, because string is recursively immutable
                assertEquals("collection:0", d.variableInfo().getLinkedVariables().toString());
            }
            if (STRING_FIELD.equals(d.variableName())) {
                String expectValue = d.iteration() == 0 ? "<f:string>" : "nullable instance type String";
                assertEquals(expectValue, d.currentValue().toString());
                // string occurs in a not-null context, even if its value is delayed
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(CONTEXT_NOT_NULL));
                assertEquals(Level.FALSE_DV, d.getProperty(CONTEXT_MODIFIED));

                assertDv(d, 0, MultiLevel.NULLABLE_DV, EXTERNAL_NOT_NULL);
            }
            if (STRING_0.equals(d.variableName())) {
                assertTrue(d.iteration() > 0);

                assertEquals("nullable instance type String", d.currentValue().toString());
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(CONTEXT_NOT_NULL));
                assertEquals(Level.FALSE_DV, d.getProperty(CONTEXT_MODIFIED));
                assertEquals(MultiLevel.NULLABLE_DV, d.getProperty(EXTERNAL_NOT_NULL));
            }
        }
        if ("setString".equals(d.methodInfo().name)) {
            if (STRING_FIELD.equals(d.variableName())) {
                assertTrue(d.variableInfo().isAssigned());
                assertEquals("string:0,this.string:0", d.variableInfo().getLinkedVariables().toString());
            }
        }
        if ("getString".equals(d.methodInfo().name)) {
            if (THIS.equals(d.variableName())) {
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(NOT_NULL_EXPRESSION));
            }
            if (STRING_FIELD.equals(d.variableName())) {
                assertTrue(d.variableInfo().isRead());
                String expectValue = d.iteration() == 0 ? "<f:string>" : "nullable instance type String";
                assertEquals(expectValue, d.currentValue().toString());
                assertEquals(MultiLevel.NULLABLE_DV, d.getProperty(CONTEXT_NOT_NULL));
                assertDv(d, 0, MultiLevel.NULLABLE_DV, NOT_NULL_EXPRESSION);
            }
            if (STRING_0.equals(d.variableName())) {
                assertTrue(d.iteration() > 0);
                assertEquals(MultiLevel.NULLABLE_DV, d.getProperty(CONTEXT_NOT_NULL));
                assertEquals(MultiLevel.NULLABLE_DV, d.getProperty(NOT_NULL_EXPRESSION));
            }
            if (d.variable() instanceof ReturnVariable) {
                String expectValue = d.iteration() == 0 ? "<f:string>" : STRING_0;
                assertEquals(expectValue, d.currentValue().toString());
                assertEquals(MultiLevel.NULLABLE_DV, d.getProperty(CONTEXT_NOT_NULL));
                assertDv(d, 0, MultiLevel.NULLABLE_DV, NOT_NULL_EXPRESSION);
            }
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        if (TYPE.equals(d.methodInfo().typeInfo.fullyQualifiedName)) {
            FieldInfo string = d.methodInfo().typeInfo.getFieldByName("string", true);
            VariableInfo fieldAsVariable = d.getFieldAsVariable(string);

            if ("getString".equals(d.methodInfo().name)) {
                assertDv(d, 0, MultiLevel.NULLABLE_DV, NOT_NULL_EXPRESSION);

                assert fieldAsVariable != null;
                assertTrue(fieldAsVariable.isRead());
                assertEquals(Level.FALSE_DV, d.methodAnalysis().getProperty(MODIFIED_METHOD));

                // property of the field as variable info in the method
                assertEquals(Level.FALSE_DV, fieldAsVariable.getProperty(CONTEXT_MODIFIED));
            }
            if ("setString".equals(d.methodInfo().name)) {
                assert fieldAsVariable != null;
                assertTrue(fieldAsVariable.isAssigned());
                assertEquals(Level.FALSE_DV, fieldAsVariable.getProperty(CONTEXT_MODIFIED));
            }
            if ("add".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 0, MultiLevel.EFFECTIVELY_NOT_NULL_DV, CONTEXT_NOT_NULL);
                assertEquals(Level.FALSE_DV, d.methodAnalysis().getProperty(MODIFIED_METHOD));
            }
        }
    };

    EvaluationResultVisitor evaluationResultVisitor = d -> {
        if (d.methodInfo().name.equals("setString") && "0".equals(d.statementId())) {
            assertTrue(d.haveMarkRead(STRING_PARAMETER), d.evaluationResult().toString());
            assertTrue(d.haveMarkRead(THIS));

            EvaluationResult.ChangeData expressionChange = d.findValueChange(STRING_FIELD);
            assertEquals("string", expressionChange.value().debugOutput());

            EvaluationResult.ChangeData cd = d.findValueChange(STRING_FIELD);
            assertEquals("string:0", cd.linkedVariables().toString());
            assertEquals("string", d.evaluationResult().value().debugOutput());
        }
        if (d.methodInfo().name.equals("getString") && "0".equals(d.statementId()) && d.iteration() == 0) {
            assertTrue(d.haveMarkRead(STRING_FIELD));
            assertTrue(d.haveMarkRead(THIS));
        }
        if (d.methodInfo().name.equals("add") && "0".equals(d.statementId())) {
            String expectEvalString = d.iteration() == 0 ? "<m:add>" : "instance type boolean";
            assertEquals(expectEvalString, d.evaluationResult().value().toString());
        }
    };

    TypeMapVisitor typeMapVisitor = typeMap -> {
        // check that the XML annotations have been read properly, and copied into the correct place
        TypeInfo stringType = typeMap.getPrimitives().stringTypeInfo;
        assertEquals(MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, stringType.typeAnalysis.get().getProperty(IMMUTABLE));

        TypeInfo collection = typeMap.get(Collection.class);
        MethodInfo add = collection.findUniqueMethod("add", 1);
        ParameterInfo p0 = add.methodInspection.get().getParameters().get(0);
        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV,
                p0.parameterAnalysis.get().getProperty(NOT_NULL_PARAMETER));
        assertEquals(Level.FALSE_DV, p0.parameterAnalysis.get().getProperty(MODIFIED_VARIABLE));
        assertEquals(Level.TRUE_DV, add.methodAnalysis.get().getProperty(MODIFIED_METHOD));
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
