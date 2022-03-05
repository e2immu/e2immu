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

package org.e2immu.analyser.parser.start;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.VariableNature;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class Test_66_VariableScope extends CommonTestRunner {

    // we want Random.nextInt() to be modifying
    public Test_66_VariableScope() {
        super(true);
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
        testClass("VariableScope_0", 0, 0, new DebugConfiguration.Builder()
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
                        String expect = d.iteration() == 0 ? "<v:j>+<m:nextInt>" : "instance type int+j$1.0.2";
                        assertEquals(expect, d.currentValue().toString());
                    } else if ("1.0.2".equals(d.statementId()) || "1.0.3".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "<loopIsNotEmptyCondition>?<v:j>+<m:nextInt>:<vl:j>" :
                                "instance type int<=9&&instance type int>=0?instance type int+j$1.0.2:instance type int";
                        assertEquals(expect, d.currentValue().toString());
                    } else fail(d.statementId()); // no other statements
                }
                if ("k".equals(d.variableName())) {
                    if ("1.0.3".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "<loopIsNotEmptyCondition>?<v:j>+<m:nextInt>:<vl:j>" :
                                "instance type int<=9&&instance type int>=0?instance type int+j$1.0.2:instance type int";
                        assertEquals(expect, d.currentValue().toString());
                    }
                    if ("2".equals(d.statementId())) {
                        // there should be no j here!
                        // IMPROVE this could be more elegant c?i+(c?i+i:0)
                        String expect = d.iteration() == 0 ? "<loopIsNotEmptyCondition>?<out of scope:j:1>+<m:nextInt>:<out of scope:j:1>"
                                : "instance type int<=9&&instance type int>=0?instance type int+(instance type int<=9&&instance type int>=0?instance type int+instance type int:instance type int):instance type int";
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
                    assertEquals(MultiLevel.MUTABLE_DV, d.getProperty(Property.IMMUTABLE));
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.currentValue().getProperty(d.context(), Property.NOT_NULL_EXPRESSION, true));
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(Property.NOT_NULL_EXPRESSION));
                }
                if ("ioe".equals(d.variableName())) {
                    if ("1.1.0".equals(d.statementId())) {
                        assertEquals("e", d.currentValue().toString());
                        assertEquals(MultiLevel.MUTABLE_DV, d.getProperty(Property.IMMUTABLE));
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(Property.NOT_NULL_EXPRESSION));
                    }
                    if ("1".equals(d.statementId())) {
                        assertEquals("instance type IOException", d.currentValue().toString());
                        assertEquals(MultiLevel.MUTABLE_DV, d.getProperty(Property.IMMUTABLE));
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(Property.NOT_NULL_EXPRESSION));
                    }
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("1.0.1".equals(d.statementId())) {
                        assertEquals("null", d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        assertEquals("instance type boolean?<return value>:null", d.currentValue().toString());
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
                    assertEquals("return=CONDITIONALLY:1",
                            d.statementAnalysis().flowData().getInterruptsFlow().entrySet().stream()
                                    .map(Object::toString).sorted().collect(Collectors.joining(",")));
                }
            }
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo ioException = typeMap.get(IOException.class);
            TypeAnalysis ioExceptionAnalysis = ioException.typeAnalysis.get();
            assertEquals(MultiLevel.MUTABLE_DV, ioExceptionAnalysis.getProperty(Property.IMMUTABLE));
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
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.currentValue().getProperty(d.context(), Property.NOT_NULL_EXPRESSION, true));
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(Property.NOT_NULL_EXPRESSION));
                    }
                }
                if ("ioe".equals(d.variableName())) {
                    if ("1.1.0".equals(d.statementId())) {
                        assertEquals("e", d.currentValue().toString());
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(Property.NOT_NULL_EXPRESSION));
                    }
                    if ("2".equals(d.statementId())) {
                        assertEquals("instance type IOException", d.currentValue().toString());
                    }
                }
            }
        };
        // 1 error: overwriting assignment to ioe
        testClass("VariableScope_4", 1, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_5() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("qualification".equals(d.variableName())) {
                    if ("1".equals(d.statementId())) {
                        String expected = d.iteration() <= 2 ? "<new:QualificationImpl>" : "new QualificationImpl()";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, DV.FALSE_DV, Property.IDENTITY);
                    }
                    if ("2".equals(d.statementId())) {
                        String expected = d.iteration() <= 2 ? "<new:QualificationImpl>" : "new QualificationImpl()";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 2, DV.FALSE_DV, Property.IDENTITY);
                    }
                }
            }
            if ("accept".equals(d.methodInfo().name)) {
                assertEquals("$1", d.methodInfo().typeInfo.simpleName);
                if ("qualification".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        String expected = d.iteration() <= 2 ? "<new:QualificationImpl>" : "new QualificationImpl()";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, DV.FALSE_DV, Property.IDENTITY);
                    }
                }
            }
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("QualificationImpl".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
            }
            if ("Qualification".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
            }
        };
        testClass("VariableScope_5", 2, 1, new DebugConfiguration.Builder()
                      //  .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                      //  .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().setForceAlphabeticAnalysisInPrimaryType(true).build());
    }

    // interestingly, the problem is independent of the FINAL value (which is FALSE when computing across all methods,
    // and TRUE when not).
    @Test
    public void test_6() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("accept".equals(d.methodInfo().name)) {
                assertEquals("$1", d.methodInfo().typeInfo.simpleName);
                if (d.variable() instanceof FieldReference fr && "allowStar".equals(fr.fieldInfo.name)) {
                    if ("1.0.2.1.0".equals(d.statementId())) {
                        assertEquals("perPackage", fr.scope.toString());
                        assertEquals("false", d.currentValue().toString());
                    }
                    if ("1.0.2".equals(d.statementId())) {
                        assertEquals("perPackage", fr.scope.toString());
                        String expected = switch (d.iteration()) {
                            case 0 -> "<m:addTypeReturnImport>&&<f:allowStar>";
                            case 1 -> "instance type boolean&&<m:addTypeReturnImport>";
                            default -> "instance type boolean&&(new QualificationImpl()).addTypeReturnImport(typeInfo)";
                        };
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        if ("<out of scope:perPackage:1>".equals(fr.scope.toString())) {
                            String expected = switch (d.iteration()) {
                                case 0 -> "!<m:equals>&&null!=<m:packageName>?<m:addTypeReturnImport>&&<f:allowStar>:<f:allowStar>";
                                case 1 -> "!<m:equals>&&null!=<f:packageName>?instance type boolean&&<m:addTypeReturnImport>:instance type boolean";
                                default -> "instance type boolean";
                            };
                            assertEquals(expected, d.currentValue().toString());
                        } else if ("instance type PerPackage".equals(fr.scope.toString())) {
                            assertTrue(d.iteration() >= 2);
                            String expected = "!myPackage.equals(typeInfo.packageName)&&null!=typeInfo.packageName?instance type boolean&&(new QualificationImpl()).addTypeReturnImport(typeInfo):instance type boolean";
                            assertEquals(expected, d.currentValue().toString());
                        } else fail("No other scope possible: " + fr.scope);
                    }
                }
                if ("doImport".equals(d.variableName())) {
                    String expected = d.iteration() <= 1 ? "<m:addTypeReturnImport>" : "(new QualificationImpl()).addTypeReturnImport(typeInfo)";
                    assertEquals(expected, d.currentValue().toString());
                }
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("allowStar".equals(d.fieldInfo().name)) {
                assertDv(d, DV.FALSE_DV, Property.FINAL);
            }
        };
        testClass("VariableScope_6", 1, 0, new DebugConfiguration.Builder()
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder()
                        .setComputeFieldAnalyserAcrossAllMethods(true)
                        .build());
    }
}
