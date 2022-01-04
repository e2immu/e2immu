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
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analyser.VariableInfoContainer;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.analysis.impl.FieldAnalysisImpl;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class Test_16_Modification_11 extends CommonTestRunner {

    public Test_16_Modification_11() {
        super(true);
    }

    @Test
    public void test11() throws IOException {
        final String TYPE = "org.e2immu.analyser.testexample.Modification_11";
        final String SET_IN_C1 = TYPE + ".C1.set";
        final String SET_IN_C1_DELAYED = "<f:set>";
        final String S2 = TYPE + ".s2";

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("C1".equals(d.methodInfo().name)) {
                if (SET_IN_C1.equals(d.variableName())) {
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(Property.NOT_NULL_EXPRESSION));
                    // not a direct assignment!
                    assertEquals("setC:1,this.set:0", d.variableInfo().getLinkedVariables().toString());
                    assertEquals(MultiLevel.NULLABLE_DV, d.getProperty(Property.CONTEXT_NOT_NULL));
                }
                if (d.variable() instanceof ParameterInfo setC && "setC".equals(setC.name)) {
                    assertEquals(DV.FALSE_DV, d.getProperty(Property.CONTEXT_MODIFIED));
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(Property.CONTEXT_NOT_NULL));
                }
            }

            if ("getSet".equals(d.methodInfo().name) && SET_IN_C1.equals(d.variableName())) {
                assertEquals(MultiLevel.NULLABLE_DV, d.getProperty(Property.CONTEXT_NOT_NULL));
                String expectValue = d.iteration() <= 1 ? SET_IN_C1_DELAYED : "instance type Set<String>";
                assertEquals(expectValue, d.currentValue().toString());
            }

            if ("add".equals(d.methodInfo().name) && "C1".equals(d.methodInfo().typeInfo.simpleName)) {
                if (SET_IN_C1.equals(d.variableName())) {
                    String expectValue = d.iteration() <= 1 ? SET_IN_C1_DELAYED : "instance type Set<String>";
                    assertEquals(expectValue, d.currentValue().toString());
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(Property.CONTEXT_NOT_NULL));
                    assertDv(d, 2, DV.TRUE_DV, Property.CONTEXT_MODIFIED);

                    String expectLv = d.iteration() <= 1 ? "string:-1,this.set:0" : "this.set:0";
                    assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                }
                if (d.variable() instanceof ParameterInfo s && "string".equals(s.name)) {
                    String expectLv = d.iteration() <= 1 ? "string:0,this.set:-1" : "string:0";
                    assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                }
            }


            if ("example1".equals(d.methodInfo().name)) {
                if (S2.equals(d.variableName()) && "0".equals(d.statementId())) {
                    assertDv(d, 2, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                    assertEquals("c:-1,this.s2:0", d.variableInfo().getLinkedVariables().toString());
                }
                if ("c".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertEquals(MultiLevel.NULLABLE_DV, d.getProperty(Property.CONTEXT_NOT_NULL));
                    }
                    if ("2".equals(d.statementId())) {
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(Property.CONTEXT_NOT_NULL));

                    }
                    String expectLinked = d.iteration() <= 2 ? "c:0,this.s2:-1" : "this.s2";
                    assertEquals(expectLinked, d.variableInfo().getLinkedVariables().toString());
                }
                if (d.variable() instanceof ReturnVariable && "2".equals(d.statementId())) {
                    String expectValue = d.iteration() <= 2 ? "<m:addAll>" : "instance type boolean";
                    assertEquals(expectValue, d.currentValue().toString());
                }
            }
            if ("addAll".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi1 && "d".equals(pi1.name)) {
                    assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV,
                            d.getProperty(Property.CONTEXT_NOT_NULL));
                }
                if (d.variable() instanceof ParameterInfo pi0 && "c".equals(pi0.name)) {
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(Property.CONTEXT_NOT_NULL));
                }
            }
            if ("Modification_11".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "s2".equals(fr.fieldInfo.name)) {
                    if ("1".equals(d.statementId())) {
                        String expectValue = d.iteration() < 2 ? "<m:getSet>" : "set2";
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("add".equals(d.methodInfo().name) && "C1".equals(d.methodInfo().typeInfo.simpleName)) {
                assertEquals(d.iteration() >= 2,
                        d.statementAnalysis().methodLevelData().linksHaveBeenEstablished());
            }
            if ("example1".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertNull(d.haveError(Message.Label.POTENTIAL_NULL_POINTER_EXCEPTION));
                }
                if ("2".equals(d.statementId())) {
                    assertEquals(d.iteration() >= 3,
                            d.statementAnalysis().methodLevelData().linksHaveBeenEstablished());
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("set".equals(d.fieldInfo().name)) {
                assertEquals("c.set:0,localD.set:0,setC:1", d.fieldAnalysis().getLinkedVariables().toString());
                assertEquals("setC/*@NotNull*/", d.fieldAnalysis().getValue().debugOutput());
                // the field analyser sees addAll being used on set in the method addAllOnC
                assertDv(d, 1, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, Property.EXTERNAL_NOT_NULL);
                assertDv(d, 2, DV.TRUE_DV, Property.MODIFIED_OUTSIDE_METHOD);
            }
            if ("s2".equals(d.fieldInfo().name)) {
                assertDv(d, 2, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, Property.EXTERNAL_NOT_NULL);
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("C1".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 2, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, Property.NOT_NULL_PARAMETER);
                assertDv(d.p(0), 3, DV.TRUE_DV, Property.MODIFIED_VARIABLE);
            }
            if ("addAll".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_PARAMETER);
                assertDv(d.p(0), 1, DV.TRUE_DV, Property.MODIFIED_VARIABLE);

                assertDv(d.p(1), 1, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, Property.NOT_NULL_PARAMETER);
                assertDv(d.p(1), 1, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Modification_11".equals(d.typeInfo().simpleName)) {
                assertTrue(d.typeAnalysis().getTransparentTypes().isEmpty());
            }
        };

        testClass("Modification_11", 0, 0, new DebugConfiguration.Builder()
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

}
