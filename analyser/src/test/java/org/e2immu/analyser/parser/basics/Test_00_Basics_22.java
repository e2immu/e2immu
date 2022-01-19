
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


import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.VariableNature;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;


public class Test_00_Basics_22 extends CommonTestRunner {
    public Test_00_Basics_22() {
        super(true);
    }

    @Test
    public void test() throws IOException {

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("loadBytes".equals(d.methodInfo().name)) {
                if ("byteArrayOutputStream".equals(d.variableName())) {
                    if (d.variableInfoContainer().variableNature() instanceof VariableNature.TryResource tryResource) {
                        assertEquals("1.0.0", tryResource.statementIndex());
                        assertFalse(tryResource.removeInSubBlockMerge("1"));
                    } else fail();
                }
                if (d.variable() instanceof ReturnVariable) {
                    // return statement
                    if ("1.0.0.0.1".equals(d.statementId())) {
                        String expect = d.iteration() <= 1 ? "<m:toByteArray>" : "byteArrayOutputStream.toByteArray()";
                        assertEquals(expect, d.currentValue().toString());
                        String expectLv = d.iteration() <= 1 ? "byteArrayOutputStream:-1,return loadBytes:0"
                                : "return loadBytes:0";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                    }

                    // try statement
                    if ("1.0.0".equals(d.statementId())) {
                        String expect = d.iteration() <= 1 ? "<m:toByteArray>" : "(instance type ByteArrayOutputStream).toByteArray()";
                        assertEquals(expect, d.currentValue().toString());
                        String expectLv = d.iteration() <= 1 ? "byteArrayOutputStream:-1,return loadBytes:0"
                                : "return loadBytes:0";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                    }

                    // for-loop: here, byteArrayOutputStream does not exist!
                    if ("1".equals(d.statementId())) {
                        String expect = switch (d.iteration()) {
                            case 0 -> "<v:prefix>.length>0?<m:toByteArray>:<return value>";
                            case 1 -> "prefix.length>0?<m:toByteArray>:<return value>";
                            default -> "prefix.length>0?(instance type ByteArrayOutputStream).toByteArray():<return value>";
                        };
                        assertEquals(expect, d.currentValue().toString());
                        String expectLv = "return loadBytes:0";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                    }
                }
            }
        };
        // expect one potential null pointer warning
        testClass("Basics_22", 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

}
