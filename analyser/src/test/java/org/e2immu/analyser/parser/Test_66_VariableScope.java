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
import org.e2immu.analyser.model.TypeAnalysis;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.VariableNature;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.e2immu.analyser.visitor.TypeMapVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class Test_66_VariableScope extends CommonTestRunner {

    public Test_66_VariableScope() {
        super(false);
    }

    @Test
    public void test_0() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("j".equals(d.variableName())) {
                    if ("0".equals(d.statementId()) || "1".equals(d.statementId())) {
                        fail("j exists in statement " + d.statementId());
                    } else if (d.statementId().startsWith("0.0")) {
                        assertEquals("i", d.currentValue().toString());
                    } else if ("2".equals(d.statementId()) || "3".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "3*<p:i>" : "3*i";
                        assertEquals(expect, d.currentValue().toString());
                    } else fail(); // no other statements
                }
            }
        };
        // potential null pointer in out
        testClass("VariableScope_0", 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }


    @Test
    public void test_1() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("j".equals(d.variableName())) {
                    if ("0".equals(d.statementId()) || "2".equals(d.statementId())) {
                        fail("j exists in statement " + d.statementId());
                    } else if ("1.0.0".equals(d.statementId()) || "1.0.1".equals(d.statementId())) {
                        assertEquals("0", d.currentValue().toString());
                    } else if ("1.0.2.0.0".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "<v:j>+<m:nextInt>" : "r.nextInt()+j$1.0.2";
                        assertEquals(expect, d.currentValue().toString());
                    } else if ("1.0.2".equals(d.statementId()) || "1.0.3".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "<replace:int><=9?<v:j>+<m:nextInt>:0" :
                                "instance type int<=9?instance type int:0";
                        assertEquals(expect, d.currentValue().toString());
                    } else fail(d.statementId()); // no other statements
                }
                if ("j$1.0.2".equals(d.variableName())) {
                    assertTrue(d.iteration() > 0);
                    assertNotEquals("2", d.statementId());
                }
                if ("k".equals(d.variableName())) {
                    if ("2".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "<merge:int>" : "instance type int<=9?instance type int:0";
                        assertEquals(expect, d.currentValue().toString());
                    }
                }
            }
        };
        testClass("VariableScope_1", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }


    @Test
    public void test_2() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("writeLine".equals(d.methodInfo().name)) {
                if ("e".equals(d.variableName())) {
                    if (!"1.1.0".equals(d.statementId())) {
                        fail("At " + d.statementId() + " in writeLine");
                    }
                    if (d.variableInfoContainer().variableNature() instanceof VariableNature.NormalLocalVariable nlv) {
                        assertEquals("1", nlv.parentBlockIndex);
                    } else fail();
                    assertEquals("instance type IOException", d.currentValue().toString());
                    assertEquals(MultiLevel.MUTABLE, d.getProperty(VariableProperty.IMMUTABLE));
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.currentValue().getProperty(d.evaluationContext(), VariableProperty.NOT_NULL_EXPRESSION, true));
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                }
                if ("ioe".equals(d.variableName())) {
                    if ("1.1.0".equals(d.statementId())) {
                        assertEquals("e", d.currentValue().toString());
                        assertEquals(MultiLevel.MUTABLE, d.getProperty(VariableProperty.IMMUTABLE));
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                    }
                    if ("1".equals(d.statementId())) {
                        assertEquals("instance type IOException", d.currentValue().toString());
                        assertEquals(MultiLevel.MUTABLE, d.getProperty(VariableProperty.IMMUTABLE));
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                    }
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("1.0.1".equals(d.statementId())) {
                        assertEquals("null", d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        assertEquals("null", d.currentValue().toString());
                    }
                    if ("2".equals(d.statementId())) {
                        assertEquals("instance type boolean?ioe:null", d.currentValue().toString());
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("writeLine".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    assertEquals("no interrupt=CONDITIONALLY,return=ALWAYS",
                            d.statementAnalysis().flowData.getInterruptsFlow().entrySet().stream()
                                    .map(Object::toString).sorted().collect(Collectors.joining(",")));
                }
            }
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo ioException = typeMap.get(IOException.class);
            TypeAnalysis ioExceptionAnalysis = ioException.typeAnalysis.get();
            assertEquals(MultiLevel.MUTABLE, ioExceptionAnalysis.getProperty(VariableProperty.IMMUTABLE));
        };

        testClass("VariableScope_2", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }


    @Test
    public void test_3() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("writeLine".equals(d.methodInfo().name)) {
                if ("e".equals(d.variableName())) {
                    if ("0.0.0".equals(d.statementId())) {
                        fail("At 0.0.0 in writeLine");
                    }
                    if ("0.1.0".equals(d.statementId())) {
                        // here, the variable is expected
                        assertEquals("instance type RuntimeException", d.currentValue().toString());
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
        testClass("VariableScope_3", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_4() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("writeLine".equals(d.methodInfo().name)) {
                if ("e".equals(d.variableName())) {
                    if ("1.1.0".equals(d.statementId())) {
                        assertEquals("instance type IOException", d.currentValue().toString());
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.currentValue().getProperty(d.evaluationContext(), VariableProperty.NOT_NULL_EXPRESSION, true));
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                    }
                }
                if ("ioe".equals(d.variableName())) {
                    if ("1.1.0".equals(d.statementId())) {
                        assertEquals("e", d.currentValue().toString());
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                    }
                }
            }
        };
        // 1 error: overwriting assignment to ioe
        testClass("VariableScope_4", 1, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }
}
