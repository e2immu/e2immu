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

import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class Test_66_VariableScope extends CommonTestRunner {

    public Test_66_VariableScope() {
        super(false);
    }

    @Test
    public void test_0() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("writeLine".equals(d.methodInfo().name)) {
                if ("e".equals(d.variableName())) {
                    if ("0.0.0".equals(d.statementId())) {
                        fail("At 0.0.0 in writeLine");
                    }
                    if ("0.1.0".equals(d.statementId())) {
                        // here, the variable is expected
                        assertEquals("", d.currentValue().toString());
                    }
                    if ("0".equals(d.statementId())) {
                        fail("At 0 in writeLine");
                    }
                }
            }
            if ("test".equals(d.methodInfo().name) && "$1".equals(d.methodInfo().typeInfo.simpleName)) {
                if ("e".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        fail("At 0 in $1.test");
                    }
                    if ("0.1.0".equals(d.statementId())) {
                        // here, the variable is expected
                        assertEquals("instance type IOException", d.currentValue().toString());
                    }
                }
            }
        };
        testClass("VariableScope_0", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

}
