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
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.visitor.FieldAnalyserVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class Test_16_Modification_4 extends CommonTestRunner {

    public Test_16_Modification_4() {
        super(true);
    }

    /*
    What happens in each iteration?
    IT 0: READ, ASSIGNED; set4 FINAL in field analyser, gets value and linked variables
    IT 1: set4 gets a value in add4; set4 linked to in4
    IT 2: set4 MODIFIED, NOT_NULL;
     */

    @Test
    public void test4() throws IOException {
        final String SET4 = "org.e2immu.analyser.parser.modification.testexample.Modification_4.set4";
        final String SET4_DELAYED = "<f:set4>";

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("add4".equals(d.methodInfo().name)) {
                if (SET4.equals(d.variableName())) {
                    assertEquals("0-E", d.variableInfo().getReadId());
                    if ("0".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<f:set4>" : "instance type Set<String>";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        // via statical assignments
                        assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                        assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                        String expected = d.iteration() == 0 ? "<f:set4>"
                                : "instance type Set<String>/*this.contains(v)&&this.size()>=1*/";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
                if ("local4".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                        assertEquals("this.set4:0", d.variableInfo().getLinkedVariables().toString());

                        assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);

                        String expect = d.iteration() == 0 ? SET4_DELAYED : "set4";
                        assertEquals(expect, d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                        assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                        String expect = d.iteration() == 0 ? "<f:set4>" : "set4";
                        assertEquals(expect, d.currentValue().toString());
                        assertEquals("this.set4:0", d.variableInfo().getLinkedVariables().toString());
                    }
                }
            }
            if ("Modification_4".equals(d.methodInfo().name) && SET4.equals(d.variableName()) && "0".equals(d.statementId())) {
                assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.EXTERNAL_NOT_NULL);
                assertEquals(MultiLevel.NULLABLE_DV, d.getProperty(Property.NOT_NULL_EXPRESSION));
                assertEquals("in4", d.currentValue().toString());
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("add4".equals(d.methodInfo().name) && "1".equals(d.statementId())) {
                assertNull(d.haveError(Message.Label.NULL_POINTER_EXCEPTION));
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if (d.fieldInfo().name.equals("set4")) {
                assertEquals(DV.TRUE_DV, d.fieldAnalysis().getProperty(Property.FINAL));
                assertDv(d, 1, DV.TRUE_DV, Property.MODIFIED_OUTSIDE_METHOD);
                assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.EXTERNAL_NOT_NULL);

                assertEquals("in4", d.fieldAnalysis().getValue().toString());
                assertEquals("in4:0", d.fieldAnalysis().getLinkedVariables().toString());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            int iteration = d.iteration();
            String name = d.methodInfo().name;
            if ("Modification_4".equals(name)) {
                assertDv(d.p(0), 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.EXTERNAL_NOT_NULL);
                assertDv(d.p(0), 2, DV.TRUE_DV, Property.MODIFIED_VARIABLE);
            }
            if ("add4".equals(name)) {
                FieldInfo set4 = d.methodInfo().typeInfo.getFieldByName("set4", true);
                if (iteration >= 1) {
                    VariableInfo vi = d.getFieldAsVariable(set4);
                    assert vi != null;
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, vi.getProperty(Property.CONTEXT_NOT_NULL));
                    assertEquals(DV.TRUE_DV, d.methodAnalysis().getProperty(Property.MODIFIED_METHOD));
                }
            }
        };

        // we've set the "compute context properties over all methods" to true
        testClass("Modification_4", 0, 0, new DebugConfiguration.Builder()
                        .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder()
                        .setComputeContextPropertiesOverAllMethods(true).build());
    }

}
