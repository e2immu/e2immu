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

package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/*
continuation of some examples that are in Basics 2, 3, 6, 7, 8, 14, 15
 */
public class Test_13_VariableField extends CommonTestRunner {

    public Test_13_VariableField() {
        super(false);
    }

    @Test
    public void test_0() throws IOException {
        final String STRING = "org.e2immu.analyser.testexample.VariableField_0.string";

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("getString".equals(d.methodInfo().name)) {

                // first read copy

                if (d.variableName().endsWith("string$0")) {
                    assertEquals(STRING + "$0", d.variableName());
                    if ("0.0.0".equals(d.statementId()) || "0.0.0.0.0".equals(d.statementId())) {
                        assertEquals("nullable instance type String", d.currentValue().toString());
                    } else if ("0".equals(d.statementId()) || "1".equals(d.statementId())) {
                        // result of merging: FIXME <v:string$0> should not be a delayed variable, but sth else
                        assertEquals("b?nullable instance type String:<v:string$0>", d.currentValue().toString());
                    } else fail("Statement " + d.statementId());
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));

                } else if (d.variableName().endsWith("string$1")) {
                    assertEquals(STRING + "$1", d.variableName());
                    if ("0.0.0.0.0".equals(d.statementId())) {
                        assertEquals("nullable instance type String", d.currentValue().toString());
                    } else if ("0.0.0".equals(d.statementId())) {
                        assertEquals("string$0.startsWith(\"abc\")?nullable instance type String:<v:string$1>", d.currentValue().toString());
                    } else if ("0".equals(d.statementId()) || "1".equals(d.statementId())) {
                        assertEquals("b?string$0.startsWith(\"abc\")?nullable instance type String:<v:string$1>:<v:string$1>", d.currentValue().toString());
                    } else fail("Statement " + d.statementId());

                    // inherited from the previous read copy:
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));

                } else if (d.variableName().contains("string")) {
                    assertEquals(STRING, d.variableName());
                    // according to the new rules, this one should not have CNN set to Effectively Not Null
                }
            }
        };

        testClass("VariableField_0", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

}
