
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

package org.e2immu.analyser.parser.start;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.analysis.impl.ParameterAnalysisImpl;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.VariableNature;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Set;

import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.it;
import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.it0;
import static org.junit.jupiter.api.Assertions.*;

public class Test_22_SubTypes extends CommonTestRunner {
    public Test_22_SubTypes() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        testClass("SubTypes_0", 0, 0, new DebugConfiguration.Builder().build());
    }

    @Test
    public void test_1() throws IOException {
        final String SUBTYPE_CONSTRUCTOR = "MethodWithSubType$KV$1";
        final String KV = "org.e2immu.analyser.parser.start.testexample.SubTypes_1." + SUBTYPE_CONSTRUCTOR;
        final String KEY = KV + ".key";

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("key".equals(d.fieldInfo().name) && SUBTYPE_CONSTRUCTOR.equals(d.fieldInfo().owner.simpleName)) {
                assertEquals(DV.TRUE_DV, d.fieldAnalysis().getProperty(Property.FINAL));
                assertTrue(d.fieldAnalysis().getLinkedVariables().isEmpty());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if (SUBTYPE_CONSTRUCTOR.equals(d.methodInfo().name)) {
                assertTrue(d.methodInfo().isConstructor);
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if (SUBTYPE_CONSTRUCTOR.equals(d.methodInfo().name) && KEY.equals(d.variableName())) {
                assertEquals("key", d.currentValue().toString());
                assertEquals("key:0", d.variableInfo().getLinkedVariables().toString());
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if (SUBTYPE_CONSTRUCTOR.equals(d.methodInfo().name)) {
                assertTrue(d.methodInfo().isConstructor);
                if ("1".equals(d.statementId())) {
                    String report = d.iteration() == 0
                            ? "s={context-modified=link@NOT_YET_SET, context-not-null=nullable:1, read=true:1}, this={context-modified=false:0}"
                            : "s={context-modified=false:0, context-not-null=nullable:1, read=true:1}, this={context-modified=false:0}";
                    assertEquals(report, d.variableAccessReport().toString());
                }
            }
            if ("methodWithSubType".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    String expected = d.iteration() == 0
                            ? "s={context-modified=initial:this.key@Method_toString_0-C;initial:this.value@Method_toString_0-C;link@NOT_YET_SET, context-not-null=initial:this.key@Method_toString_0-C;initial:this.value@Method_toString_0-C, read=true:1}, this={context-modified=initial:this.key@Method_toString_0-C;initial:this.value@Method_toString_0-C}"
                            : "s={context-modified=false:0, context-not-null=nullable:1, read=true:1}, this={context-modified=false:0}";
                    assertEquals(expected, d.statementAnalysis().propertiesFromSubAnalysersSortedToString());
                }
            }
        };

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if (SUBTYPE_CONSTRUCTOR.equals(d.methodInfo().name) && "0".equals(d.statementId())) {
                assertEquals("key", d.evaluationResult().value().toString());
            }
            if (SUBTYPE_CONSTRUCTOR.equals(d.methodInfo().name) && "1".equals(d.statementId())) {
                assertEquals("value+\"abc\"", d.evaluationResult().value().toString());
            }
        };

        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("---", d.delaySequence());

        testClass("SubTypes_1", 0, 0, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addBreakDelayVisitor(breakDelayVisitor)
                .build());
    }

    @Test
    public void test_2() throws IOException {

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("NonStaticSubType2".equals(d.methodInfo().typeInfo.simpleName) && "toString".equals(d.methodInfo().name)) {
                Set<MethodAnalysis> overrides = d.methodAnalysis().getOverrides(d.evaluationContext().getAnalyserContext(), true);
                assertEquals(1, overrides.size());
                MethodAnalysis objectToString = overrides.stream().findFirst().orElseThrow();
                assertEquals("Object", objectToString.getMethodInfo().typeInfo.simpleName);
                assertDv(d, MultiLevel.CONTAINER_DV, Property.CONTAINER);
            }
        };

        TypeMapVisitor typeMapVisitor = d -> {
            TypeInfo object = d.typeMap().get(Object.class);
            MethodInfo toString = object.findUniqueMethod("toString", 0);
            MethodAnalysis ma = d.getMethodAnalysis(toString);
            assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, ma.getProperty(Property.NOT_NULL_EXPRESSION));
            assertEquals(DV.FALSE_DV, ma.getProperty(Property.MODIFIED_METHOD));

            TypeInfo nonStatic2 = d.typeMap().get("org.e2immu.analyser.parser.start.testexample.SubTypes_2.NonStaticSubType2");
            MethodInfo toString2 = nonStatic2.findUniqueMethod("toString", 0);
            Set<MethodInfo> overrides = toString2.methodResolution.get().overrides();
            assertEquals(1, overrides.size());
            assertSame(toString, overrides.stream().findFirst().orElseThrow());
        };

        testClass("SubTypes_2", 2, 0, new DebugConfiguration.Builder()
                .addTypeMapVisitor(typeMapVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test_3() throws IOException {
        testClass("SubTypes_3", 0, 0, new DebugConfiguration.Builder().build());
    }

    @Test
    public void test_4() throws IOException {
        testClass("SubTypes_4", 3, 0, new DebugConfiguration.Builder().build());
    }

    // contains some standard loop stuff, plus an interesting application of VariableAccessReport

    @Test
    public void test_5() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            VariableNature variableNature = d.variableInfoContainer().variableNature();
            if ("sum".equals(d.methodInfo().name)) {
                if ("i".equals(d.variableName())) {
                    if (variableNature instanceof VariableNature.LoopVariable loop) {
                        assertEquals("1", loop.statementIndex());
                    } else fail();
                    if ("1".equals(d.statementId())) {
                        String expected = d.iteration() <= 1 ? "<vl:i>" : "instance type int";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("1.0.0".equals(d.statementId())) {
                        String expected = d.iteration() <= 1 ? "<vl:i>" : "instance type int";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
                if ("sum".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("0", d.currentValue().toString());
                        assertDv(d, DV.FALSE_DV, Property.IDENTITY);
                    }
                    if ("1.0.0".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0 -> "<vl:i>+<v:sum>";
                            case 1 -> "<vl:i>+sum$1";
                            default -> "i+sum$1";
                        };
                        assertEquals(expected, d.currentValue().toString());
                        if (variableNature instanceof VariableNature.VariableDefinedOutsideLoop outside) {
                            assertEquals("1", outside.statementIndex());
                        } else fail("Of " + variableNature.getClass());
                        assertDv(d, 2, DV.FALSE_DV, Property.IDENTITY);
                    }
                    if ("1".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0 -> "<loopIsNotEmptyCondition>?<oos:i>+<v:sum>:0";
                            case 1 -> "<loopIsNotEmptyCondition>?<oos:i>+sum$1:0";
                            default -> "instance type boolean?instance type int+sum$1:0";
                        };
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
            }
        };
        testClass("SubTypes_5", 0, 0, new DebugConfiguration.Builder()
                // .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_6() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("go".equals(d.methodInfo().name)) {
                if ("it2".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("set2:2", d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        assertEquals("set2:2", d.variableInfo().getLinkedVariables().toString());
                    }
                }
                if (d.variable() instanceof ParameterInfo p && "set2".equals(p.name)) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("it2:2", d.variableInfo().getLinkedVariables().toString());
                    }
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("apply".equals(d.methodInfo().name) && "$1".equals(d.methodInfo().typeInfo.simpleName)) {
                assertEquals(DV.FALSE_DV, d.methodAnalysis().getProperty(Property.MODIFIED_METHOD));
                ParameterAnalysis p0 = d.parameterAnalyses().get(0);
                assertEquals("set1", ((ParameterAnalysisImpl.Builder) p0).simpleName);
                assertDv(d.p(0), 1, DV.TRUE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d, 1, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("SubTypes_6".equals(d.typeInfo().simpleName)) {
                assertTrue(d.typeAnalysis().getHiddenContentTypes().isEmpty());
            }
        };

        testClass("SubTypes_6", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }

    @Test
    public void test_7() throws IOException {
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("method".equals(d.fieldInfo().name)) {
                assertDv(d, 1, MultiLevel.EFFECTIVELY_IMMUTABLE_DV, Property.EXTERNAL_IMMUTABLE);
            }
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("SubTypes_7".equals(d.typeInfo().simpleName)) {
                assertDv(d, 0, MultiLevel.EFFECTIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
            }

            if ("Example7".equals(d.typeInfo().simpleName)) {
                assertDv(d, 1, MultiLevel.EFFECTIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
            }
            // nested in Example7
            if ("$1".equals(d.typeInfo().simpleName)) {
                assertEquals("Example7", d.typeInfo().packageNameOrEnclosingType.getRight().simpleName);
                assertDv(d, 0, MultiLevel.EFFECTIVELY_IMMUTABLE_DV, Property.PARTIAL_IMMUTABLE);
                assertDv(d, 2, MultiLevel.EFFECTIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
            }

            if ("Example8".equals(d.typeInfo().simpleName)) {
                assertEquals("T", d.typeAnalysis().getHiddenContentTypes().toString());
                assertDv(d, MultiLevel.EFFECTIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
            }
            // nested in Example8
            if ("$2".equals(d.typeInfo().simpleName)) {
                assertEquals("Example8", d.typeInfo().packageNameOrEnclosingType.getRight().simpleName);
                assertDv(d, 0, MultiLevel.EFFECTIVELY_IMMUTABLE_DV, Property.PARTIAL_IMMUTABLE);
                assertDv(d, 1, MultiLevel.EFFECTIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
            }
        };

        testClass("SubTypes_7", 0, 0, new DebugConfiguration.Builder()
                // .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                //  .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }

    // delay cycle to be broken:
    // 1- anonymous type is partially immutable, but needs primary type to finalize the value
    // 2- because the anonymous type's IMMUTABLE is not known, the field "external" gets no value
    // 3- because the field gets no value, it can get no IGNORE_MODS, hence no value for MODIFIED_OUTSIDE_METHOD
    // 4- because of the delay on the field, the primary type's IMMUTABLE cannot be computed
    @Test
    public void test_10() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            // is analysed before "go", but after $1
            // iteration 2 should see a value
            if ("SubTypes_10".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "external".equals(fr.fieldInfo.name)) {
                    assertEquals("0", d.statementId());
                    String expected = d.iteration() == 0 ? "<new:External>" : "new External(){}";
                    assertEquals(expected, d.currentValue().toString());
                }
            }
            if ("go".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "external".equals(fr.fieldInfo.name)) {
                    assertEquals("0", d.statementId());
                    assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                }
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("external".equals(d.fieldInfo().name)) {
                assertDv(d, MultiLevel.NOT_IGNORE_MODS_DV, Property.EXTERNAL_IGNORE_MODIFICATIONS);
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_OUTSIDE_METHOD);

                String expected = d.iteration() == 0 ? "values:this.external@Field_external" : "";
                assertEquals(expected, d.fieldAnalysis().valuesDelayed().toString());
            }
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("External".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
                assertDv(d, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
                assertDv(d, MultiLevel.CONTAINER_DV, Property.CONTAINER);
            }
            // will only see CM in "go" in iteration 2, as it is analysed before "go" and the constructor
            if ("$1".equals(d.typeInfo().simpleName)) {
                // so we know early on that the anonymous type itself is immutable; however, we must wait for the enclosing type
                assertDv(d, MultiLevel.EFFECTIVELY_IMMUTABLE_DV, Property.PARTIAL_IMMUTABLE);
                assertDv(d, 2, MultiLevel.EFFECTIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
                assertDv(d, 1, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
            }
        };
        testClass("SubTypes_10", 0, 0, new DebugConfiguration.Builder()
                //  .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                //  .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                //  .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }

    @Test
    public void test_11() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("SubTypes_11".equals(d.methodInfo().name)) {
                if ("0.0.0".equals(d.statementId()) && "outerField".equals(d.variableName())) {
                    assertTrue(d.variable() instanceof ParameterInfo);
                    assertDv(d, 0, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                }
                if (d.variable() instanceof FieldReference fr && "outerField".equals(fr.fieldInfo.name)) {
                    assertEquals("outerField", d.currentValue().toString());
                    assertDv(d, 0, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("nonPrivateNonFinal".equals(d.fieldInfo().name)) {
                assertNotNull(d.haveError(Message.Label.NON_PRIVATE_FIELD_NOT_FINAL));
            }
            if ("unusedInnerField".equals(d.fieldInfo().name)) {
                if (d.iteration() > 0) {
                    assertNotNull(d.haveError(Message.Label.PRIVATE_FIELD_NOT_READ_IN_OWNER_TYPE));
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("doAssignmentIntoNestedType".equals(d.methodInfo().name)) {
                assertNotNull(d.haveError(Message.Label.METHOD_SHOULD_BE_MARKED_STATIC));
            }
        };


        testClass("SubTypes_11", 4, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }

    @Test
    public void test_12() throws IOException {
        // TODO: remove the null-pointer warning next to the null pointer error!
        testClass("SubTypes_12", 1, 3,
                new DebugConfiguration.Builder().build(),
                new AnalyserConfiguration.Builder().setForceAlphabeticAnalysisInPrimaryType(true).build());
    }
}
