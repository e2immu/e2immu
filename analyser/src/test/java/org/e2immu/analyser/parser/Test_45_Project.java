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

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Test_45_Project extends CommonTestRunner {

    public Test_45_Project() {
        super(false);
    }

    @Test
    public void test_0() throws IOException {
        final String CONTAINER = "org.e2immu.analyser.testexample.Project_0.Container";

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("set".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "value".equals(fr.fieldInfo.name)) {
                    assertEquals(CONTAINER + ".value#prev", fr.fullyQualifiedName());
                    if ("2".equals(d.statementId())) {
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                        int expectNne = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                        assertEquals(expectNne, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                    }
                }
                if (d.variable() instanceof ReturnVariable && "3".equals(d.statementId())) {
                    String expectValue = switch (d.iteration()) {
                        case 0 -> "null==<m:get>?<v:return set>:<m:equals>?<f:value>:<f:value>";
                        case 1, 2 -> "null==<m:get>?null:prev.value";
                        default -> "null==kvStore.get(key)?null:prev.value";
                    };
                    assertEquals(expectValue, d.currentValue().toString());
                    int expectNne = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
                    assertEquals(expectNne, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                }
            }
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo map = typeMap.get(Map.class);
            MethodInfo get = map.findUniqueMethod("get", 1);
            assertEquals(MultiLevel.NULLABLE, get.methodAnalysis.get().getProperty(VariableProperty.NOT_NULL_EXPRESSION));
        };

        testClass("Project_0", 1, 7, new DebugConfiguration.Builder()
                .addTypeMapVisitor(typeMapVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_1() throws IOException {
        testClass("Project_1", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    /*

     */
    @Test
    public void test_2() throws IOException {
        final String CONTAINER = "[org.e2immu.analyser.testexample.Project_2.Container.Container(java.lang.String)]";

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("set".equals(d.methodInfo().name)) {
                if ("1.0.0".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? CONTAINER: "[]";
                    assertEquals(expect, d.evaluationResult().causesOfContextModificationDelay().toString());
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("set".equals(d.methodInfo().name)) {
                if ("1.0.0".equals(d.statementId())) {
                    String expect = d.iteration() <= 1 ? CONTAINER: "[]";
                    assertEquals(expect, d.statementAnalysis().methodLevelData
                            .getCausesOfContextModificationDelay().toString());
                }
                if ("1".equals(d.statementId())) { // test the merge
                    String expect = d.iteration() == 0 ? CONTAINER: "[]";
                    assertEquals(expect, d.statementAnalysis().methodLevelData
                            .getCausesOfContextModificationDelay().toString());
                }
                if ("2".equals(d.statementId())) { // test the normal propagation
                    String expect = d.iteration() == 0 ? CONTAINER: "[]";
                    assertEquals(expect, d.statementAnalysis().methodLevelData
                            .getCausesOfContextModificationDelay().toString());
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("value".equals(d.fieldInfo().name)) {
                assertEquals(Level.FALSE, d.fieldAnalysis().getProperty(VariableProperty.EXTERNAL_PROPAGATE_MOD));
            }
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeAnalysis stringAnalysis = typeMap.getPrimitives().stringTypeInfo.typeAnalysis.get();
            assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE, stringAnalysis.getProperty(VariableProperty.IMMUTABLE));
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("Container".equals(d.methodInfo().name) && d.methodInfo().isConstructor) {
                ParameterAnalysis p0 = d.parameterAnalyses().get(0);
                int expectMom = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                assertEquals(expectMom, p0.getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD));
                int expectCm = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                assertEquals(expectCm, p0.getProperty(VariableProperty.CONTEXT_MODIFIED));
                int expectMv = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                 assertEquals(expectMv, p0.getProperty(VariableProperty.MODIFIED_VARIABLE));
            }
        };

        testClass("Project_2", 0, 0, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

}
