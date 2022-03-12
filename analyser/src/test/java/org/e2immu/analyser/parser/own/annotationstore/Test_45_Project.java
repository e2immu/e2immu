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

package org.e2immu.analyser.parser.own.annotationstore;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

// IMPORTANT: without Annotated APIs! Methods are non-modifying by default

public class Test_45_Project extends CommonTestRunner {

    public Test_45_Project() {
        super(false);
    }

    @Test
    public void test_0() throws IOException {
        final String CONTAINER = "org.e2immu.analyser.parser.own.annotationstore.testexample.Project_0.Container";

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("recentlyReadAndUpdatedAfterwards".equals(d.methodInfo().name)) {
                if ("2.0.1.0.1".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0, 1, 2 -> "<m:isAfter>&&<m:isBefore>&&null!=<f:read>";
                        default -> "entry.getValue().read.plusMillis(readWithinMillis).isAfter(now$2)&&entry.getValue().read.isBefore(entry.getValue().updated)&&null!=entry.getValue().read";
                    };
                    assertEquals(expected, d.evaluationResult().getExpression().toString());
                    EvaluationResult.ChangeData changeData = d.findValueChangeByToString("container.read");
                    assertFalse(changeData.getProperty(Property.CONTEXT_NOT_NULL).isDelayed());
                }
                if ("2.0.1.0.1.0.0".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0, 1, 2 -> "<m:put>";
                        default -> "result$2.put(entry.getKey(),entry.getValue().value)";
                    };
                    assertEquals(expected, d.evaluationResult().getExpression().toString());
                }
                if ("3".equals(d.statementId())) {
                    String expected = d.iteration() <= 2 ? "<m:debug>" : "<no return value>";
                    assertEquals(expected, d.evaluationResult().getExpression().toString());
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("set".equals(d.methodInfo().name)) {
                if ((CONTAINER + ".value#prev").equals(d.variable().fullyQualifiedName())) {
                    if ("2".equals(d.statementId())) {
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(Property.CONTEXT_NOT_NULL));
                        assertDv(d, 3, MultiLevel.NULLABLE_DV, Property.NOT_NULL_EXPRESSION);
                    }
                }
                if (d.variable() instanceof ReturnVariable && "3".equals(d.statementId())) {
                    String expectValue = switch (d.iteration()) {
                        case 0 -> "null==<m:get>?null:<f:value>";
                        case 1 -> "null==<vp:Container:cm@Parameter_previousRead;cm@Parameter_value;container@Class_Container;initial@Field_read;initial@Field_updated;initial@Field_value;mom@Parameter_previousRead;mom@Parameter_value>?null:<f:value>";
                        case 2 -> "null==<vp:Container:assign_to_field@Parameter_previousRead;assign_to_field@Parameter_value;container@Class_Container;mom@Parameter_previousRead;mom@Parameter_value>?null:<f:value>";
                        default -> "null==kvStore.get(key)?null:kvStore.get(key).value";
                    };
                    assertEquals(expectValue, d.currentValue().toString());
                    assertDv(d, 3, MultiLevel.NULLABLE_DV, Property.NOT_NULL_EXPRESSION);
                }
            }
            if (d.variable() instanceof FieldReference fr && "read".equals(fr.fieldInfo.name)) {
                if ("2.0.0".equals(d.statementId())) {
                    assertDv(d, 2, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                }
            }
            if ("recentlyReadAndUpdatedAfterwards".equals(d.methodInfo().name)) {
                if ("result".equals(d.variableName())) {
                    if ("2.0.1.0.1.0.0".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0, 1, 2 -> "<vl:result>";
                            default -> "new HashMap<>()";
                        };
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("2.0.1.0.1".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0 -> "<vl:result>";
                            case 1, 2 -> "<m:isAfter>&&<m:isBefore>&&null!=<f:read>?<vl:result>:new HashMap<>()";
                            default -> "new HashMap<>()";
                        };
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("2.0.1".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0 -> "<vl:result>";
                            case 1, 2 -> "queried.contains(entry.getKey())||!<m:isAfter>||!<m:isBefore>||null==<f:read>?new HashMap<>():<vl:result>";
                            default -> "new HashMap<>()";
                        };
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("2".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0 -> "<vl:result>";
                            case 1, 2 -> "kvStore.entrySet().isEmpty()||queried.contains((nullable instance type Entry<String,Container>).getKey())||!<m:isAfter>||!<m:isBefore>||null==<f:read>?new HashMap<>():<vl:result>";
                            default -> "new HashMap<>()";
                        };
                        assertEquals(expected, d.currentValue().toString());
                        String expectedVars = switch (d.iteration()) {
                            case 0 -> "result";
                            case 1, 2 -> "<out of scope:container:2.0.1>.read,container,entry,kvStore,org.e2immu.analyser.parser.own.annotationstore.testexample.Project_0.recentlyReadAndUpdatedAfterwards(java.util.Set<java.lang.String>,long):0:queried,result";
                            default -> "";
                        };
                        assertEquals(expectedVars, d.currentValue().variables(true)
                                .stream().map(Object::toString).sorted().collect(Collectors.joining(",")));
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("recentlyReadAndUpdatedAfterwards".equals(d.methodInfo().name)) {
                if ("2.0.1.0.1.0.0".equals(d.statementId())) {
                    String expectedCondition = switch (d.iteration()) {
                        case 0, 1, 2 -> "<m:isAfter>&&<m:isBefore>&&null!=<f:read>";
                        default -> "entry.getValue().read.plusMillis(readWithinMillis).isAfter(now$2)&&entry.getValue().read.isBefore(entry.getValue().updated)&&null!=entry.getValue().read";
                    };
                    assertEquals(expectedCondition, d.condition().toString());
                    assertEquals("true", d.state().toString());
                }
            }
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo map = typeMap.get(Map.class);
            MethodInfo get = map.findUniqueMethod("get", 1);
            assertEquals(MultiLevel.NULLABLE_DV, get.methodAnalysis.get().getProperty(Property.NOT_NULL_EXPRESSION));

            MethodInfo putInMap = map.findUniqueMethod("put", 2);
            assertEquals(DV.FALSE_DV, putInMap.getAnalysis().getProperty(Property.MODIFIED_METHOD));

            TypeInfo hashMap = typeMap.get(HashMap.class);
            MethodInfo put = hashMap.findUniqueMethod("put", 2);
            assertEquals(DV.FALSE_DV, put.getAnalysis().getProperty(Property.MODIFIED_METHOD));
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("read".equals(d.fieldInfo().name)) {
                assertDv(d, MultiLevel.NULLABLE_DV, Property.EXTERNAL_NOT_NULL);
            }
        };

        testClass("Project_0", 2, 14, new DebugConfiguration.Builder()
             //   .addTypeMapVisitor(typeMapVisitor)
             //   .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
            //    .addStatementAnalyserVisitor(statementAnalyserVisitor)
             //   .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
             //   .addEvaluationResultVisitor(evaluationResultVisitor)
                .build());
    }

    /*
    tests breaking not-null delays on field Container.read, specifically, prev.read which is delayed because it is
    linked to the parameter that it is assigned to (previousRead).
     */
    @Test
    public void test_0bis() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("set".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "read".equals(fr.fieldInfo.name) && "prev".equals(fr.scope.toString())) {
                    assertDv(d, 2, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                }
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("read".equals(d.fieldInfo().name)) {
                assertDv(d, 1, MultiLevel.NULLABLE_DV, Property.EXTERNAL_NOT_NULL);
                assertEquals("previousRead:0", d.fieldAnalysis().getLinkedVariables().toString());
            }
        };
        testClass("Project_0", 2, 10, new DebugConfiguration.Builder()
                     //   .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                      //  .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder()
                        .setComputeContextPropertiesOverAllMethods(true)
                        .setComputeFieldAnalyserAcrossAllMethods(true)
                        .build());
    }

