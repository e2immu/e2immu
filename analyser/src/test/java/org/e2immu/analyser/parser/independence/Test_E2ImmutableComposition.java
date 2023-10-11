
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

package org.e2immu.analyser.parser.independence;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.it;
import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.it0;
import static org.junit.jupiter.api.Assertions.*;

public class Test_E2ImmutableComposition extends CommonTestRunner {

    public Test_E2ImmutableComposition() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            String clazz = d.methodInfo().typeInfo.simpleName;
            if ("first".equals(d.methodInfo().name) && "EncapsulatedAssignableArrayOfHasSize".equals(clazz)) {
                String expect = d.iteration() < 4 ? "<array-access:HasSize>" : "nullable instance type HasSize/*{L one:3}*/";
                assertEquals(expect, d.evaluationResult().value().toString());
            }
            if ("first".equals(d.methodInfo().name) && "ArrayOfConstants".equals(clazz)) {
                String expected = d.iteration() == 0 ? "<dv:strings[0]>" : "\"a\"";
                assertEquals(expected, d.evaluationResult().value().toString());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            String clazz = d.methodInfo().typeInfo.simpleName;
            if ("visit".equals(d.methodInfo().name) && "One".equals(clazz)) {
                if (d.variable() instanceof FieldReference fr && "t".equals(fr.fieldInfo.name)) {
                    assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                }
            }
            if ("visit".equals(d.methodInfo().name) && "ImmutableArrayOfTransparentOnes".equals(clazz)) {
                if ("one".equals(d.variableName())) {
                    if ("0.0.0".equals(d.statementId())) {
                        String expected = d.iteration() < 4 ? "<vl:one>" : "nullable instance type One<Integer>";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 4, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
                    }
                }
            }
            if ("visit".equals(d.methodInfo().name) && "EncapsulatedExposedArrayOfHasSize".equals(clazz)) {
                if ("element".equals(d.variableName())) {
                    if ("0.0.0".equals(d.statementId())) {
                        String expected = d.iteration() < 4 ? "<vl:element>" : "nullable instance type HasSize";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 4, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
                        String linked = d.iteration() < 4
                                ? "consumer:-1,this.one:-1,this:-1"
                                : "consumer:3,this.one:3,this:3";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                }
                if (d.variable() instanceof FieldReference fr && "one".equals(fr.fieldInfo.name)) {
                    if ("0".equals(d.statementId())) {
                        String linked = d.iteration() < 4 ? "consumer:-1,this:-1" : "consumer:4,this:4";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                }
                if (d.variable() instanceof ParameterInfo pi && "consumer".equals(pi.name)) {
                    if ("0".equals(d.statementId())) {
                        String linked = d.iteration() < 4 ? "this.one:-1,this:-1" : "this.one:4,this:4";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                }
            }
            if ("visitArray".equals(d.methodInfo().name) && "ExposedArrayOfHasSize".equals(clazz)) {
                assertEquals("0", d.statementId());
                if (d.variable() instanceof ParameterInfo pi && "consumer".equals(pi.name)) {
                    assertEquals("this:4", d.variableInfo().getLinkedVariables().toString());
                }
                if (d.variable() instanceof FieldReference fr && "elements".equals(fr.fieldInfo.name)) {
                    assertTrue(fr.scopeIsThis());
                    assertLinked(d,
                            it0("consumer:-1,this:-1"),
                            it(1, "consumer:3,this:4"));
                }
            }
            if ("getElements".equals(d.methodInfo().name) && "EncapsulatedExposedArrayOfHasSize".equals(clazz)) {
                assertEquals("0", d.statementId());
                if (d.variable() instanceof ReturnVariable) {
                    String expected = d.iteration() < 4 ? "<m:first>" : "`one.t`";
                    assertEquals(expected, d.currentValue().toString());
                    assertDv(d, 4, MultiLevel.DEPENDENT_DV, Property.INDEPENDENT);
                    assertDv(d, 4, MultiLevel.EFFECTIVELY_FINAL_FIELDS_DV, Property.IMMUTABLE);
                    // the expanded variable is linked to "this.one:3", delays are provided by EvaluateMethodCall.delay
                    String linked = d.iteration() < 4 ? "this.one:-1,this:-1" : "this.one:3,this:3";
                    assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                }
            }
            if ("size".equals(d.methodInfo().name) && "EncapsulatedImmutableArrayOfHasSize".equals(clazz)) {
                if (d.variable() instanceof FieldReference fr && "one".equals(fr.fieldInfo.name)) {
                    assertTrue(fr.scopeIsThis());
                    assertEquals("Type org.e2immu.analyser.parser.independence.testexample.E2ImmutableComposition_0.ImmutableOne<org.e2immu.analyser.parser.independence.testexample.E2ImmutableComposition_0.HasSize[]>",
                            fr.parameterizedType.toString());
                }
                if ("first".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("Type org.e2immu.analyser.parser.independence.testexample.E2ImmutableComposition_0.HasSize[]",
                                d.variableInfo().variable().parameterizedType().toString());
                        assertDv(d, 4, MultiLevel.CONTAINER_DV, Property.CONTAINER);
                    }
                }
            }
            if ("first".equals(d.methodInfo().name) && "One".equals(d.methodInfo().typeInfo.simpleName)) {
                assertEquals("0", d.statementId());
                if (d.variable() instanceof FieldReference fr && "t".equals(fr.fieldInfo.name)) {
                    assertTrue(fr.scopeIsThis());
                    assertEquals("", d.variableInfo().getLinkedVariables().toString());
                }
                if (d.variable() instanceof ReturnVariable) {
                    assertLinked(d, it(0, "this.t:0,this:3"));
                }
            }
            if ("first".equals(d.methodInfo().name) && "OneWithOne".equals(d.methodInfo().typeInfo.simpleName)) {
                assertEquals("0", d.statementId());
                if (d.variable() instanceof FieldReference fr && "one".equals(fr.fieldInfo.name)) {
                    assertTrue(fr.scopeIsThis());
                    String linked = d.iteration() < 4 ? "this:-1" : "";
                    assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                }
                if (d.variable() instanceof ReturnVariable) {
                    String expected = d.iteration() < 4 ? "<m:first>" : "`one.t`";
                    assertEquals(expected, d.currentValue().toString());
                    String linked = d.iteration() < 4 ? "this.one:-1,this:-1" : "this.one:3,this:3";
                    assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                }
            }
            if ("first".equals(d.methodInfo().name) && "EncapsulatedExposedArrayOfHasSize".equals(d.methodInfo().typeInfo.simpleName)) {
                assertEquals("0", d.statementId());
                if (d.variable() instanceof FieldReference fr && "one".equals(fr.fieldInfo.name)) {
                    assertTrue(fr.scopeIsThis());
                    String linked = d.iteration() < 4 ? "av-480:20:-1,this:-1" : "";
                    assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                }
                if (d.variable() instanceof ReturnVariable) {
                    String expected = d.iteration() < 4 ? "<array-access:HasSize>"
                            : "nullable instance type HasSize/*{L one:3,this:3}*/";
                    assertEquals(expected, d.currentValue().toString());
                    String linked = d.iteration() < 4
                            ? "av-480:20:-1,av-480:20[0]:0,this.one:-1,this:-1"
                            : "av-480:20:3,av-480:20[0]:0,this.one:3,this:3";
                    assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                }
                if (d.variableName().startsWith("av-")) {
                    if ("av-480:20".equals(d.variableName())) {
                        String expected = d.iteration() < 4 ? "<m:first>" : "`one.t`";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 4, MultiLevel.NULLABLE_DV, Property.NOT_NULL_EXPRESSION);
                        if (d.iteration() >= 4) {
                            VariableInfo vi1 = d.variableInfoContainer().getPreviousOrInitial();
                            assertEquals(MultiLevel.NULLABLE_DV, vi1.getProperty(Property.NOT_NULL_EXPRESSION));
                        }
                        String linked = d.iteration() < 4 ? "this.one:-1,this:-1" : "this.one:3,this:3";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                        assertDv(d, 4, MultiLevel.NULLABLE_DV, Property.NOT_NULL_EXPRESSION);

                    } else if ("av-480:20[0]".equals(d.variableName())) {
                        String linked = d.iteration() < 4
                                ? "av-480:20:-1,this.one:-1,this:-1"
                                : "av-480:20:3,this.one:3,this:3";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);

                        // depends on av-480-20 nne in vi1
                        assertDv(d, 4, MultiLevel.NULLABLE_DV, Property.NOT_NULL_EXPRESSION);
                    } else {
                        fail("?: " + d.variableName());
                    }
                }
            }
            if ("first".equals(d.methodInfo().name) & "ImmutableArrayOfMarker".equals(d.methodInfo().typeInfo.simpleName)) {
                if (d.variable() instanceof ReturnVariable) {
                    String expected = d.iteration() == 0 ? "<dv:markers[0]>" : "markers[0]";
                    assertEquals(expected, d.currentValue().toString());
                    assertLinked(d,
                            it0("markers[0]:0,this.markers:-1,this:-1"),
                            it(1, "markers[0]:0,this.markers:3,this:3"));
                }
            }
            if ("ImmutableArrayOfTransparentOnes".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "generator".equals(pi.name)) {
                    if ("1".equals(d.statementId())) {
                        // One is transparent, so we're filling the 'ones' array with the equivalent of an unbound parameter type
                        assertLinked(d, it(0, 2, "this.ones:-1,this:-1"),
                                it(3, "this.ones:4,this:4"));
                    }
                }
            }
            if ("EncapsulatedExposedArrayOfHasSize".equals(d.methodInfo().name) && "EncapsulatedExposedArrayOfHasSize".equals(clazz)) {
                if ("elements".equals(d.variableName())) {
                    if ("1".equals(d.statementId())) {
                        assertEquals("generator:4", d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("2".equals(d.statementId())) {
                        String linked = switch (d.iteration()) {
                            case 0 -> "generator:-1,size:-1,this.one:-1";
                            case 1 -> "generator:-1,this.one:-1";
                            default -> "generator:4,this.one:4";
                        };
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                }
            }
            if ("set".equals(d.methodInfo().name) && "EncapsulatedAssignableArrayOfHasSize".equals(clazz)) {
                assertEquals("0", d.statementId());
                if (d.variable() instanceof FieldReference fr && "one".equals(fr.fieldInfo.name)) {
                    // link to this because of expanded variable `one.t`
                    String linked = d.iteration() < 4 ? "av-527:13:-1,this:-1" : "";
                    assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                }
                if (d.variableName().startsWith("av-")) {
                    assertTrue(d.variableName().startsWith("av-527:13"));
                    if (d.variableName().equals("av-527:13")) {
                        // represents one.first()
                        String expected = d.iteration() < 4 ? "<m:first>" : "`one.t`";
                        assertEquals(expected, d.currentValue().toString());
                        String linked = d.iteration() < 4 ? "this.one:-1,this:-1" : "this.one:3,this:3";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    } else {
                        // represents one.first()[index]
                        assertTrue(d.variableName().startsWith("av-527:13["));
                        String linked = d.iteration() < 4
                                ? "av-527:13:-1,hasSize:0,this.one:-1,this:-1"
                                : "av-527:13:3,hasSize:0,this.one:3,this:3";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                }
                if (d.variable() instanceof ParameterInfo pi && "hasSize".equals(pi.name)) {
                    String linked = d.iteration() < 4
                            ? "av-527:13:-1,av-527:13[index]:0,this.one:-1,this:-1"
                            : "av-527:13:3,av-527:13[index]:0,this.one:3,this:3";
                    assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            String clazz = d.fieldInfo().owner.simpleName;
            if ("strings".equals(d.fieldInfo().name) && "ArrayOfConstants".equals(clazz)) {
                assertEquals("{\"a\",\"b\",\"c\"}", d.fieldAnalysis().getValue().toString());
            }
            if ("t".equals(d.fieldInfo().name) && "One".equals(clazz)) {
                assertDv(d, 1, DV.FALSE_DV, Property.MODIFIED_OUTSIDE_METHOD);
            }
            if ("one".equals(d.fieldInfo().name) && "EncapsulatedExposedArrayOfHasSize".equals(clazz)) {
                String expected = d.iteration() < 3 ? "<f:one>" : "instance type ImmutableOne<HasSize[]>";
                assertEquals(expected, d.fieldAnalysis().getValue().toString());
                String linked = switch (d.iteration()) {
                    case 0 -> "av-480:20:-1,consumer:-1,elements:-1,generator:-1,size:-1,this:-1";
                    case 1, 2, 3 -> "av-480:20:-1,consumer:-1,elements:-1,generator:-1,this:-1";
                    default -> "generator:4";
                };
                assertEquals(linked, d.fieldAnalysis().getLinkedVariables().toString());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            String clazz = d.methodInfo().typeInfo.simpleName;
            if ("first".equals(d.methodInfo().name) && "ArrayOfConstants".equals(clazz)) {
                String expected = d.iteration() == 0 ? "<m:first>" : "\"a\"";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("setFirst".equals(d.methodInfo().name) && "One".equals(clazz)) {
                assertDv(d.p(0), 2, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
            }
            if ("visitArray".equals(d.methodInfo().name) && "ExposedArrayOfHasSize".equals(clazz)) {
                assertDv(d.p(0), 2, MultiLevel.DEPENDENT_DV, Property.INDEPENDENT);
            }
            if ("first".equals(d.methodInfo().name) && "EncapsulatedExposedArrayOfHasSize".equals(clazz)) {
                assertDv(d, 4, MultiLevel.INDEPENDENT_HC_DV, Property.INDEPENDENT);
            }
            if ("EncapsulatedExposedArrayOfHasSize".equals(d.methodInfo().name) && "EncapsulatedExposedArrayOfHasSize".equals(clazz)) {
                assertDv(d.p(1), 5, MultiLevel.INDEPENDENT_HC_DV, Property.INDEPENDENT);
            }
            if ("first".equals(d.methodInfo().name) & "ImmutableArrayOfMarker".equals(clazz)) {
                assertDv(d, 1, MultiLevel.INDEPENDENT_HC_DV, Property.INDEPENDENT);
            }
            if ("getElements".equals(d.methodInfo().name) && "EncapsulatedExposedArrayOfHasSize".equals(clazz)) {
                String expected = d.iteration() < 4 ? "<m:getElements>" : "/*inline getElements*/`one.t`";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                assertDv(d, 4, MultiLevel.DEPENDENT_DV, Property.INDEPENDENT);
            }
            if ("visit".equals(d.methodInfo().name) && "EncapsulatedExposedArrayOfHasSize".equals(clazz)) {
                assertDv(d.p(0), 5, MultiLevel.INDEPENDENT_HC_DV, Property.INDEPENDENT);
            }
            if ("visit".equals(d.methodInfo().name) && "ImmutableArrayOfTransparentOnes".equals(clazz)) {
                assertDv(d.p(0), 5, MultiLevel.DEPENDENT_DV, Property.INDEPENDENT);
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            String clazz = d.typeInfo().simpleName;
            if ("Marker".equals(clazz)) {
                assertTrue(d.typeAnalysis().getHiddenContentTypes().isEmpty());
            }
            if ("HasSize".equals(clazz)) {
                assertTrue(d.typeAnalysis().getHiddenContentTypes().isEmpty());
            }
            if ("NonEmptyImmutableList".equals(clazz)) {
                assertHc(d, 0, "T");
                assertDv(d, MultiLevel.CONTAINER_DV, Property.CONTAINER);
            }
            if ("NonEmptyList".equals(clazz)) {
                assertHc(d, 0, "T");
            }
            if ("One".equals(clazz)) {
                assertHc(d, 0, "T");
            }
            if ("ImmutableOne".equals(clazz)) {
                assertDv(d, 1, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
                assertHc(d, 0, "T");
            }
            if ("ConstantOne".equals(clazz)) {
                assertTrue(d.typeAnalysis().getHiddenContentTypes().isEmpty());
            }
            if ("Marker".equals(clazz)) {
                assertDv(d, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
                assertDv(d, MultiLevel.CONTAINER_DV, Property.CONTAINER);
            }
            if ("ImmutableArrayOfT".equals(clazz)) {
                assertHc(d, 0, "T");
            }
            if ("ImmutableArrayOfMarker".equals(clazz)) {
                assertHc(d, 0, "Marker");
            }
            if ("ImmutableArrayOfHasSize".equals(clazz)) {
                assertHc(d, 0, "HasSize");
            }
            if ("ImmutableArrayOfTransparentOnes".equals(clazz)) {
                assertHc(d, 1, "");
            }
            if ("EncapsulatedExposedArrayOfHasSize".equals(clazz)) {
                assertHc(d, 1, "HasSize");
            }
            if ("EncapsulatedImmutableArrayOfHasSize".equals(clazz)) {
                assertHc(d, 1, "HasSize");
            }
        };

        /*
         All the warnings are complaints about one.first() getting used without checks.
         We leave it this way (rather than adding @NotNull on the interface) because
         parsing EncapsulatedImmutableArrayOfHasSize.size(), in Arrays.stream(...), tests InlinedMethod.expandedVariable


         ERRORS: 268 ImmutableArrayOfTransparentOnes.visit:consumer:0: dependent, required independent hc

         */
        testClass("E2ImmutableComposition_0", 1, 8, new DebugConfiguration.Builder()
                //   .addEvaluationResultVisitor(evaluationResultVisitor)
                //.addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
             //   .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
             //   .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }

}
