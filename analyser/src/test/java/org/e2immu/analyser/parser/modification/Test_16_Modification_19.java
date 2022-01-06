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
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.analysis.impl.FieldAnalysisImpl;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.FieldAnalyserVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.TypeAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
This is an example of a cyclic dependency on the ContextModified computation.
See ComputeLinkedVariables for the actual code breaking the dependency.

The sequence is:

1. parameter setC has been assigned to C1.set; it needs the MODIFIED_OUTSIDE_METHOD property from the field.
2. the MOM of C1.set is computed based on the CONTEXT_MODIFIED property in the VariableInfo of the field in the
   2 methods where it occurs: size() and example1().
3. to assign a CM property to a variable, we wait until we have value for that variable. Otherwise, we run into
   problems as shown in Basics_20.
   The CM value of set in size() is duly computed.
4. The CM value of set in example1() being set depends on the evaluation of c.set (rather than this.set).
   C1 c = new C1(...);
   To have a value for c, we need the value properties of C1.
5. Among the value properties for C1 is @Container, which needs the MODIFIED_VARIABLE of all parameters of methods
   and constructors in C1, and therefore also of setC. This completes the circle.

The solution is to add a dedicated delay each time we let a CM wait because the evaluation was delayed.
At some point, we can see that this evaluation is delayed exactly by this same delay (the delay has traveled the system.)
Then, we break, and we assign the CM regardless.
 */
public class Test_16_Modification_19 extends CommonTestRunner {

    public Test_16_Modification_19() {
        super(true);
    }

