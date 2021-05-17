
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
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.*;
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
                assertFalse(d.evaluationResult().someValueWasDelayed());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("valueOf".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo name && "name".equals(name.name)) {
                    assertEquals(MultiLevel.NOT_INVOLVED, d.getProperty(VariableProperty.EXTERNAL_IMMUTABLE));
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.EXTERNAL_NOT_NULL));
                }
            }
            if ("test".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo name && "name".equals(name.name)) {
                    int expectExtImm = d.iteration() == 0 ? Level.DELAY : MultiLevel.NOT_INVOLVED;
                    assertEquals(expectExtImm, d.getProperty(VariableProperty.EXTERNAL_IMMUTABLE));
                    int expectEnn = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                    assertEquals(expectEnn, d.getProperty(VariableProperty.EXTERNAL_NOT_NULL));
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("test".equals(d.methodInfo().name)) {
                assertEquals(d.iteration() > 0, d.statementAnalysis().methodLevelData.linksHaveBeenEstablished.isSet());
            }
            if ("values".equals(d.methodInfo().name)) {
                assertEquals(d.iteration() > 0, d.statementAnalysis().methodLevelData.linksHaveBeenEstablished.isSet());
            }
            if ("valueOf".equals(d.methodInfo().name)) {
                assertEquals(d.iteration() > 0, d.statementAnalysis().methodLevelData.linksHaveBeenEstablished.isSet());
            }
            if ("isThree".equals(d.methodInfo().name)) {
                assertEquals(d.iteration() > 0, d.statementAnalysis().methodLevelData.linksHaveBeenEstablished.isSet());
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("ONE".equals(d.fieldInfo().name)) {
                assertEquals("new Enum_0()", d.fieldAnalysis().getEffectivelyFinalValue().toString());

                int expectImm = d.iteration() <= 1 ? Level.DELAY : MultiLevel.EFFECTIVELY_E2IMMUTABLE;
                assertEquals(expectImm, d.fieldAnalysis().getProperty(VariableProperty.EXTERNAL_IMMUTABLE));
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            // predicate depends on name and values, but comes earlier (we look at class only, not primary type)
            // valueOf depends on predicate
            if ("name".equals(d.methodInfo().name)) {
                fail("Shallow method analyser! name() has no code");
            }

            if ("test".equals(d.methodInfo().name)) {
                // predicate implements "test"
                assertFalse(d.methodInfo().methodResolution.get().overrides().isEmpty());
                assertEquals(Level.FALSE, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
                if (d.iteration() == 0) {
                    assertNull(d.methodAnalysis().getSingleReturnValue());
                } else {
                    assertEquals("(instance type String).equals(name)", d.methodAnalysis().getSingleReturnValue().toString());
                }
            }
            if ("valueOf".equals(d.methodInfo().name)) {
                assertEquals(Level.FALSE, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
                if (d.iteration() == 0) {
                    assertNull(d.methodAnalysis().getSingleReturnValue());
                } else {
                    assertEquals("Arrays.stream({ONE,TWO,THREE}).filter((instance type String).equals(name)).findFirst().orElseThrow()", d.methodAnalysis().getSingleReturnValue().toString());
                }
                int expectImm = d.iteration() <= 1 ? Level.DELAY : MultiLevel.EFFECTIVELY_E2IMMUTABLE;
                assertEquals(expectImm, d.methodAnalysis().getProperty(VariableProperty.IMMUTABLE));
            }
            if ("values".equals(d.methodInfo().name)) {
                if (d.iteration() == 0) {
                    assertNull(d.methodAnalysis().getSingleReturnValue());
                } else {
                    assertEquals("{ONE,TWO,THREE}", d.methodAnalysis().getSingleReturnValue().toString());
                }
                int expectMom = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                assertEquals(expectMom, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
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
                int expectImm = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_E2IMMUTABLE;
                assertEquals(expectImm, d.typeAnalysis().getProperty(VariableProperty.IMMUTABLE));
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
        assertEquals(Level.FALSE, nameAnalysis.getProperty(VariableProperty.MODIFIED_METHOD));
        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, nameAnalysis.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
    }

    @Test
    public void test4() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("highest".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertEquals(d.iteration() == 0, d.evaluationResult().someValueWasDelayed());
                    String expectValue = d.iteration() == 0 ? "1==<m:getCnt>" : "true";
                    assertEquals(expectValue, d.evaluationResult().value().toString());
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("getCnt".equals(d.methodInfo().name)) {
                assertEquals(Level.FALSE, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
                if (d.iteration() == 0) {
                    assertNull(d.methodAnalysis().getSingleReturnValue());
                } else {
                    assertEquals("cnt", d.methodAnalysis().getSingleReturnValue().toString());
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("ONE".equals(d.fieldInfo().name)) {
                assertEquals("new Enum_4(1)", d.fieldAnalysis().getEffectivelyFinalValue().toString());

                int expectImm = d.iteration() <= 1 ? Level.DELAY : MultiLevel.EFFECTIVELY_E2IMMUTABLE;
                assertEquals(expectImm, d.fieldAnalysis().getProperty(VariableProperty.EXTERNAL_IMMUTABLE));
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
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("returnTwo".equals(d.methodInfo().name)) {
                int expectImm = d.iteration() <= 1 ? Level.DELAY : MultiLevel.EFFECTIVELY_E2IMMUTABLE;
                assertEquals(expectImm, d.methodAnalysis().getProperty(VariableProperty.IMMUTABLE));
            }
        };
        testClass("Enum_5", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test6() throws IOException {
        testClass("Enum_6", 0, 0, new DebugConfiguration.Builder()
                .build());
    }
}
