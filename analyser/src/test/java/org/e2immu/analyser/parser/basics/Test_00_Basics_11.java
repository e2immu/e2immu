
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

package org.e2immu.analyser.parser.basics;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.e2immu.analyser.analyser.Property.CONTEXT_NOT_NULL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Test_00_Basics_11 extends CommonTestRunner {

    public Test_00_Basics_11() {
        super(false);
    }

    @Test
    public void test_11() throws IOException {
        final String NULLABLE_INSTANCE = "nullable instance type String/*@Identity*/";
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("test".equals(d.methodInfo().name)) {
                String value = d.currentValue().toString();
                DV cnn = d.getProperty(CONTEXT_NOT_NULL);

                if (d.variable() instanceof ParameterInfo in1 && "in".equals(in1.name)) {
                    if ("0".equals(d.statementId()) || "1".equals(d.statementId())) {
                        assertEquals(NULLABLE_INSTANCE, value);
                        assertEquals(MultiLevel.NULLABLE_DV, cnn);
                    }
                    if ("2".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "<p:in>" : NULLABLE_INSTANCE;
                        assertEquals(expect, value);
                    }
                    if ("3".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "<p:in>" : NULLABLE_INSTANCE;
                        assertEquals(expect, value);
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, cnn);
                    }
                    if ("4".equals(d.statementId())) {
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, cnn);
                    }
                }
                if ("s1".equals(d.variableName())) {
                    if ("0".equals(d.statementId()) || "1".equals(d.statementId())) {
                        assertEquals("in", value);
                    }
                    if ("2".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "<v:s1>" : "in";
                        assertEquals(expect, value);
                    }
                }
                if ("s2".equals(d.variableName())) {
                    if ("1".equals(d.statementId()) || "2".equals(d.statementId())) {
                        assertEquals("in", value);
                    }
                    if ("3".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "<v:s2>" : "in";
                        assertEquals(expect, value);
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, cnn);
                    }
                }
                if (d.variable() instanceof FieldReference fr && "out".equals(fr.fieldInfo.name)) {
                    assertTrue(d.statementId().compareTo("2") >= 0);
                    String expectValue = d.iteration() == 0 ? "<f:out>" : "nullable instance type PrintStream";
                    assertEquals(expectValue, d.currentValue().toString());
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(CONTEXT_NOT_NULL));
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("test".equals(d.methodInfo().name)) {
                if ("2".equals(d.statementId())) {
                    Message error = d.haveError(Message.Label.POTENTIAL_NULL_POINTER_EXCEPTION);
                    assertEquals(d.iteration() > 0, error != null);
                }
            }
        };

        // warning: out potential null pointer (x1) and assert always true (x1)
        testClass("Basics_11", 0, 2, new DebugConfiguration.Builder()
             //   .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
             //   .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }
}
