
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
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Test_E2ImmutableComposition extends CommonTestRunner {

    public Test_E2ImmutableComposition() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            String clazz = d.methodInfo().typeInfo.simpleName;
            if ("first".equals(d.methodInfo().name) && "EncapsulatedAssignableArrayOfHasSize".equals(clazz)) {
                assertEquals("nullable instance type HasSize", d.evaluationResult().value().toString());
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
                        String expected = d.iteration() <= 3 ? "<vl:one>" : "nullable instance type One<Integer>";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 4, MultiLevel.EFFECTIVELY_E2IMMUTABLE_DV, Property.IMMUTABLE);
                    }
                }
            }
            if ("visitArray".equals(d.methodInfo().name) && "ExposedArrayOfHasSize".equals(clazz)) {
                assertEquals("0", d.statementId());
                if (d.variable() instanceof ParameterInfo pi && "consumer".equals(pi.name)) {
                    // dependent, rather than independent1: modifications are possible!
                    String linked = d.iteration() == 0 ? "this.elements:-1" : "this.elements:2";
                    assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                }
            }
            if ("getElements".equals(d.methodInfo().name) && "EncapsulatedExposedArrayOfHasSize".equals(clazz)) {
                assertEquals("0", d.statementId());
                if (d.variable() instanceof ReturnVariable) {
                    String expected = d.iteration() <= 3 ? "<m:first>" : "`one.t`";
                    assertEquals(expected, d.currentValue().toString());
                    assertDv(d, 4, MultiLevel.DEPENDENT_DV, Property.INDEPENDENT);
                    assertDv(d, 4, MultiLevel.EFFECTIVELY_E1IMMUTABLE_DV, Property.IMMUTABLE);
                    // the expanded variable is linked to "this", delays are provided by EvaluateMethodCall.delay
                    String linked = d.iteration() <= 3 ? "this.one:-1,this:-1" : "this:2";
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
                String expected = d.iteration() <= 2 ? "<f:one>" : "instance type ImmutableOne<HasSize[]>";
                assertEquals(expected, d.fieldAnalysis().getValue().toString());
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
                assertDv(d, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
            }
            if ("getElements".equals(d.methodInfo().name) && "EncapsulatedExposedArrayOfHasSize".equals(clazz)) {
                String expected = d.iteration() <= 3 ? "<m:getElements>" : "/*inline getElements*/`one.t`";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                assertDv(d, 4, MultiLevel.DEPENDENT_DV, Property.INDEPENDENT);
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("ImmutableArrayOfTransparentOnes".equals(d.typeInfo().simpleName)) {
                assertEquals("Type org.e2immu.analyser.parser.independence.testexample.E2ImmutableComposition_0.One",
                        d.typeAnalysis().getTransparentTypes().toString());
            }
        };

        /*
         All the warnings are complaints about one.first() getting used without checks.
         We leave it this way (rather than adding @NotNull on the interface) because
         parsing EncapsulatedImmutableArrayOfHasSize.size(), in Arrays.stream(...), tests InlinedMethod.expandedVariable
         */
        testClass("E2ImmutableComposition_0", 0, 8, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }

}
