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
import org.e2immu.analyser.analyser.Stage;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.analysis.impl.FieldAnalysisImpl;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.variable.*;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class Test_66_VariableScope extends CommonTestRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(Test_66_VariableScope.class);

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
                        String expect = switch (d.iteration()) {
                            case 0 -> "<v:j>+<m:nextInt>";
                            case 1 -> "j$1.0.2+<m:nextInt>";
                            default -> "instance type int+j$1.0.2";
                        };
                        assertEquals(expect, d.currentValue().toString());
                    } else if ("1.0.2".equals(d.statementId())) {
                        String expect = switch (d.iteration()) {
                            case 0 -> "<loopIsNotEmptyCondition>?<v:j>+<m:nextInt>:0";
                            case 1 -> "instance type int<=9&&instance type int>=0?j$1.0.2+<m:nextInt>:0";
                            default -> "instance type int<=9&&instance type int>=0?instance type int+j$1.0.2:0";
                        };
                        assertEquals(expect, d.currentValue().toString());
                    } else if (!"1.0.3".equals(d.statementId())) {
                        fail(d.statementId()); // no other statements
                    }
                }
                if ("k".equals(d.variableName())) {
                    if ("1.0.3".equals(d.statementId())) {
                        String expect = switch (d.iteration()) {
                            case 0 -> "<loopIsNotEmptyCondition>?<v:j>+<m:nextInt>:0";
                            case 1 -> "instance type int<=9&&instance type int>=0?j$1.0.2+<m:nextInt>:0";
                            default -> "instance type int<=9&&instance type int>=0?instance type int+j$1.0.2:0";
                        };
                        assertEquals(expect, d.currentValue().toString());
                    }
                    if ("2".equals(d.statementId())) {
                        // there should be no j here!
                        String expect = d.iteration() <= 1
                                ? "<loopIsNotEmptyCondition>?<oos:j>+<m:nextInt>:0"
                                : "instance type int<=9&&instance type int>=0?instance type int+(instance type int<=9&&instance type int>=0?instance type int+instance type int:0):0";
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
                        assertDv(d, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                    }
                    if ("1".equals(d.statementId())) {
                        assertEquals("instance type IOException", d.currentValue().toString());
                        assertEquals(MultiLevel.MUTABLE_DV, d.getProperty(Property.IMMUTABLE));
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(Property.NOT_NULL_EXPRESSION));
                        assertDv(d, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
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
                        assertEquals("ioe:1", d.variableInfo().getLinkedVariables().toString());
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
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addTypeMapVisitor(typeMapVisitor)
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

    /*
    problem in iteration 3, statement 1.0.0 of $1.accept, sensitive to which variables are transferred upwards.
     */
    @Test
    public void test_5() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("accept".equals(d.methodInfo().name)) {
                assertEquals("$1", d.methodInfo().typeInfo.simpleName);
                if ("1".equals(d.statementId())) {
                    assertEquals("!myPackage.equals(typeInfo.packageName())&&null!=typeInfo.packageName()",
                            d.evaluationResult().value().toString());
                    assertEquals("", d.evaluationResult().causesOfDelay().toString());
                }
                if ("1.0.0".equals(d.statementId())) {
                    if (d.iteration() >= 3) {
                        assertEquals("", d.evaluationResult().causesOfDelay().toString());
                    }
                    String expected = d.iteration() <= 2 ? "<m:addTypeReturnImport>"
                            : "typeInfo.packageName().startsWith(\"org\")";
                    assertEquals(expected, d.evaluationResult().value().toString());
                }
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("qualification".equals(d.variableName())) {
                    assertEquals("QualificationImpl", d.currentValue().returnType().typeInfo.simpleName);
                    if ("1".equals(d.statementId())) {
                        String expected = d.iteration() <= 2 ? "<new:QualificationImpl>" : "new QualificationImpl()";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, DV.FALSE_DV, Property.IDENTITY);
                    }
                    if ("2".equals(d.statementId())) {
                        String expected = d.iteration() <= 2 ? "<new:QualificationImpl>" : "new QualificationImpl()";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, DV.FALSE_DV, Property.IDENTITY);
                    }
                }
                if (d.variable() instanceof FieldReference fr && "types".equals(fr.fieldInfo.name)) {
                    if ("<out of scope:perPackage:1>".equals(fr.scope.toString())) {
                        if ("2".equals(d.statementId()) || "3".equals(d.statementId())) {
                            String expected = d.iteration() <= 2 ? "<f:types>" : "instance type LinkedList<TypeInfo>";
                            assertEquals(expected, d.currentValue().toString());
                            assertDv(d, 3, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                            mustSeeIteration(d, 5);
                        }
                    } else if ("instance type PerPackage".equals(fr.scope.toString())) {
                        assertTrue(d.iteration() >= 3);
                        assertDv(d, 3, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    } else {
                        fail("Scope " + fr.scope);
                    }
                }
            }
            if ("accept".equals(d.methodInfo().name)) {
                assertEquals("$1", d.methodInfo().typeInfo.simpleName);
                if ("qualification".equals(d.variableName())) {
                    assertEquals("QualificationImpl", d.currentValue().returnType().typeInfo.simpleName);
                    if ("0".equals(d.statementId())) {
                        String expected = d.iteration() <= 2 ? "<new:QualificationImpl>" : "new QualificationImpl()";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, DV.FALSE_DV, Property.IDENTITY);
                    }
                    if ("1.0.0".equals(d.statementId())) {
                        VariableInfo previous = d.variableInfoContainer().getPreviousOrInitial();
                        String expectedPrev = d.iteration() <= 2 ? "<new:QualificationImpl>" : "new QualificationImpl()";
                        assertEquals(expectedPrev, previous.getValue().toString());

                        String expected = d.iteration() <= 2 ? "<new:QualificationImpl>" : "new QualificationImpl()";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 3, DV.FALSE_DV, Property.IDENTITY);
                        assertDv(d, 3, DV.FALSE_DV, Property.CONTEXT_MODIFIED);

                        String linked = switch (d.iteration()) {
                            case 0 -> "NOT_YET_SET";
                            case 1, 2 -> "doImport:-1,typeInfo:-1";
                            default -> "";
                        };
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("1.0.1".equals(d.statementId())) {
                        assertDv(d, 3, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("1".equals(d.statementId())) {
                        assertDv(d, 3, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if ("packageName".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("typeInfo.packageName()", d.currentValue().toString());
                    }
                    if ("1.0.1".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<v:packageName>" : "typeInfo.packageName()";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 3, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("1".equals(d.statementId())) {
                        assertDv(d, 3, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if (d.variable() instanceof ParameterInfo pi && "myPackage".equals(pi.name)) {
                    if ("1.0.0".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<p:myPackage>" : "nullable instance type String";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("0".equals(d.statementId())) {
                        String expected = "nullable instance type String";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("1.0.1".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<p:myPackage>" : "nullable instance type String";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 3, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("1".equals(d.statementId())) {
                        assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if (d.variable() instanceof ParameterInfo pi && "typeInfo".equals(pi.name)) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("nullable instance type TypeInfo/*@Identity*/", d.currentValue().toString());
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("1.0.0".equals(d.statementId())) {
                        String expected = d.iteration() <= 2 ? "<mod:TypeInfo>"
                                : "nullable instance type TypeInfo/*@Identity*/";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 3, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV,
                                Property.CONTEXT_NOT_NULL);
                    }
                    if ("1.0.2.0.0".equals(d.statementId())) {
                        assertDv(d, 3, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if ("doImport".equals(d.variableName())) {
                    assertNotEquals("1", d.statementId());
                    if ("1.0.0".equals(d.statementId())) {
                        String expected = d.iteration() <= 2 ? "<m:addTypeReturnImport>"
                                : "typeInfo.packageName().startsWith(\"org\")";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
                if ("perPackage".equals(d.variableName())) {
                    if ("1.0.1".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<s:PerPackage>" : "instance type PerPackage";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if (d.variable() instanceof FieldReference fr && "types".equals(fr.fieldInfo.name)) {
                    if ("1.0.2".equals(d.statementId())) {
                        assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("1.0.2.0.0".equals(d.statementId())) {
                        assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if ("scope-perPackage:1".equals(d.variableName())) {
                    assertEquals("1", d.statementId());
                    assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                assertEquals(d.iteration() >= 3, d.methodAnalysis().getLastStatement().variableStream()
                        .filter(vi -> !(vi.variable() instanceof ReturnVariable))
                        .peek(vi -> LOGGER.warn("CM of {}: {}", vi.variable(), vi.getProperty(Property.CONTEXT_MODIFIED)))
                        .allMatch(vi -> vi.getProperty(Property.CONTEXT_MODIFIED).isDone()));
            }
            // interface
            if ("addTypeReturnImport".equals(d.methodInfo().name) && "Qualification".equals(d.methodInfo().typeInfo.simpleName)) {
                assertDv(d.p(0), MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
                assertDv(d, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
            }
            // implementation
            if ("addTypeReturnImport".equals(d.methodInfo().name) && "QualificationImpl".equals(d.methodInfo().typeInfo.simpleName)) {
                assertDv(d.p(0), 1, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
                assertDv(d.p(0), 1, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
            }
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("QualificationImpl".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.EFFECTIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
                assertDv(d, 1, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
                assertDv(d, 2, MultiLevel.CONTAINER_DV, Property.CONTAINER);
                assertHc(d, 0, "");
            }
            if ("Qualification".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
                assertDv(d, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
                assertHc(d, 0, "");
            }
            if ("TypeInfo".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
                assertDv(d, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
                assertHc(d, 0, "");
            }
            if ("VariableScope_5".equals(d.typeInfo().simpleName)) {
                assertHc(d, 0, "");
            }
        };
        testClass("VariableScope_5", 2, 1, new DebugConfiguration.Builder()
                        .addEvaluationResultVisitor(evaluationResultVisitor)
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
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
                            case 0 -> "<f:perPackage.allowStar>&&<m:addTypeReturnImport>";
                            case 1, 2 -> "instance type boolean&&<m:addTypeReturnImport>";
                            default -> "instance type boolean&&`typeInfo.packageName`.startsWith(\"org\")";
                        };
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        if ("instance type PerPackage".equals(fr.scope.toString())) {
                            String expected = "!myPackage.equals(typeInfo.packageName)&&null!=typeInfo.packageName?instance type boolean&&(new QualificationImpl()).addTypeReturnImport(typeInfo):instance type boolean";
                            assertEquals(expected, d.currentValue().toString());
                        } else if ("scope-perPackage:1".equals(fr.scope.toString())) {
                            String expected = switch (d.iteration()) {
                                case 0 -> "<null-check>&&!<m:equals>?<dv:scope-perPackage:1.allowStar>&&<m:addTypeReturnImport>:<f:scope-perPackage:1.allowStar>";
                                case 1, 2 -> "!myPackage.equals(`typeInfo.packageName`)&&null!=`typeInfo.packageName`?instance type boolean&&<m:addTypeReturnImport>:instance type boolean";
                                default -> "!myPackage.equals(`typeInfo.packageName`)&&null!=`typeInfo.packageName`?instance type boolean&&`typeInfo.packageName`.startsWith(\"org\"):instance type boolean";
                            };
                            assertEquals(expected, d.currentValue().toString());
                        } else {
                            fail("No other scope possible: " + fr.scope);
                        }
                    }
                }
                if ("doImport".equals(d.variableName())) {
                    String expected = d.iteration() <= 2 ? "<m:addTypeReturnImport>"
                            : "`typeInfo.packageName`.startsWith(\"org\")";
                    assertEquals(expected, d.currentValue().toString());
                }
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("allowStar".equals(d.fieldInfo().name)) {
                assertDv(d, DV.FALSE_DV, Property.FINAL);
            }
        };
        testClass("VariableScope_6", 1, 1, new DebugConfiguration.Builder()
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder()
                        .setComputeFieldAnalyserAcrossAllMethods(true)
                        .build());
    }

    @Test
    public void test_7() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("combine".equals(d.methodInfo().name)) {
                if ("3".equals(d.statementId())) {
                    String expected = d.iteration() < 2 ? "<m:addAll>" : "instance type boolean";
                    assertEquals(expected, d.evaluationResult().value().toString());
                }
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("combine".equals(d.methodInfo().name)) {
                if ("m".equals(d.variableName())) {
                    if ("2".equals(d.statementId())) {
                        String expected = d.iteration() < 2 ? "<s:VariableScope_7>" : "new VariableScope_7()";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("3".equals(d.statementId())) {
                        String expected = d.iteration() < 2 ? "<s:VariableScope_7>" : "new VariableScope_7()";
                        assertEquals(expected, d.currentValue().toString());

                        assertEquals("m.messages:4,other.messages:4,other:4",
                                d.variableInfo().getLinkedVariables().toString());
                    }
                }
                if (d.variable() instanceof FieldReference fr && "messages".equals(fr.fieldInfo.name)) {
                    assertTrue(Set.of("this", "other", "m").contains(fr.scope.toString()));
                    if ("m".equals(fr.scope.toString())) {
                        if ("3".equals(d.statementId())) {
                            String expected = d.iteration() < 2 ? "<f:m.messages>"
                                    : "instance type Set<Message>/*this.size()>=other.messages.size()*/";
                            assertEquals(expected, d.currentValue().toString());
                        }
                    }
                }
            }
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Message".equals(d.typeInfo().simpleName)) {
                assertHc(d, 0, "");
            }
            if ("VariableScope_7".equals(d.typeInfo().simpleName)) {
                assertHc(d, 0, "Message");
            }
        };
        testClass("VariableScope_7", 0, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }

    @Test
    public void test_8() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {

            if ("output2".equals(d.methodInfo().name)) {
                if ("outputBuilder".equals(d.variableName())) {
                    String expected2 = d.iteration() <= 2 ? "<new:OutputBuilder>" : "instance type OutputBuilder";
                    if ("2.0.0.0.1".equals(d.statementId())) {
                        assertEquals(expected2, d.currentValue().toString());
                    }
                    if ("2.0.0.0.2".equals(d.statementId())) {
                        assertEquals(expected2, d.currentValue().toString());
                        assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("3".equals(d.statementId())) {
                        assertDv(d, 3, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                        String expected = d.iteration() <= 2
                                ? "<new:OutputBuilder>"
                                : "!(object instanceof MethodCall)||null==object?new OutputBuilder(new LinkedList<>()):instance type OutputBuilder";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("3".equals(d.statementId())) {
                        assertCurrentValue(d, 3, "!(object instanceof MethodCall)||null==object?new OutputBuilder(new LinkedList<>()):instance type OutputBuilder");
                    }
                }
                if (d.variable() instanceof FieldReference fr && "object".equals(fr.fieldInfo.name)) {
                    assertEquals("this", fr.scope.toString());
                    if ("2".equals(d.statementId())) {
                        assertDv(d, 17, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if ("methodCall".equals(d.variableName())) {
                    if ("2.0.0".equals(d.statementId())) {

                        // evaluation
                        assertFalse(d.variableInfoContainer().hasMerge());
                        assertTrue(d.variableInfoContainer().hasEvaluation());
                        assertCurrentValue(d, 17, "object/*(MethodCall)*/");
                    }
                    assertNotEquals("2", d.statementId());
                    assertNotEquals("3", d.statementId());
                }
                if ("gg".equals(d.variableName())) {
                    if ("2.0.0.0.0".equals(d.statementId())) {
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("2.0.0.0.1".equals(d.statementId())) {
                        // recursive method call, parameter is not modified by default
                        String linked = d.iteration() < 17
                                ? "guideGenerator:0,methodCall:-1,outputBuilder:-1,qualification:-1,this.object:-1,this:-1"
                                : "guideGenerator:0";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, 17, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("2.0.0.0.2".equals(d.statementId())) {
                        String linked = d.iteration() < 17
                                ? "guideGenerator:0,methodCall:-1,outputBuilder:-1,qualification:-1,this.object:-1,this:-1"
                                : "guideGenerator:0,outputBuilder:4";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, 17, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if (d.variable() instanceof ParameterInfo pi && "guideGenerator".equals(pi.name)) {
                    if ("2.0.0.0.0".equals(d.statementId())) {
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("2.0.0.0.1".equals(d.statementId())) {
                        // recursive method call, parameter is not modified by default
                        String linked = d.iteration() < 17
                                ? "gg:0,methodCall:-1,outputBuilder:-1,qualification:-1,this.object:-1,this:-1"
                                : "gg:0";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, 17, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("2.0.0.0.2".equals(d.statementId())) {
                        String linked = d.iteration() < 17
                                ? "gg:0,methodCall:-1,outputBuilder:-1,qualification:-1,this.object:-1,this:-1"
                                : "gg:0,outputBuilder:4";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, 17, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }

            if ("valueOf".equals(d.methodInfo().name) && "Required".equals(d.methodInfo().typeInfo.simpleName)) {
                if (d.variable() instanceof ParameterInfo pi && "name".equals(pi.name)) {
                    assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                }
            }

            if ("outputInParenthesis".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    // iteration 7 == breaking delay
                    if ("0.0.0".equals(d.statementId())) {
                        String linked = switch (d.iteration()) {
                            case 0 -> "Symbol.LEFT_PARENTHESIS:-1,Symbol.RIGHT_PARENTHESIS:-1,expression:-1,precedence:-1,qualification:-1";
                            case 1, 2, 3 -> "Symbol.LEFT_PARENTHESIS:-1,Symbol.RIGHT_PARENTHESIS:-1,expression:-1,qualification:-1";
                            default -> "";
                        };
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("valueOf".equals(d.methodInfo().name) && "Required".equals(d.methodInfo().typeInfo.simpleName)) {
                assertEquals("0", d.statementId());
                String expected = d.iteration() == 0
                        ? "name={context-modified=link@NOT_YET_SET, context-not-null=nullable:1, read=true:1}, this={context-modified=link@NOT_YET_SET}"
                        : "name={context-modified=false:0, context-not-null=nullable:1, read=true:1}, this={context-modified=false:0}";
                assertEquals(expected, d.statementAnalysis().propertiesFromSubAnalysersSortedToString());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                assertEquals("OutputBuilder", d.methodInfo().typeInfo.simpleName);
                assertDv(d.p(0), 1, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d.p(0), 1, MultiLevel.INDEPENDENT_HC_DV, Property.INDEPENDENT);
                assertDv(d, 1, DV.TRUE_DV, Property.MODIFIED_METHOD);
            }
            if ("mid".equals(d.methodInfo().name)) {
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("output".equals(d.methodInfo().name) && "MethodCall".equals(d.methodInfo().typeInfo.simpleName)) {
                assertDv(d, 17, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("output2".equals(d.methodInfo().name)) {
                assertDv(d, 17, DV.FALSE_DV, Property.MODIFIED_METHOD);
                String expected = d.iteration() < 17 ? "<m:output2>"
                        : "/*inline output2*/!(object instanceof MethodCall)||null==object?new OutputBuilder(new LinkedList<>()):instance type OutputBuilder";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());

                assertDv(d.p(0), 18, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d.p(1), 18, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
            }
            if ("valueOf".equals(d.methodInfo().name) && "Required".equals(d.methodInfo().typeInfo.simpleName)) {
                assertDv(d.p(0), 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("MethodCall".equals(d.typeInfo().simpleName)) {
                assertDv(d, 16, MultiLevel.EFFECTIVELY_FINAL_FIELDS_DV, Property.IMMUTABLE);
            }
        };

        testClass("VariableScope_8", 0, 6, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());

    }

    // with the recursive call, the second call commented out
    @Test
    public void test_8_1() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {

            if ("output2".equals(d.methodInfo().name)) {
                if ("outputBuilder".equals(d.variableName())) {
                    if ("2.0.0.0.1".equals(d.statementId())) {
                        String expected = d.iteration() < 3 ? "<new:OutputBuilder>" : "instance type OutputBuilder";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("3".equals(d.statementId())) {
                        assertDv(d, 3, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                        assertCurrentValue(d, 3, "!(object instanceof MethodCall)||null==object?new OutputBuilder(new LinkedList<>()):instance type OutputBuilder");
                    }
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("3".equals(d.statementId())) {
                        assertCurrentValue(d, 3, "!(object instanceof MethodCall)||null==object?new OutputBuilder(new LinkedList<>()):instance type OutputBuilder");
                    }
                }
                if (d.variable() instanceof FieldReference fr && "object".equals(fr.fieldInfo.name)) {
                    assertEquals("this", fr.scope.toString());
                    if ("2".equals(d.statementId())) {
                        assertDv(d, 17, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("output2".equals(d.methodInfo().name)) {
                assertDv(d, 17, DV.FALSE_DV, Property.MODIFIED_METHOD);
                String expected = d.iteration() < 17 ? "<m:output2>"
                        : "/*inline output2*/!(object instanceof MethodCall)||null==object?new OutputBuilder(new LinkedList<>()):instance type OutputBuilder";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                assertDv(d.p(0), 18, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d.p(1), 18, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
            }
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("MethodCall".equals(d.typeInfo().simpleName)) {
                assertDv(d, 16, MultiLevel.EFFECTIVELY_FINAL_FIELDS_DV, Property.IMMUTABLE);
            }
        };
        testClass("VariableScope_8_1", 0, DONT_CARE, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }

    // first (recursive) call commented out
    @Test
    public void test_8_2() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("output2".equals(d.methodInfo().name)) {
                if ("gg".equals(d.variableName())) {
                    if ("2.0.0.0.1".equals(d.statementId())) {
                        String expected = d.iteration() < 3 ? "<c:boolean>?<m:defaultGuideGenerator>:guideGenerator"
                                : "null==guideGenerator?new GuideGenerator(){public OutputElement start(){return null;}public OutputElement end(){return null;}public OutputElement mid(){return null;}}:guideGenerator";
                        assertEquals(expected, d.currentValue().toString());
                        String linked =switch( d.iteration()) {
                            case 0 -> "guideGenerator:0,methodCall:-1,outputBuilder:-1,qualification:-1,this.object:-1";
                            case 1 -> "guideGenerator:0,outputBuilder:-1";
                            default -> "guideGenerator:0,outputBuilder:2";
                        };
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                }
                if ("outputBuilder".equals(d.variableName())) {
                    if ("2.0.0.0.1".equals(d.statementId())) {
                        assertCurrentValue(d, 3, "instance type OutputBuilder");
                    }
                }
                if (d.variable() instanceof ParameterInfo pi && "guideGenerator".equals(pi.name)) {
                    if ("2.0.0.0.1".equals(d.statementId())) {
                        assertCurrentValue(d, 1, "nullable instance type GuideGenerator");
                    }
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("output2".equals(d.methodInfo().name)) {
                assertDv(d, 2, DV.FALSE_DV, Property.MODIFIED_METHOD);
                String expected = d.iteration() < 3 ? "<m:output2>"
                        : "/*inline output2*/!(object instanceof MethodCall)||null==object?new OutputBuilder(new LinkedList<>()):instance type OutputBuilder";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                assertDv(d.p(0), 1, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d.p(1), 3, DV.TRUE_DV, Property.MODIFIED_VARIABLE);
            }
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("MethodCall".equals(d.typeInfo().simpleName)) {
                assertDv(d, 3, MultiLevel.EFFECTIVELY_FINAL_FIELDS_DV, Property.IMMUTABLE);
            }
        };
        testClass("VariableScope_8_2", 1, DONT_CARE, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }

    @Test
    public void test_10() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("removeInSubBlockMerge".equals(d.methodInfo().name) && "VariableDefinedOutsideLoop".equals(d.methodInfo().typeInfo.simpleName)) {
                if ("1".equals(d.statementId())) {
                    String expected = d.iteration() == 0 ? "<instanceOf:VariableDefinedOutsideLoop>&&<m:startsWith>"
                            : "vn$1/*(VariableDefinedOutsideLoop)*/.statementIndex.startsWith(index+\".\")&&vn$1 instanceof VariableDefinedOutsideLoop";
                    assertEquals(expected, d.evaluationResult().getExpression().toString());
                    assertEquals(d.iteration() == 0, d.evaluationResult().causesOfDelay().isDelayed());
                }
                if ("2".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0 -> "<null-check>&&<instanceOf:VariableDefinedOutsideLoop>&&<m:startsWith>&&<dv:scope-vdol:1.previousVariableNature>!=this";
                        case 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11 -> "scope-vdol:1.statementIndex.startsWith(index+\".\")&&<null-check>&&vn$1 instanceof VariableDefinedOutsideLoop&&<dv:scope-vdol:1.previousVariableNature>!=this";
                        default -> "scope-vdol:1.statementIndex.startsWith(index+\".\")&&vn$1 instanceof VariableDefinedOutsideLoop&&scope-vdol:1.previousVariableNature!=this";
                    };
                    assertEquals(expected, d.evaluationResult().getExpression().toString());
                }
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("removeInSubBlockMerge".equals(d.methodInfo().name) && "VariableDefinedOutsideLoop".equals(d.methodInfo().typeInfo.simpleName)) {
                if ("vn".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("this", d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        assertTrue(d.variableInfoContainer().hasEvaluation());
                        String eval = d.iteration() == 0 ? "<vl:vn>" : "nullable instance type VariableScope_10";
                        assertEquals(eval, d.variableInfoContainer().best(Stage.EVALUATION).getValue().toString());
                        assertTrue(d.variableInfoContainer().hasMerge());
                        String merge = switch (d.iteration()) {
                            case 0 -> "<instanceOf:VariableDefinedOutsideLoop>&&<m:startsWith>?<dv:scope-vdol:1.previousVariableNature>:this";
                            case 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11 -> "scope-vdol:1.statementIndex.startsWith(index+\".\")&&vn$1 instanceof VariableDefinedOutsideLoop?<dv:scope-vdol:1.previousVariableNature>:this";
                            default -> "scope-vdol:1.statementIndex.startsWith(index+\".\")&&vn$1 instanceof VariableDefinedOutsideLoop?scope-vdol:1.previousVariableNature:this";
                        };
                        assertEquals(merge, d.currentValue().toString());
                        List<Variable> variables = d.currentValue().variables(true);
                        assertFalse(variables.toString().contains("vdol.statementIndex"));
                    }
                }
                if ("vdol".equals(d.variableName())) {
                    if ("1".equals(d.statementId())) {
                        assertTrue(d.variableInfoContainer().variableNature() instanceof VariableNature.Pattern);
                        assertFalse(d.variableInfoContainer().hasMerge());

                        // initial
                        VariableInfo viInit = d.variableInfoContainer().getPreviousOrInitial();
                        assertEquals("<not yet assigned>", viInit.getValue().toString());

                        // eval
                        String expected = switch (d.iteration()) {
                            case 0 -> "<v:vn>/*(VariableDefinedOutsideLoop)*/";
                            case 1, 2 -> "<vp:vn:cm@Parameter_index;cm@Parameter_previousVariableNature;cm@Parameter_statementIndex;constructor-to-instance@Method_removeInSubBlockMerge_2.0.0-E;initial:index@Method_removeInSubBlockMerge_1-E;initial:vn@Method_removeInSubBlockMerge_1-C;initial@Field_previousVariableNature;mom@Parameter_previousVariableNature;mom@Parameter_statementIndex>/*(VariableDefinedOutsideLoop)*/";
                            case 3, 4, 5, 6, 7, 8, 9, 10, 11 -> "<vp:vn:break_mom_delay@Parameter_previousVariableNature;cm@Parameter_index;cm@Parameter_previousVariableNature;cm@Parameter_statementIndex;constructor-to-instance@Method_removeInSubBlockMerge_2.0.0-E;initial:index@Method_removeInSubBlockMerge_1-E;initial:vn@Method_removeInSubBlockMerge_1-C;initial@Field_previousVariableNature;mom@Parameter_previousVariableNature;mom@Parameter_statementIndex>/*(VariableDefinedOutsideLoop)*/";
                            default -> "vn$1/*(VariableDefinedOutsideLoop)*/";
                        };
                        assertEquals(expected, d.currentValue().toString());
                    }
                    // variable should not exist further than 1...
                    assertTrue(d.statementId().compareTo("2") < 0, "Found in " + d.statementId());
                }
                if (d.variable() instanceof FieldReference fr && "statementIndex".equals(fr.fieldInfo.name)) {
                    if ("vdol".equals(fr.scope.toString())) {
                        assertTrue(d.statementId().compareTo("2") < 0);
                        String expected = d.iteration() == 0 ? "<f:vdol.statementIndex>" : "nullable instance type String";
                        assertEquals(expected, d.currentValue().toString());
                    } else if ("scope-vdol:1".equals(fr.scope.toString())) {
                        assertNotNull(fr.scopeVariable);
                        if (fr.scopeVariable.variableNature() instanceof VariableNature.ScopeVariable sv) {
                            assertEquals("1", sv.getIndexCreatedInMerge());
                            assertEquals("1~", sv.getBeyondIndex());
                        } else fail();
                        assertNotEquals("1.0.0", d.statementId());
                        String expected = d.iteration() == 0 ? "<f:scope-vdol:1.statementIndex>" : "nullable instance type String";
                        assertEquals(expected, d.currentValue().toString());
                    } else fail("? scope " + fr.scope);
                    if ("1".equals(d.statementId())) {
                        assertTrue(d.variableInfoContainer().variableNature() instanceof VariableNature.NormalLocalVariable);
                    }
                }
                if (d.variable() instanceof FieldReference fr && "previousVariableNature".equals(fr.fieldInfo.name)) {
                    if ("1".equals(d.statementId())) {
                        if ("scope-vdol:1".equals(fr.scope.toString())) {
                            assertTrue(d.variableInfoContainer().variableNature() instanceof VariableNature.NormalLocalVariable,
                                    "is " + d.variableInfoContainer().variableNature().getClass());
                            assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                        } else fail("Have scope " + fr.scope);
                    }
                    if ("2".equals(d.statementId())) {
                        assertEquals("scope-vdol:1", fr.scope.toString());
                    }
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("removeInSubBlockMerge".equals(d.methodInfo().name) && "VariableDefinedOutsideLoop".equals(d.methodInfo().typeInfo.simpleName)) {
                if ("1".equals(d.statementId())) {
                    String expected = d.iteration() == 0 ? "CM{state=!<instanceOf:VariableDefinedOutsideLoop>||!<m:startsWith>;parent=CM{}}"
                            : "CM{state=!scope-vdol:1.statementIndex.startsWith(index+\".\")||!(vn instanceof VariableDefinedOutsideLoop);parent=CM{}}";
                    assertEquals(expected, d.conditionManagerForNextStatement().toString());
                }
                if ("2".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0 -> "CM{state=(!<instanceOf:VariableDefinedOutsideLoop>||!<m:startsWith>)&&(!<null-check>||!<instanceOf:VariableDefinedOutsideLoop>||!<m:startsWith>||<dv:scope-vdol:1.previousVariableNature>==this);parent=CM{}}";
                        case 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11 -> "CM{state=(!scope-vdol:1.statementIndex.startsWith(index+\".\")||!<null-check>||!(vn$1 instanceof VariableDefinedOutsideLoop)||<dv:scope-vdol:1.previousVariableNature>==this)&&(!scope-vdol:1.statementIndex.startsWith(index+\".\")||!(vn instanceof VariableDefinedOutsideLoop));parent=CM{}}";
                        default -> "CM{state=(!scope-vdol:1.statementIndex.startsWith(index+\".\")||!(vn instanceof VariableDefinedOutsideLoop))&&(!scope-vdol:1.statementIndex.startsWith(index+\".\")||!(vn$1 instanceof VariableDefinedOutsideLoop)||scope-vdol:1.previousVariableNature==this);parent=CM{}}";
                    };
                    assertEquals(expected, d.conditionManagerForNextStatement().toString());
                }
            }
        };
        testClass("VariableScope_10", 0, 2, new DebugConfiguration.Builder()
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .addEvaluationResultVisitor(evaluationResultVisitor)
                        .build(),
                new AnalyserConfiguration.Builder()
                        .setComputeFieldAnalyserAcrossAllMethods(true)
                        .setForceAlphabeticAnalysisInPrimaryType(true)
                        .build());
    }


    @Test
    public void test_11() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    if ("0.0.0".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "s.length()==<f:x.i>?<f:x.i>:<return value>"
                                : "s.length()==x.i?s.length()/*{L i:0,x:3}*/:<return value>";
                        assertEquals(expected, d.currentValue().toString());
                        String linked = d.iteration() == 0 ? "x.i:0,x:-1,xs:-1" : "x.i:0,x:2";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("0".equals(d.statementId())) {
                        String expected = d.iteration() == 0
                                ? "xs.isEmpty()||s.length()!=<dv:scope-x:0.i>?<return value>:<dv:scope-x:0.i>"
                                : "xs.isEmpty()||s.length()!=scope-x:0.i?<return value>:s.length()/*{L i:0,x:3}*/";
                        assertEquals(expected, d.currentValue().toString());
                        String linked = d.iteration() == 0 ? "scope-x:0.i:0,scope-x:0:-1,xs:-1" : "scope-x:0.i:0,scope-x:0:2";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        String expected = d.iteration() == 0
                                ? "<m:isEmpty>||<dv:scope-x:0.i>!=<m:length>?0:<dv:scope-x:0.i>"
                                : "xs.isEmpty()||s.length()!=scope-x:0.i?0:s.length()/*{L i:0,x:3}*/";
                        assertEquals(expected, d.currentValue().toString());
                        String linked = d.iteration() == 0 ? "s:-1,scope-x:0.i:0,scope-x:0:-1,xs:-1" : "scope-x:0.i:0,scope-x:0:2";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                }
                if (d.variable() instanceof FieldReference fr && "i".equals(fr.fieldInfo.name)) {
                    if ("x".equals(fr.scope.toString())) {
                        if ("0.0.0.0.0".equals(d.statementId())) {
                            String expected = d.iteration() == 0 ? "<f:x.i>" : "instance type int";
                            assertEquals(expected, d.currentValue().toString());
                        }
                        if ("0.0.0".equals(d.statementId())) {
                            String expected = d.iteration() == 0 ? "<f:x.i>" : "instance type int";
                            assertEquals(expected, d.currentValue().toString());
                        }
                        if ("0".equals(d.statementId())) {
                            fail("Should not exist here");
                        }
                    } else if ("scope-x:0".equals(fr.scope.toString())) {
                        assertNotNull(fr.scopeVariable);
                        assertEquals("scope-x:0", fr.scopeVariable.fullyQualifiedName());
                        if ("0".equals(d.statementId())) {
                            String expected = d.iteration() == 0 ? "xs.isEmpty()?<f:scope-x:0.i>:<dv:scope-x:0.i>"
                                    : "instance type int";
                            assertEquals(expected, d.currentValue().toString());
                        }
                    } else fail("Scope " + fr.scope);
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    String expected = d.iteration() == 0
                            ? "CM{state=xs.isEmpty()||s.length()!=<dv:scope-x:0.i>;parent=CM{}}"
                            : "CM{state=xs.isEmpty()||s.length()!=scope-x:0.i;parent=CM{}}";
                    assertEquals(expected, d.statementAnalysis().stateData().getConditionManagerForNextStatement().toString());
                }
            }
        };
        testClass("VariableScope_11", 0, 0, new DebugConfiguration.Builder()
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder()
                        .setComputeFieldAnalyserAcrossAllMethods(true)
                        .build());
    }

    @Test
    public void test_12() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    if ("0.0.0".equals(d.statementId())) {
                        String expected = d.iteration() <= 1 ? "s.length()==<f:x.i>?<f:x.i>:<return value>"
                                : "s.length()==y/*(X)*/.i$0?s.length()/*{L i:0,x:3}*/:<return value>";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("0".equals(d.statementId())) {
                        String expected = d.iteration() <= 1
                                ? "y instanceof X&&null!=y&&s.length()==<dv:scope-x:0.i>?<dv:scope-x:0.i>:<return value>"
                                : "y instanceof X&&null!=y&&s.length()==scope-x:0.i?s.length()/*{L i:0,x:3}*/:<return value>";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        String expected = d.iteration() <= 1
                                ? "y instanceof X&&null!=y&&<dv:scope-x:0.i>==<m:length>?<dv:scope-x:0.i>:0"
                                : "y instanceof X&&null!=y&&s.length()==y/*(X)*/.i$1?s.length()/*{L i:0,x:3}*/:0";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
                if (d.variable() instanceof FieldReference fr && "i".equals(fr.fieldInfo.name)) {
                    if ("x".equals(fr.scope.toString())) {
                        if ("0.0.0.0.0".equals(d.statementId())) {
                            String expected = d.iteration() <= 1 ? "<f:x.i>" : "instance type int";
                            assertEquals(expected, d.currentValue().toString());
                        }
                        if ("0.0.0".equals(d.statementId())) {
                            String expected = switch (d.iteration()) {
                                case 0 -> "<f:x.i>";
                                case 1 -> "s.length()==instance type int?<f:x.i>:instance type int";
                                // myself is recognized in EvaluateInlineConditional
                                default -> "instance type int";
                            };
                            assertEquals(expected, d.currentValue().toString());
                        }
                        if ("0".equals(d.statementId())) {
                            fail("Should not exist here");
                        }
                    } else if ("scope-x:0".equals(fr.scope.toString())) {
                        assertNotNull(fr.scopeVariable);
                        assertEquals("scope-x:0", fr.scopeVariable.fullyQualifiedName());
                        if ("0".equals(d.statementId())) {
                            String expected = switch (d.iteration()) {
                                case 0 -> "y instanceof X&&null!=y?<dv:scope-x:0.i>:<f:scope-x:0.i>";
                                case 1 -> "y instanceof X&&null!=y&&s.length()==instance type int?<dv:scope-x:0.i>:instance type int";
                                // myself is recognized in EvaluateInlineConditional
                                default -> "instance type int";
                            };
                            assertEquals(expected, d.currentValue().toString());
                        }
                    } else fail("Scope " + fr.scope);
                }
                if ("scope-x:0".equals(d.variableName())) {
                    if (d.variable() instanceof LocalVariableReference lvr) {
                        if (lvr.variableNature() instanceof VariableNature.ScopeVariable sv) {
                            assertEquals("0~", sv.getBeyondIndex());
                        } else fail();
                    } else fail();
                    assertFalse(d.statementId().startsWith("0."), "in " + d.statementId());
                    if ("0".equals(d.statementId())) {
                        String expected = d.iteration() <= 1 ? "<oos:x>" : "y/*(X)*/";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertEquals("CM{parent=CM{}}", d.statementAnalysis().stateData().getConditionManagerForNextStatement().toString());
                }
            }
        };
        testClass("VariableScope_12", 0, 0, new DebugConfiguration.Builder()
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder()
                        .setComputeFieldAnalyserAcrossAllMethods(true)
                        .build());
    }


    @Test
    public void test_13() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    String expected = d.iteration() == 0
                            ? "y instanceof X&&null!=y&&s.length()==<dv:scope-x:0.i>?<dv:scope-x:0.i>:3"
                            : "y instanceof X&&null!=y&&s.length()==scope-x:0.i?scope-x:0.i:3";
                    assertEquals(expected, d.evaluationResult().value().toString());
                }
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                String expected = switch (d.iteration()) {
                    case 0 -> "<vp:y:container@Class_X>/*(X)*/";
                    case 1 -> "<vp:y:final@Field_i>/*(X)*/";
                    default -> "y/*(X)*/";
                };
                if ("scope-x:0".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
                if ("x".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertEquals(expected, d.currentValue().toString());
                    } else if ("2".equals(d.statementId())) {
                        String expected1 = switch (d.iteration()) {
                            case 0 -> "<p:y>/*(X)*/";
                            case 1 -> "<vp:y:final@Field_i>/*(X)*/";
                            default -> "y/*(X)*/";
                        };
                        assertEquals(expected1, d.currentValue().toString());
                    } else fail("x should only exist in statements 0 and 2");
                }
                if (d.variable() instanceof FieldReference fr && "i".equals(fr.fieldInfo.name)) {
                    if ("x".equals(fr.scope.toString())) {
                        if ("0".equals(d.statementId())) {
                            String v = d.iteration() == 0 ? "<f:x.i>" : "instance type int";
                            assertEquals(v, d.currentValue().toString());
                        }
                    } else if ("scope-x:0".equals(fr.scope.toString())) {
                        String v = d.iteration() == 0 ? "<f:scope-x:0.i>" : "instance type int";
                        assertEquals(v, d.currentValue().toString());
                    } else if ("scope-x:2".equals(fr.scope.toString())) {
                        String v = d.iteration() == 0 ? "<f:scope-x:2.i>" : "instance type int";
                        assertEquals(v, d.currentValue().toString());
                    } else fail("Scope " + fr.scope);
                }
            }
        };
        testClass("VariableScope_13", 0, 0, new DebugConfiguration.Builder()
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addEvaluationResultVisitor(evaluationResultVisitor)
                        .build(),
                new AnalyserConfiguration.Builder()
                        .setComputeFieldAnalyserAcrossAllMethods(true)
                        .build());
    }

    @Test
    public void test_14() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("k".equals(d.variableName())) {
                    assertCurrentValue(d, 1, "y instanceof X&&null!=y&&s.length()==scope-x:0.i?scope-x:0.i:3");
                }
                if ("scope-x:0".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertCurrentValue(d, 2, "y/*(X)*/");
                    }
                }
                if ("x".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertCurrentValue(d, 2, "y/*(X)*/");
                    } else fail("x should only exist in statement 0");
                }
                if ("scope-z:2".equals(d.variableName())) {
                    if ("2".equals(d.statementId())) {
                        assertCurrentValue(d, 2, "y/*(X)*/");
                    }
                }
                if ("z".equals(d.variableName())) {
                    if ("2".equals(d.statementId())) {
                        assertCurrentValue(d, 2, "y/*(X)*/");
                        assertFalse(d.variableInfoContainer().hasMerge());
                    } else fail("z should only exist in statement 2");
                }
                if (d.variable() instanceof FieldReference fr && "i".equals(fr.fieldInfo.name)) {
                    if ("x".equals(fr.scope.toString())) {
                        if ("0".equals(d.statementId())) {
                            String v = d.iteration() == 0 ? "<f:x.i>" : "instance type int";
                            assertEquals(v, d.currentValue().toString());
                        } else fail("x.i should only exist in statement 0");
                    } else if ("scope-x:0".equals(fr.scope.toString())) {
                        String v = d.iteration() == 0 ? "<f:scope-x:0.i>" : "instance type int";
                        assertEquals(v, d.currentValue().toString());
                    } else if ("z".equals(fr.scope.toString())) {
                        if ("2".equals(d.statementId())) {
                            String v = d.iteration() == 0 ? "<f:z.i>" : "instance type int";
                            assertEquals(v, d.currentValue().toString());
                        } else fail("z.i should only exist in statement 0");
                    } else if ("scope-z:2".equals(fr.scope.toString())) {
                        if ("2".equals(d.statementId())) {
                            String v = d.iteration() == 0 ? "<f:scope-z:2.i>" : "instance type int";
                            assertEquals(v, d.currentValue().toString());
                        }
                    } else fail("Scope " + fr.scope);
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("i".equals(d.fieldInfo().name)) {
                FieldAnalysisImpl.Builder b = ((FieldAnalysisImpl.Builder) d.fieldAnalysis());
                assertEquals("0,i", b.sortedValuesString());
                assertEquals("", b.valuesDelayed().toString());
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("X".equals(d.typeInfo().simpleName)) {
                assertDv(d, 1, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
                assertDv(d, MultiLevel.CONTAINER_DV, Property.CONTAINER);
            }
        };

        testClass("VariableScope_14", 0, 0, new DebugConfiguration.Builder()
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                        .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder()
                        .setComputeFieldAnalyserAcrossAllMethods(true)
                        .build());
    }
}