    @Test
    public void test_1() throws IOException {
        testClass("Project_1", 0, 1, new DebugConfiguration.Builder()
                .build());
    }

    /*

     */
    @Test
    public void test_2() throws IOException {

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("set".equals(d.methodInfo().name)) {
                if ("1.0.0".equals(d.statementId())) {
                    String expect = d.iteration() == 0
                            ? "cm@Parameter_value;cnn@Parameter_value;ext_not_null@Parameter_value;mom@Parameter_value"
                            : "";
                    assertEquals(expect, d.evaluationResult().causesOfDelay().toString());
                }
            }
        };


        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeAnalysis stringAnalysis = typeMap.getPrimitives().stringTypeInfo().typeAnalysis.get();
            assertEquals(MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, stringAnalysis.getProperty(Property.IMMUTABLE));
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("Container".equals(d.methodInfo().name) && d.methodInfo().isConstructor) {
                assertDv(d.p(0), 1, DV.FALSE_DV, Property.MODIFIED_OUTSIDE_METHOD);
                assertDv(d.p(0), 1, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d.p(0), 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
            }
        };

        testClass("Project_2", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build());
    }

    @Test
    public void test_3() throws IOException {
        testClass("Project_3", 1, 3, new DebugConfiguration.Builder()
                .build());
    }


    @Test
    public void test_4() throws IOException {
        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo map = typeMap.get(Map.class);
            MethodInfo get = map.findUniqueMethod("get", 1);
            ParameterInfo p0 = get.methodInspection.get().getParameters().get(0);
            ParameterAnalysis p0a = p0.parameterAnalysis.get();
            assertEquals(DV.TRUE_DV, p0a.getProperty(Property.IDENTITY)); // first property

            assertEquals(DV.FALSE_DV, p0a.getProperty(Property.MODIFIED_VARIABLE));
            assertEquals(MultiLevel.NULLABLE_DV, p0a.getProperty(Property.NOT_NULL_PARAMETER));
            assertEquals(MultiLevel.INDEPENDENT_DV, p0a.getProperty(Property.INDEPENDENT));

            assertEquals(MultiLevel.NOT_CONTAINER_DV, p0a.getProperty(Property.CONTAINER));
            assertEquals(MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, p0a.getProperty(Property.IMMUTABLE));
        };
        testClass("Project_4", 1, 0, new DebugConfiguration.Builder()
                .addTypeMapVisitor(typeMapVisitor)
                .build());
    }

}
