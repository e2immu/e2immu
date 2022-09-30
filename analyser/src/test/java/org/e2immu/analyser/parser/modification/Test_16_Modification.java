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
import org.e2immu.analyser.analyser.Stage;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.FieldAnalyserVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.TypeAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class Test_16_Modification extends CommonTestRunner {

    public Test_16_Modification() {
        super(true);
    }

    @Test
    public void test7() throws IOException {
        //WARN in Method org.e2immu.analyser.parser.modification.testexample.Modification_7.stream() (line 44, pos 9): Potential null pointer exception: Variable: set
        testClass("Modification_7", 0, 1, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test8() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("Modification_8".equals(d.methodInfo().name) && d.variable() instanceof FieldReference fr
                    && "set".equals(fr.fieldInfo.name)) {
                assertEquals("input/*@NotNull*/", d.currentValue().toString());
                assertEquals(MultiLevel.MUTABLE_DV, d.getProperty(Property.IMMUTABLE));
            }
        };
        testClass("Modification_8", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }


    @Test
    public void test13() throws IOException {
        final String INNER_THIS = "org.e2immu.analyser.parser.modification.testexample.Modification_13.Inner.this";
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("clearIfExceeds".equals(d.methodInfo().name) && INNER_THIS.equals(d.variableName())) {
                if ("0".equals(d.statementId())) {
                    assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                }
                if ("0.0.0".equals(d.statementId())) {
                    assertDv(d, 0, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                }
            }
            if ("Modification_13".equals(d.methodInfo().name)) {
                assertEquals("0", d.statementId());
                if (d.variable() instanceof ParameterInfo pi && "input".equals(pi.name)) {
                    assertEquals("this.set:4", d.variableInfo().getLinkedVariables().toString());
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("clearIfExceeds".equals(d.methodInfo().name)) {
                assertDv(d, 1, DV.TRUE_DV, Property.MODIFIED_METHOD);
            }
            if ("Modification_13".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 1, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
                assertDv(d.p(0), 1, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
            }
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Modification_13".equals(d.typeInfo().simpleName)) {
                assertTrue(d.typeAnalysis().getHiddenContentTypes().isEmpty());
            }
        };
        testClass("Modification_13", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }

    @Test
    public void test14() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("Modification_14".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo input && "input".equals(input.name)) {
                    if ("0".equals(d.statementId())) {
                        assertTrue(d.variableInfoContainer().hasEvaluation());
                        if (d.iteration() > 1) {
                            VariableInfo eval = d.variableInfoContainer().best(Stage.EVALUATION);
                            assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, eval.getProperty(Property.EXTERNAL_NOT_NULL));
                            assertTrue(d.variableInfoContainer().hasMerge());
                            assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(Property.EXTERNAL_NOT_NULL));
                        }
                    }
                }
                if (d.variable() instanceof FieldReference fieldReference
                        && "input".equals(fieldReference.fieldInfo.name)) {
                    if ("1".equals(d.statementId())) {
                        assertDv(d, 2, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.EXTERNAL_NOT_NULL);
                    }
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("input".equals(d.fieldInfo().name)) {
                assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.EXTERNAL_NOT_NULL);
                String expected = d.iteration() == 0 ? "<f:input>" : "input";
                assertEquals(expected, d.fieldAnalysis().getValue().toString());
            }
            if ("j".equals(d.fieldInfo().name)) {
                assertDv(d, MultiLevel.CONTAINER_DV, Property.CONTAINER);
                assertDv(d, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER_RESTRICTION);
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("TwoIntegers".equals(d.typeInfo().simpleName)) {
                assertDv(d, 1, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
            }
        };

        // ! no warning @NotNull on field -> if(...)
        testClass("Modification_14", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }

    @Test
    public void test15() throws IOException {
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("TwoIntegers".equals(d.typeInfo().simpleName)) {
                assertDv(d, 1, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
            }
        };

        /*
        in iteration 2, statementAnalysis should copy the IMMUTABLE value of 1 of input into the variable's properties
         */
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("Modification_15".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "input".equals(pi.name)) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, 3, MultiLevel.MUTABLE_DV, Property.EXTERNAL_IMMUTABLE);
                        assertDv(d, 2, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.EXTERNAL_NOT_NULL);
                        assertDv(d, 0, MultiLevel.MUTABLE_DV, Property.CONTEXT_IMMUTABLE);
                    }
                    if ("1".equals(d.statementId())) {
                        assertDv(d, MultiLevel.MUTABLE_DV, Property.CONTEXT_IMMUTABLE);
                    }
                } else if (d.variable() instanceof FieldReference fr && "input".equals(fr.fieldInfo.name)) {
                    assertEquals("1", d.statementId());
                    assertDv(d, 2, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.EXTERNAL_NOT_NULL);
                    assertDv(d, 3, MultiLevel.MUTABLE_DV, Property.EXTERNAL_IMMUTABLE);
                } else if (d.variable() instanceof This) {
                    assertDv(d, 0, MultiLevel.NOT_INVOLVED_DV, Property.EXTERNAL_NOT_NULL);
                    assertDv(d, 4, MultiLevel.EFFECTIVELY_FINAL_FIELDS_DV, Property.EXTERNAL_IMMUTABLE);
                } else {
                    fail("?" + d.variableName());
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("Modification_15".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 3, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
            }
        };
        testClass("Modification_15", 1, 0, new DebugConfiguration.Builder()
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    /*
    List<ErrorMessage> is mutable, even if the List itself were immutable -- it is the List's concrete choice of
    hidden content that determines the final immutable value.

    Now is errorMessage linked :2 or linked :3 to messages?
    It should be :3 because ErrorMessage takes the role of hidden content in List<ErrorMessage>
    A modification to the list does not imply a modification to the ErrorMessage.
    A modification in an error message does imply a modification to the whole graph, but it's in List's hidden content.

    It is important to note that ErrorMessage is not part of the hidden content of FaultyImplementation!
    The parameter ErrorMessage does link in a DEPENDENT fashion to the fields of the type, even if its link with
    this.messages is :3.
    */

    @Test
    public void test16() throws IOException {
        // one error on the method's parameter
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("addError".equals(d.methodInfo().name)) {
                if ("FaultyImplementation".equals(d.methodInfo().typeInfo.simpleName)) {
                    if (d.variable() instanceof ParameterInfo pi && "errorMessage".equals(pi.name)) {
                        if ("0".equals(d.statementId())) {
                            String linked = d.iteration() <= 1 ? "this.messages:-1" : "this.messages:3";
                            assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                        }
                    }
                    if (d.variable() instanceof FieldReference fr && "messages".equals(fr.fieldInfo.name)) {
                        if ("0".equals(d.statementId())) {
                            // asymmetrical link!
                            String linked = d.iteration() <= 1 ? "errorMessage:-1" : "";
                            assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                        }
                    }
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("getErrors".equals(d.methodInfo().name)) {
                if ("ErrorRegistry".equals(d.methodInfo().typeInfo.simpleName)) {
                    assertDv(d, MultiLevel.CONTAINER_DV, Property.CONTAINER);
                    assertDv(d, MultiLevel.DEPENDENT_DV, Property.INDEPENDENT);
                }
                if ("FaultyImplementation".equals(d.methodInfo().typeInfo.simpleName)) {
                    assertDv(d, 1, MultiLevel.CONTAINER_DV, Property.CONTAINER);
                    assertDv(d, 3, MultiLevel.DEPENDENT_DV, Property.INDEPENDENT);
                }
            }
            if ("addError".equals(d.methodInfo().name)) {
                if ("ErrorRegistry".equals(d.methodInfo().typeInfo.simpleName)) {
                    assertDv(d.p(0), DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                    assertDv(d.p(0), MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER_RESTRICTION);
                    assertDv(d.p(0), 2, MultiLevel.CONTAINER_DV, Property.CONTAINER);

                    // 2 delays because of the computation of IMMUTABLE of ErrorMessage, which is a class which needs
                    // the ComputedTypeAnalyser. It ends up being MUTABLE, so that the parameter becomes DEPENDENT by convention
                    assertDv(d.p(0), 2, MultiLevel.DEPENDENT_DV, Property.INDEPENDENT);
                }
                if ("FaultyImplementation".equals(d.methodInfo().typeInfo.simpleName)) {
                    assertDv(d.p(0), 3, MultiLevel.CONTAINER_DV, Property.CONTAINER);
                    assertDv(d.p(0), 3, MultiLevel.DEPENDENT_DV, Property.INDEPENDENT);
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("messages".equals(d.fieldInfo().name)) {
                assertEquals("instance type ArrayList<ErrorMessage>", d.fieldAnalysis().getValue().toString());
                assertDv(d, MultiLevel.CONTAINER_DV, Property.CONTAINER);
                assertDv(d, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER_RESTRICTION);
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("FaultyImplementation".equals(d.typeInfo().simpleName)) {
                assertHc(d, 2, "");
                assertDv(d, 3, MultiLevel.DEPENDENT_DV, Property.INDEPENDENT);
            }
            if ("ErrorRegistry".equals(d.typeInfo().simpleName)) {
                assertTrue(d.typeAnalysis().getHiddenContentTypes().isEmpty());
                assertDv(d, MultiLevel.DEPENDENT_DV, Property.INDEPENDENT);

            }
        };

        testClass("Modification_16_M", 1, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }


    @Test
    public void test17() throws IOException {
        // statics
        testClass("Modification_17", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    // static method reference
    @Test
    public void test18() throws IOException {
        // statics
        testClass("Modification_18", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test22() throws IOException {
        testClass("Modification_22", 0, 0, new DebugConfiguration.Builder()
                .build());
    }
}
