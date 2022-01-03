
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

package org.e2immu.analyser.parser.failing;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.testexample.Enum_0;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class Test_26_Enum_withAPI extends CommonTestRunner {

    public Test_26_Enum_withAPI() {
        super(true);
    }

    /*
    static fields ONE, TWO, THREE
    three static methods:
    String name();
    EnumType[] values() { return new EnumType[] { ONE, TWO, THREE }; };
    EnumType valueOf(String) { return Arrays.stream(values()).filter(v -> v.name().equals(name)).findFirst().orElseThrow() };
    predicate = the lambda in valueOf
     */

    @Test
    public void test0() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("valueOf".equals(d.methodInfo().name) && "0".equals(d.statementId()) && d.iteration() > 0) {
                assertFalse(d.evaluationResult().causesOfDelay().isDelayed());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("valueOf".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo name && "name".equals(name.name)) {
                    assertEquals(MultiLevel.NOT_INVOLVED_DV, d.getProperty(Property.EXTERNAL_IMMUTABLE));
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(Property.EXTERNAL_NOT_NULL));
                }
            }
            if ("test".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo name && "name".equals(name.name)) {
                    assertEquals(MultiLevel.NOT_INVOLVED_DV, d.getProperty(Property.EXTERNAL_IMMUTABLE));
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(Property.EXTERNAL_NOT_NULL));
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("test".equals(d.methodInfo().name)) {
                assertTrue(d.statementAnalysis().methodLevelData().linksHaveBeenEstablished());
            }
            if ("values".equals(d.methodInfo().name)) {
                assertTrue(d.statementAnalysis().methodLevelData().linksHaveBeenEstablished());
            }
            if ("valueOf".equals(d.methodInfo().name)) {
                assertTrue(d.statementAnalysis().methodLevelData().linksHaveBeenEstablished());
            }
            if ("isThree".equals(d.methodInfo().name)) {
                assertTrue(d.statementAnalysis().methodLevelData().linksHaveBeenEstablished());
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("ONE".equals(d.fieldInfo().name)) {
                assertEquals("new Enum_0()", d.fieldAnalysis().getValue().toString());
                assertDv(d, 1, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.EXTERNAL_IMMUTABLE);
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            // predicate depends on name and values, but comes earlier (we look at class only, not primary type)
            // valueOf depends on predicate
            if ("name".equals(d.methodInfo().name)) {
                // shallow method analyser, does not have an evaluation context; no code in name()
                assertNull(d.evaluationContext());
            }

            if ("test".equals(d.methodInfo().name)) {
                // predicate implements "test"
                assertFalse(d.methodInfo().methodResolution.get().overrides().isEmpty());
                assertEquals(DV.FALSE_DV, d.methodAnalysis().getProperty(Property.MODIFIED_METHOD));

                assertEquals("(instance type String).equals(name)", d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("valueOf".equals(d.methodInfo().name)) {
                assertEquals(DV.FALSE_DV, d.methodAnalysis().getProperty(Property.MODIFIED_METHOD));
                if (d.iteration() == 0) {
                    assertNull(d.methodAnalysis().getSingleReturnValue());
                } else {
                    assertEquals("Arrays.stream({ONE,TWO,THREE}).filter((instance type String).equals(name)).findFirst().orElseThrow()", d.methodAnalysis().getSingleReturnValue().toString());
                }
                assertDv(d, 1, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.EXTERNAL_IMMUTABLE);
            }
            if ("values".equals(d.methodInfo().name)) {
                if (d.iteration() == 0) {
                    assertNull(d.methodAnalysis().getSingleReturnValue());
                } else {
                    assertEquals("{ONE,TWO,THREE}", d.methodAnalysis().getSingleReturnValue().toString());
                }
                assertEquals(DV.FALSE_DV, d.methodAnalysis().getProperty(Property.MODIFIED_METHOD));
            }
            if ("isThree".equals(d.methodInfo().name)) {
                if (d.iteration() == 0) {
                    assertNull(d.methodAnalysis().getSingleReturnValue());
                } else {
                    assertEquals("THREE==this", d.methodAnalysis().getSingleReturnValue().toString());
                }
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Enum_0".equals(d.typeInfo().simpleName)) {
                assertDv(d, 0, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
            }
        };

        TypeContext typeContext = testClass("Enum_0", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
        TypeInfo enum0 = typeContext.getFullyQualified(Enum_0.class);
        MethodInfo name = enum0.findUniqueMethod("name", 0);
        MethodAnalysis nameAnalysis = name.methodAnalysis.get();
        assertEquals(DV.FALSE_DV, nameAnalysis.getProperty(Property.MODIFIED_METHOD));
        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, nameAnalysis.getProperty(Property.NOT_NULL_EXPRESSION));
    }

    @Test
    public void test1() throws IOException {
        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo math = typeMap.get(Math.class);
            MethodInfo max = math.findUniqueMethod("max", 2);
            assertEquals(DV.FALSE_DV, max.methodAnalysis.get().getProperty(Property.MODIFIED_METHOD));
        };

        testClass("Enum_1", 0, 0, new DebugConfiguration.Builder()
                .addTypeMapVisitor(typeMapVisitor)
                .build());
    }

    @Test
    public void test4() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("highest".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertEquals(d.iteration() == 0, d.evaluationResult().causesOfDelay().isDelayed());
                    String expectValue = d.iteration() == 0 ? "1==<m:getCnt>" : "true";
                    assertEquals(expectValue, d.evaluationResult().value().toString());
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("getCnt".equals(d.methodInfo().name)) {
                assertEquals(DV.FALSE_DV, d.methodAnalysis().getProperty(Property.MODIFIED_METHOD));
                if (d.iteration() == 0) {
                    assertNull(d.methodAnalysis().getSingleReturnValue());
                } else {
                    assertEquals("cnt", d.methodAnalysis().getSingleReturnValue().toString());
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("ONE".equals(d.fieldInfo().name)) {
                assertEquals("new Enum_4(1)", d.fieldAnalysis().getValue().toString());
                assertDv(d, 2, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.EXTERNAL_IMMUTABLE);
            }
        };

        testClass("Enum_4", 0, 2, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build());
    }

    @Test
    public void test5() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("returnTwo".equals(d.methodInfo().name)) {
                assertFalse(d.variableName().contains("name"));
                if (d.variable() instanceof FieldReference fr && "TWO".equals(fr.fieldInfo.name)) {
                    assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("returnTwo".equals(d.methodInfo().name)) {
                assertDv(d, 2, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
            }
        };

        testClass("Enum_5", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test6() throws IOException {
        testClass("Enum_6", 0, 0, new DebugConfiguration.Builder()
                .build());
    }
}