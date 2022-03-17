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
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.e2immu.analyser.analyser.Property.*;
import static org.junit.jupiter.api.Assertions.*;

// differs only in the approach to @NotNull, see parameter computeContextPropertiesOverAllMethods, now set to true

// cause(s|d) a crash where the other version doesn't, so worth splitting out.

public class Test_16_Modification_11_2 extends CommonTestRunner {

    public Test_16_Modification_11_2() {
        super(true);
    }

    @Test
    public void test11_2() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("C1".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "set".equals(fr.fieldInfo.name)) {
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(NOT_NULL_EXPRESSION));
                    // not a direct assignment!
                    assertEquals("setC:1,this.set:0", d.variableInfo().getLinkedVariables().toString());
                    assertEquals(MultiLevel.NULLABLE_DV, d.getProperty(CONTEXT_NOT_NULL));
                }
                if (d.variable() instanceof ParameterInfo setC && "setC".equals(setC.name)) {
                    assertEquals(DV.FALSE_DV, d.getProperty(CONTEXT_MODIFIED));
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(CONTEXT_NOT_NULL));
                }
            }

            if ("getSet".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "set".equals(fr.fieldInfo.name)) {
                    assertEquals(MultiLevel.NULLABLE_DV, d.getProperty(CONTEXT_NOT_NULL));
                    assertDv(d, 2, MultiLevel.MUTABLE_DV, IMMUTABLE);
                    String expectValue = d.iteration() <= 1 ? "<f:set>" : "instance type Set<String>";
                    assertEquals(expectValue, d.currentValue().toString());
                }
                if (d.variable() instanceof ReturnVariable) {
                    String expect = switch (d.iteration()) {
                        case 0 -> "<f:set>";
                        case 1 -> "<vp:set:cnn@Parameter_c>";
                        default -> "set";
                    };
                    assertEquals(expect, d.currentValue().toString());
                    String expectDelay = switch (d.iteration()) {
                        case 0 -> "initial:this.set@Method_getSet_0-C";
                        case 1 -> "cnn@Parameter_c";
                        default -> "";
                    };
                    assertEquals(expectDelay, d.currentValue().causesOfDelay().toString());
                    assertDv(d, 2, MultiLevel.MUTABLE_DV, IMMUTABLE);
                }
            }

            if ("add".equals(d.methodInfo().name) && "C1".equals(d.methodInfo().typeInfo.simpleName)) {
                if (d.variable() instanceof FieldReference fr && "set".equals(fr.fieldInfo.name)) {
                    String expectValue = d.iteration() <= 1 ? "<mmc:set>" : "instance type Set<String>";
                    assertEquals(expectValue, d.currentValue().toString());
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(CONTEXT_NOT_NULL));
                    assertDv(d, DV.TRUE_DV, CONTEXT_MODIFIED);

                    String expectLv = "this.set:0";
                    assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                }
                if (d.variable() instanceof ParameterInfo s && "string".equals(s.name)) {
                    String expectLv = "string:0";
                    assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                    //container:this.set@Method_add_0
                    assertDv(d, DV.FALSE_DV, CONTEXT_MODIFIED);
                }
            }


            if ("example1".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "s2".equals(fr.fieldInfo.name)) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, 2, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, CONTEXT_NOT_NULL);
                        String expected = d.iteration() <= 2 ? "c:-1,this.s2:0" : "c:2,this.s2:0";
                        assertEquals(expected, d.variableInfo().getLinkedVariables().toString());
                    }
                }
                if ("c".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertEquals(MultiLevel.NULLABLE_DV, d.getProperty(CONTEXT_NOT_NULL));
                    }
                    if ("2".equals(d.statementId())) {
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(CONTEXT_NOT_NULL));

                        String expectLinked = d.iteration() <= 2 ? "c.set:-1,c:0,this.s2:-1"
                                : "c.set:2,c:0,new org.e2immu.analyser.parser.modification.testexample.Modification_11.C1(this.s2).set:2,this.s2:2";
                        assertEquals(expectLinked, d.variableInfo().getLinkedVariables().toString());
                    }
                }
                if (d.variable() instanceof ReturnVariable && "2".equals(d.statementId())) {
                    String causesOfDelay = switch (d.iteration()) {
                        case 0 -> "initial:c.set@Method_example1_2-C;initial:localD.set@Method_example1_2-C";
                        case 1 -> "cnn@Parameter_c";
                        // when all goes well:
                        case 2 -> "initial:this.s2@Method_example1_0-C;initial@Field_set";
                        // otherwise: cm@Parameter_string;container@Class_C1;initial@Method_add
                        default -> "";
                    };
                    assertEquals(causesOfDelay, d.currentValue().causesOfDelay().toString());
                    String expectValue = d.iteration() <= 2 ? "<m:addAll>" : "instance type boolean";
                    assertEquals(expectValue, d.currentValue().toString());
                }
            }
            if ("addAll".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi1 && "d".equals(pi1.name)) {
                    assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV,
                            d.getProperty(CONTEXT_NOT_NULL));
                }
                if (d.variable() instanceof ParameterInfo pi0 && "c".equals(pi0.name)) {
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(CONTEXT_NOT_NULL));
                }
            }
            if ("Modification_11".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "s2".equals(fr.fieldInfo.name)) {
                    if ("1".equals(d.statementId())) {
                        String expectValue = d.iteration() <= 1 ? "<m:getSet>" : "set2";
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("add".equals(d.methodInfo().name) && "C1".equals(d.methodInfo().typeInfo.simpleName)) {
                assertTrue(d.statementAnalysis().methodLevelData().linksHaveBeenEstablished());
            }
            if ("example1".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertNull(d.haveError(Message.Label.POTENTIAL_NULL_POINTER_EXCEPTION));
                }
                if ("2".equals(d.statementId())) {
                    mustSeeIteration(d, 3);
                    assertEquals(d.iteration() > 2,
                            d.statementAnalysis().methodLevelData().linksHaveBeenEstablished());
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("set".equals(d.fieldInfo().name)) {
                // "setC:1" instead of "c.set:0,localD.set:0,setC:1" consequence of change in FieldAnalyserImpl
                assertEquals("setC:1", d.fieldAnalysis().getLinkedVariables().toString());
                assertEquals("setC/*@NotNull*/", d.fieldAnalysis().getValue().toString());
                // the field analyser sees addAll being used on set in the method addAllOnC
                assertDv(d, 1, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, EXTERNAL_NOT_NULL);
                assertDv(d, DV.TRUE_DV, MODIFIED_OUTSIDE_METHOD);
            }
            if ("s2".equals(d.fieldInfo().name)) {
                assertDv(d, 2, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, EXTERNAL_NOT_NULL);
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("C1".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 2, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, NOT_NULL_PARAMETER);
                assertDv(d.p(0), 1, DV.TRUE_DV, MODIFIED_VARIABLE);
            }
            if ("addAll".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, NOT_NULL_PARAMETER);
                assertDv(d.p(0), 1, DV.TRUE_DV, MODIFIED_VARIABLE);

                assertDv(d.p(1), 1, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, NOT_NULL_PARAMETER);
                assertDv(d.p(1), 1, DV.FALSE_DV, MODIFIED_VARIABLE);
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Modification_11".equals(d.typeInfo().simpleName)) {
                assertTrue(d.typeAnalysis().getTransparentTypes().isEmpty());
            }
            if ("C1".equals(d.typeInfo().simpleName)) {
                assertDv(d, 1, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER);
            }
        };

        testClass("Modification_11", 0, 0, new DebugConfiguration.Builder()
                        .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                        .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder()
                        .setComputeFieldAnalyserAcrossAllMethods(true)
                        .setComputeContextPropertiesOverAllMethods(true).build());
    }

    @Test
    public void test11_3() throws IOException {
        // the @NotNull1 computations require linking to be across the primary type!
        // if that's disabled, there is no chance of detecting them
        testClass("Modification_11", 3, 0,
                new DebugConfiguration.Builder().build(),
                new AnalyserConfiguration.Builder()
                        .setComputeFieldAnalyserAcrossAllMethods(false)
                        .setComputeContextPropertiesOverAllMethods(true)
                        .build());
    }
}
