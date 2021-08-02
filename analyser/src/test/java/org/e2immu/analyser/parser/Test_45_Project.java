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

import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Test_45_Project extends CommonTestRunner {

    public Test_45_Project() {
        super(false);
    }

    @Test
    public void test_0() throws IOException {
        final String CONTAINER = "org.e2immu.analyser.testexample.Project_0.Container";

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("recentlyReadAndUpdatedAfterwards".equals(d.methodInfo().name)) {
                if ("2.0.1.0.1".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0 -> "<m:isAfter>&&<m:isBefore>&&null!=<f:read>";
                        case 1 -> "instance type boolean&&<m:isBefore>&&null!=read$7";
                        default -> "instance type boolean&&instance type boolean&&null!=read$7";
                    };
                    assertEquals(expected, d.evaluationResult().getExpression().toString());
                    EvaluationResult.ChangeData changeData = d.findValueChangeByToString("container.read");
                    assertEquals(Level.DELAY, changeData.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                }
                if ("2.0.1.0.1.0.0".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0, 1 -> "<m:put>";
                        default -> "result.put(entry$2.getKey(),entry$2.getValue().value)";
                    };
                    assertEquals(expected, d.evaluationResult().getExpression().toString());
                }
                if ("3".equals(d.statementId())) {
                    String expected = d.iteration() < 2 ? "<m:debug>" : "<no return value>";
                    assertEquals(expected, d.evaluationResult().getExpression().toString());
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("set".equals(d.methodInfo().name)) {
                if ((CONTAINER + ".value#prev").equals(d.variable().fullyQualifiedName())) {
                    if ("2".equals(d.statementId())) {
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                        int expectNne = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                        assertEquals(expectNne, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                    }
                }
                if (d.variable() instanceof ReturnVariable && "3".equals(d.statementId())) {
                    String expectValue = d.iteration() == 0 ? "null==<m:get>?null:<f:value>" :
                            "null==kvStore.get(key)?null:kvStore.get(key).value";
                    assertEquals(expectValue, d.currentValue().toString());
                    assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                }
            }
            if (d.variable() instanceof FieldReference fr && "read".equals(fr.fieldInfo.name)) {
                int cnn = d.getProperty(VariableProperty.CONTEXT_NOT_NULL);
                assertTrue(cnn <= MultiLevel.NULLABLE, "Problem: " + d.statementId() + " in " + d.methodInfo().name);
            }
            if ("recentlyReadAndUpdatedAfterwards".equals(d.methodInfo().name)) {
                if ("result".equals(d.variableName())) {
                    if("2.0.1.0.1.0.0".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0, 1 -> "<v:result>";
                            // FIXME modification code broken
                            default -> "kvStore.entrySet().isEmpty()?new HashMap<>():instance type java.util.Map";
                        };
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if("2.0.1.0.1".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0 -> "<m:isAfter>&&<m:isBefore>&&null!=<f:read>?<v:result>:new HashMap<>()";
                            case 1 -> "instance type boolean&&<m:isBefore>&&null!=read$7?<v:result>:new HashMap<>()";
                            default -> "kvStore.entrySet().isEmpty()?new HashMap<>():instance type java.util.Map";
                        };
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if("2.0.1".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0 -> "<m:contains>||!<m:isAfter>||!<m:isBefore>||null==<f:read>?new HashMap<>():<v:result>";
                            case 1 -> "!instance type boolean||queried.contains(entry$2.getKey())||!<m:isBefore>||null==read$7?new HashMap<>():<v:result>";
                            default -> "kvStore.entrySet().isEmpty()?new HashMap<>():instance type java.util.Map";
                        };
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("2".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0 -> "<m:entrySet>.isEmpty()||<m:contains>||!<m:isAfter>||!<m:isBefore>||null==<f:read>?new HashMap<>():<v:result>";
                            case 1 -> "kvStore.entrySet().isEmpty()?new HashMap<>():<merge:Map<String,String>>";
                            default -> "kvStore.entrySet().isEmpty()?new HashMap<>():instance type java.util.Map";
                        };
                        assertEquals(expected, d.currentValue().toString());
                        String expectedVars = switch (d.iteration()) {
                            case 0 -> "[kvStore, org.e2immu.analyser.testexample.Project_0.recentlyReadAndUpdatedAfterwards(java.util.Set<java.lang.String>,long):0:queried, container.read, container.read, container.read, result]";
                            // NOTE: read$7 is still present (copy of variable field)
                            case 1 -> "[kvStore, org.e2immu.analyser.testexample.Project_0.recentlyReadAndUpdatedAfterwards(java.util.Set<java.lang.String>,long):0:queried, read$7, read$7, result]";
                            default -> "[]";
                        };
                        assertEquals(expectedVars, d.currentValue().variables().toString());
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("recentlyReadAndUpdatedAfterwards".equals(d.methodInfo().name)) {
                if ("2.0.1.0.1.0.0".equals(d.statementId())) {
                    String expectedCondition = switch (d.iteration()) {
                        case 0 -> "<m:isAfter>&&<m:isBefore>&&null!=<f:read>";
                        case 1 -> "instance type boolean&&<m:isBefore>&&null!=read$7";
                        default -> "instance type boolean&&instance type boolean&&null!=read$7";
                    };
                    assertEquals(expectedCondition, d.condition().toString());
                    assertEquals("true", d.state().toString());
                }
            }
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo map = typeMap.get(Map.class);
            MethodInfo get = map.findUniqueMethod("get", 1);
            assertEquals(MultiLevel.NULLABLE, get.methodAnalysis.get().getProperty(VariableProperty.NOT_NULL_EXPRESSION));

            MethodInfo putInMap = map.findUniqueMethod("put", 2);
            assertEquals(Level.TRUE, putInMap.getAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));

            TypeInfo hashMap = typeMap.get(HashMap.class);
            MethodInfo put = hashMap.findUniqueMethod("put", 2);
            assertEquals(Level.TRUE, put.getAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("read".equals(d.fieldInfo().name)) {
                int expectEnn = d.iteration() <= 1 ? Level.DELAY : MultiLevel.NULLABLE;
                assertEquals(expectEnn, d.fieldAnalysis().getProperty(VariableProperty.EXTERNAL_NOT_NULL));
            }
        };

        testClass("Project_0", 1, 11, new DebugConfiguration.Builder()
                .addTypeMapVisitor(typeMapVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
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
                    String expect = d.iteration() <= 2
                            ? "{org.e2immu.analyser.testexample.Project_2.Container.Container(java.lang.String)=true}"
                            : "{org.e2immu.analyser.testexample.Project_2.Container.Container(java.lang.String)=false}";
                    assertEquals(expect, d.evaluationResult().causesOfContextModificationDelay().toString());
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("set".equals(d.methodInfo().name)) {
                int done = "1".equals(d.statementId()) ? 0 : 2;
                if ("1.0.0".equals(d.statementId()) || "1".equals(d.statementId()) || "2".equals(d.statementId())) {
                    String expect = d.iteration() <= done ? CONTAINER : "[]";
                    assertEquals(expect, d.statementAnalysis().methodLevelData
                            .getCausesOfContextModificationDelay().toString(), d.statementId());
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
                int expectMom = d.iteration() <= 2 ? Level.DELAY : Level.FALSE;
                assertEquals(expectMom, p0.getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD));
                int expectCm = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                assertEquals(expectCm, p0.getProperty(VariableProperty.CONTEXT_MODIFIED));
                int expectMv = d.iteration() <= 2 ? Level.DELAY : Level.FALSE;
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

    @Test
    public void test_3() throws IOException {
        testClass("Project_3", 1, 3, new DebugConfiguration.Builder()
                .build());
    }
}