    @Test
    public void test19() throws IOException {
        final int LIMIT = 4;

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("example1".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "s2".equals(fr.fieldInfo.name)) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, 3, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if ("c".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        String expectValue = "new C1(s2)";
                        String expectedDelay = switch (d.iteration()) {
                            case 0 -> "container@Class_C1;immutable@Class_C1;independent@Class_C1";
                            case 1 -> "assign_to_field@Parameter_setC;cm:this.set@Method_size_0;initial:this.set@Method_size_0;link:this.set@Method_size_0";
                            case 2 -> "cm:c.set@Method_example1_2;cm:localD.set@Method_example1_2;cm:this.set@Method_size_0;immutable@Class_C1;independent@Parameter_setC;initial:this.set@Method_size_0;link:c@Method_example1_2;link:this.s2@Method_example1_2;link:this.set@Method_size_0";
                            default -> "cm:c.set@Method_example1_2;cm:localD.set@Method_example1_2;cm:this.set@Method_size_0;initial:this.set@Method_size_0;link:c@Method_example1_2;link:this.s2@Method_example1_2;link:this.set@Method_size_0";
                        };
                        assertCurrentValue(d, LIMIT, expectedDelay, expectValue);

                        String linkDelay = switch (d.iteration()) {
                            case 0 -> "immutable@Class_C1;independent@Parameter_setC";
                            case 1 -> "cm:this.set@Method_size_0;initial:this.set@Method_size_0;link:this.set@Method_size_0";
                            default -> "cm:c.set@Method_example1_2;cm:localD.set@Method_example1_2;cm:this.set@Method_size_0;initial:this.set@Method_size_0;link:c@Method_example1_2;link:this.s2@Method_example1_2;link:this.set@Method_size_0";
                        };
                        assertLinked(d, 3, linkDelay, "c:0,this.s2:2");
                    }
                }
                if (d.variable() instanceof FieldReference fr && "c".equals(fr.scope.toString())) {
                    if ("2".equals(d.statementId())) {
                        String expectValue = d.iteration() < LIMIT ? "<f:set>" : "nullable instance type Set<String>";
                        assertEquals(expectValue, d.currentValue().toString());

                        // delays in iteration 1, because no value yet
                        String expectedDelay = d.iteration() == 0
                                ? "cm:c.set@Method_example1_2;immutable@Class_C1;independent@Parameter_setC;link:this.s2@Method_example1_2"
                                : "cm:c.set@Method_example1_2;cm:this.set@Method_size_0;initial:this.set@Method_size_0;link:c@Method_example1_2;link:this.s2@Method_example1_2;link:this.set@Method_size_0";
                        assertDv(d, expectedDelay, 2, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }
            if ("size".equals(d.methodInfo().name) && "C1".equals(d.methodInfo().typeInfo.simpleName)) {
                if (d.variable() instanceof FieldReference fr && "set".equals(fr.fieldInfo.name)) {
                    String expectValue = d.iteration() == 0 ? "<f:set>" : "nullable instance type Set<String>";
                    assertEquals(expectValue, d.currentValue().toString());

                    String expectedDelay = "cm:this.set@Method_size_0;initial:this.set@Method_size_0;link:this.set@Method_size_0";
                    assertDv(d, expectedDelay, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("C1".equals(d.methodInfo().name)) {
                assertTrue(d.methodInfo().isConstructor);
                String expectedDelay = d.iteration() == 0 ? "cm@Parameter_setC;mom@Parameter_setC" : "mom@Parameter_setC";
                assertDv(d.p(0), expectedDelay, 3, DV.TRUE_DV, Property.MODIFIED_VARIABLE);

                ParameterAnalysis p0 = d.parameterAnalyses().get(0);
                String expectAssigned = d.iteration() == 0 ? "[]" : "[set]";
                assertEquals(expectAssigned, p0.getAssignedToField().keySet().toString());
            }
            if ("addAll".equals(d.methodInfo().name)) {
                assertEquals(DV.FALSE_DV, d.methodAnalysis().getProperty(Property.MODIFIED_METHOD));
                assertDv(d.p(0), 1, DV.TRUE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d.p(1), 1, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
            }
            if ("size".equals(d.methodInfo().name) && "C1".equals(d.methodInfo().typeInfo.simpleName)) {
                String expectedDelay = "cm:this.set@Method_size_0;initial:this.set@Method_size_0;link:this.set@Method_size_0";
                assertDv(d, expectedDelay, 1, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("set".equals(d.fieldInfo().name)) {
                assertEquals("setC", d.fieldAnalysis().getValue().toString());
                assertTrue(d.fieldAnalysis().getValue().isDone());

                assertEquals("setC:0", d.fieldAnalysis().getLinkedVariables().toString());
                assertTrue(((FieldAnalysisImpl.Builder) d.fieldAnalysis()).allLinksHaveBeenEstablished().isDone());

                String expectedDelay = d.iteration() == 0
                        ? "cm:c.set@Method_example1_2;cm:localD.set@Method_example1_2;cm:this.set@Method_size_0;immutable@Class_C1;independent@Parameter_setC;initial:this.set@Method_size_0;link:this.s2@Method_example1_2;link:this.set@Method_size_0"
                        : "cm:c.set@Method_example1_2;cm:localD.set@Method_example1_2;cm:this.set@Method_size_0;initial:this.set@Method_size_0;link:c@Method_example1_2;link:this.s2@Method_example1_2;link:this.set@Method_size_0";
                assertDv(d, expectedDelay, 2, DV.TRUE_DV, Property.MODIFIED_OUTSIDE_METHOD);
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("C1".equals(d.typeInfo().simpleName)) {
                assertEquals("", d.typeAnalysis().getTransparentTypes().toString());
                String expectedDelay = d.iteration() == 0
                        ? "cm:this.set@Method_size_0;initial:this.set@Method_size_0;link:this.set@Method_size_0"
                        : "cm:c.set@Method_example1_2;cm:localD.set@Method_example1_2;cm:this.set@Method_size_0;initial:this.set@Method_size_0;link:c@Method_example1_2;link:this.s2@Method_example1_2;link:this.set@Method_size_0";
                assertDv(d, expectedDelay, 2, MultiLevel.EFFECTIVELY_E1IMMUTABLE_DV, Property.IMMUTABLE);

                String expectContainerDelay = switch (d.iteration()) {
                    case 0 -> "assign_to_field@Parameter_setC";
                    case 1 -> "cm:c.set@Method_example1_2;cm:localD.set@Method_example1_2;cm:this.set@Method_size_0;immutable@Class_C1;independent@Parameter_setC;initial:this.set@Method_size_0;link:this.s2@Method_example1_2;link:this.set@Method_size_0";
                    default -> "cm:c.set@Method_example1_2;cm:localD.set@Method_example1_2;cm:this.set@Method_size_0;initial:this.set@Method_size_0;link:c@Method_example1_2;link:this.s2@Method_example1_2;link:this.set@Method_size_0";
                };
                assertDv(d, expectContainerDelay, 3, DV.FALSE_DV, Property.CONTAINER);
            }
        };

        testClass("Modification_19", 0, 2, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .build());
    }
}