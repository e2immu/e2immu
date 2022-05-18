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

package org.e2immu.analyser.parser.modification;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analysis.impl.FieldAnalysisImpl;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

// detailed explanation in Test_16_Modification_19; note the 2 differences compared to that test.
public class Test_16_Modification_20 extends CommonTestRunner {

    public Test_16_Modification_20() {
        super(true);
    }

    @Test
    public void test20() throws IOException {

        // infinite loop
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("C1".equals(d.methodInfo().name)) {
                assertTrue(d.methodInfo().isConstructor);
                if (d.variable() instanceof FieldReference fr && "set".equals(fr.fieldInfo.name)) {
                    assertFalse(d.context().evaluationContext().isMyself(d.variable()));

                    assertEquals("setC", d.currentValue().toString());
                    assertEquals("setC:0", d.variableInfo().getLinkedVariables().toString());
                }
            }

            if ("example1".equals(d.methodInfo().name)) {
                if (d.variable() instanceof This thisVar && "Modification_20".equals(thisVar.typeInfo.simpleName)) {
                    if ("0".equals(d.statementId())) {
                        String expectedDelay = switch (d.iteration()) {
                            case 0 -> "cm:c@Method_example1_0-E;cm:this.s2@Method_example1_0-E;cm:this@Method_example1_0-E;initial:this.s2@Method_example1_0-C";
                            case 1 -> "mom@Parameter_setC";
                            default -> "";
                        };
                        assertDv(d, expectedDelay, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if (d.variable() instanceof FieldReference fr && "s2".equals(fr.fieldInfo.name)) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                        assertDv(d, 1, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                        String expected = d.iteration() == 0 ? "<f:s2>" : "instance type HashSet<String>";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("2".equals(d.statementId())) {
                        String expected = d.iteration() <= 3 ? "<f:s2>" : "instance type HashSet<String>";
                        assertEquals(expected, d.currentValue().toString());

                        String linked = switch (d.iteration()) {
                            case 0 -> "c.set:-1,c:-1,localD.set:-1,localD:-1,return example1:-1,this:-1";
                            case 1, 2 -> "c.set:-1,c:-1,localD.set:-1,localD:-1,return example1:-1";
                            default -> "";
                        };
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());

                        assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                        assertDv(d, 4, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                    }
                }

                if (d.variable() instanceof FieldReference fr && "set".equals(fr.fieldInfo.name)) {
                    if ("c".equals(fr.scope.toString())) {
                        if ("2".equals(d.statementId())) {
                            assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                        }
                    }
                    // applies to c.set and d.set
                    if ("0".equals(d.statementId())) {
                        assertDv(d, 3, DV.FALSE_DV, Property.CONTEXT_MODIFIED);

                        String expectValue = d.iteration() <= 3 ? "<f:set>" : "nullable instance type Set<String>";
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                    if ("2".equals(d.statementId())) {
                        String expectValue = d.iteration() <= 3 ? "<f:set>" : "nullable instance type Set<String>";
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                }
                if ("c".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        String expectValue = d.iteration() <= 3 ? "<new:C1>" : "new C1(s2)";
                        mustSeeIteration(d, 4);
                        assertEquals(expectValue, d.currentValue().toString());
                        String expectLinked = switch (d.iteration()) {
                            case 0 -> "this.s2:-1,this:-1";
                            case 1, 2 -> "this.s2:-1";
                            default -> "";
                        };
                        assertEquals(expectLinked, d.variableInfo().getLinkedVariables().toString());
                    }
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("C1".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 5, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d.p(0), 1, MultiLevel.INDEPENDENT_1_DV, Property.INDEPENDENT);
                assertDv(d.p(0), 1, MultiLevel.NULLABLE_DV, Property.NOT_NULL_PARAMETER);
            }
            // addAll will not modify its parameters
            if ("addAll".equals(d.methodInfo().name)) {
                assertDv(d.p(0), "cm@Parameter_c", 1, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d.p(1), "cm@Parameter_d", 1, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d.p(0), 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_PARAMETER);
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("set".equals(d.fieldInfo().name)) {
                assertEquals("setC:0", d.fieldAnalysis().getLinkedVariables().toString());

                assertTrue(((FieldAnalysisImpl.Builder) d.fieldAnalysis()).allLinksHaveBeenEstablished().isDone());

                assertEquals(DV.TRUE_DV, d.fieldAnalysis().getProperty(Property.FINAL));
                // value from the constructor
                assertEquals("setC", d.fieldAnalysis().getValue().toString());
                assertDv(d, MultiLevel.NULLABLE_DV, Property.EXTERNAL_NOT_NULL);
                assertDv(d, 4, DV.FALSE_DV, Property.MODIFIED_OUTSIDE_METHOD);
                // note that while the type of the field is transparent in C1, we do not verify that here
                assertDv(d, 0, MultiLevel.MUTABLE_DV, Property.EXTERNAL_IMMUTABLE);
            }
            if ("s2".equals(d.fieldInfo().name)) {
                assertEquals(DV.TRUE_DV, d.fieldAnalysis().getProperty(Property.FINAL));
                assertEquals("instance type HashSet<String>", d.fieldAnalysis().getValue().toString());
                assertTrue(((FieldAnalysisImpl.Builder) d.fieldAnalysis()).allLinksHaveBeenEstablished().isDone());

                assertDv(d, 4, DV.FALSE_DV, Property.MODIFIED_OUTSIDE_METHOD);
            }
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo set = typeMap.get(Set.class);
            MethodInfo equals = set.findUniqueMethod("equals", 1);
            DV mm = equals.methodAnalysis.get().getProperty(Property.MODIFIED_METHOD);
            assertEquals(DV.FALSE_DV, mm);
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("C1".equals(d.typeInfo().simpleName)) {
                assertEquals("Type java.util.Set<java.lang.String>", d.typeAnalysis().getTransparentTypes().toString());
                assertDv(d, 4, MultiLevel.EFFECTIVELY_E2IMMUTABLE_DV, Property.IMMUTABLE);
            }
            if ("Modification_20".equals(d.typeInfo().simpleName)) {
                assertEquals("", d.typeAnalysis().getTransparentTypes().toString());
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            assertEquals(d.iteration() == 4, d.context().evaluationContext().allowBreakDelay());
        };

        //WARN in Method org.e2immu.analyser.parser.modification.testexample.Modification_20.example1() (line 43, pos 9): Potential null pointer exception: Variable: set
        testClass("Modification_20", 0, 1, new DebugConfiguration.Builder()
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        //    .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                        .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                        .addTypeMapVisitor(typeMapVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().setComputeFieldAnalyserAcrossAllMethods(true).build());
    }
}
