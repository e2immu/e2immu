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
                        String expect = d.iteration() == 0
                                ? "<loopIsNotEmptyCondition>?<oos:j>+<m:nextInt>:<oos:j>"
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
                            : "(new QualificationImpl()).addTypeReturnImport(typeInfo)";
                    assertEquals(expected, d.evaluationResult().value().toString());
                }
            }
        };
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
                    if ("0".equals(d.statementId())) {
                        String expected = "<new:QualificationImpl>";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, DV.FALSE_DV, Property.IDENTITY);
                        assertTrue(d.iteration() <= 2);
                    }
                    if ("1.0.0".equals(d.statementId())) {
                        VariableInfo previous = d.variableInfoContainer().getPreviousOrInitial();
                        String expectedPrev = d.iteration() <= 2 ? "<new:QualificationImpl>" : "new QualificationImpl()";
                        assertEquals(expectedPrev, previous.getValue().toString());

                        String expected = d.iteration() <= 2 ? "<new:QualificationImpl>" : "new QualificationImpl()";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 3, DV.FALSE_DV, Property.IDENTITY);
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("1.0.1".equals(d.statementId())) {
                        assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("1".equals(d.statementId())) {
                        assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if ("packageName".equals(d.variableName())) {
                    if ("0".equals(d.statementId()) || "1.0.0".equals(d.statementId())) {
                        assertEquals("typeInfo.packageName()", d.currentValue().toString());
                    }
                    if ("1.0.1".equals(d.statementId())) {
                        String expected = d.iteration() <= 1 ? "<v:packageName>" : "typeInfo.packageName()";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("1".equals(d.statementId())) {
                        assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if (d.variable() instanceof ParameterInfo pi && "myPackage".equals(pi.name)) {
                    if ("0".equals(d.statementId()) || "1.0.0".equals(d.statementId())) {
                        assertEquals("nullable instance type String", d.currentValue().toString());
                    }
                    if ("1.0.1".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<p:myPackage>" : "nullable instance type String";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("1".equals(d.statementId())) {
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if (d.variable() instanceof ParameterInfo pi && "typeInfo".equals(pi.name)) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("nullable instance type TypeInfo/*@Identity*/", d.currentValue().toString());
                    }
                    if ("1.0.0".equals(d.statementId())) {
                        String expected = d.iteration() <= 2 ? "<p:typeInfo>" : "nullable instance type TypeInfo/*@Identity*/";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("1".equals(d.statementId())) {
                        assertDv(d, 2, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if ("doImport".equals(d.variableName())) {
                    assertNotEquals("1", d.statementId());
                    if ("1.0.0".equals(d.statementId())) {
                        String expected = d.iteration() <= 2 ? "<m:addTypeReturnImport>" : "(new QualificationImpl()).addTypeReturnImport(typeInfo)";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
                if ("perPackage".equals(d.variableName())) {
                    if ("1.0.1".equals(d.statementId())) {
                        String expected = d.iteration() <= 1 ? "<m:computeIfAbsent>" : "instance type PerPackage";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if (d.variable() instanceof FieldReference fr && "types".equals(fr.fieldInfo.name)) {
                    if ("1.0.2.0.0".equals(d.statementId()) || "1.0.2".equals(d.statementId())) {
                        assertDv(d, 2, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                assertEquals(d.iteration() >= 2, d.methodAnalysis().getLastStatement().variableStream()
                        .peek(vi -> LOGGER.warn("CM of {}: {}", vi.variable(), vi.getProperty(Property.CONTEXT_MODIFIED)))
                        .allMatch(vi -> vi.getProperty(Property.CONTEXT_MODIFIED).isDone()));
            }
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("QualificationImpl".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
                assertDv(d, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
                assertDv(d, 2, MultiLevel.CONTAINER_DV, Property.CONTAINER);
            }
            if ("Qualification".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
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
                            case 0 -> "<m:addTypeReturnImport>&&<f:allowStar>";
                            case 1 -> "instance type boolean&&<m:addTypeReturnImport>";
                            default -> "instance type boolean&&(new QualificationImpl()).addTypeReturnImport(typeInfo)";
                        };
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        if ("instance type PerPackage".equals(fr.scope.toString())) {
                            String expected = "!myPackage.equals(typeInfo.packageName)&&null!=typeInfo.packageName?instance type boolean&&(new QualificationImpl()).addTypeReturnImport(typeInfo):instance type boolean";
                            assertEquals(expected, d.currentValue().toString());
                        } else if ("scope-perPackage:1".equals(fr.scope.toString())) {
                            String expected = switch (d.iteration()) {
                                case 0 -> "<null-check>&&!<m:equals>?<m:addTypeReturnImport>&&<f:allowStar>:<f:allowStar>";
                                case 1 -> "!myPackage.equals(`typeInfo.packageName`)&&null!=`typeInfo.packageName`?instance type boolean&&<m:addTypeReturnImport>:instance type boolean";
                                default -> "instance type boolean";
                            };
                            assertEquals(expected, d.currentValue().toString());
                        } else {
                            fail("No other scope possible: " + fr.scope);
                        }
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

    @Test
    public void test_7() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("combine".equals(d.methodInfo().name)) {
                if ("3".equals(d.statementId())) {
                    String expected = d.iteration() == 0 ? "<m:addAll>" : "instance type boolean";
                    assertEquals(expected, d.evaluationResult().value().toString());
                }
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("combine".equals(d.methodInfo().name)) {
                if ("m".equals(d.variableName())) {
                    String expected = d.iteration() == 0 ? "<s:VariableScope_7>" : "new VariableScope_7()";
                    assertEquals(expected, d.currentValue().toString());
                }
                if (d.variable() instanceof FieldReference fr && "messages".equals(fr.fieldInfo.name)) {
                    assertTrue(Set.of("this", "other", "m").contains(fr.scope.toString()));
                    if ("m".equals(fr.scope.toString())) {
                        if ("3".equals(d.statementId())) {
                            String expected = d.iteration() == 0 ? "<mmc:messages>"
                                    // FIXME why nullable?
                                    : "nullable instance type Set<Message>/*this.size()>=other.messages.size()*/";
                            assertEquals(expected, d.currentValue().toString());
                        }
                    }
                }
            }
        };
        testClass("VariableScope_7", 0, 9, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_8() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("output".equals(d.methodInfo().name)) {
                if ("4.0.1".equals(d.statementId())) {
                    String expected = d.iteration() <= 1 ? "<instanceOf:MethodCall>" : "object instanceof MethodCall";
                    assertEquals(expected, d.evaluationResult().value().toString());
                }
                if ("4.0.1.1.0".equals(d.statementId())) {
                    String expected = d.iteration() <= 1 ? "<instanceOf:TypeExpression>" : "object instanceof TypeExpression";
                    assertEquals(expected, d.evaluationResult().value().toString());
                }
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("output".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "typeInfo".equals(fr.fieldInfo.name)) {
                    if ("4".equals(d.statementId())) {
                        if (d.iteration() < 4) {
                            assertEquals("<out of scope:typeExpression:4.0.1.1.0>", fr.scope.toString());
                        } else {
                            Set<String> scopes = Set.of("<out of scope:typeExpression:4.0.1.1.0>", "object/*(TypeExpression)*/");
                            assertTrue(scopes.contains(fr.scope.toString()), "Was " + fr.scope);
                        }
                    }
                }
                if (d.variable() instanceof This) {
                    if ("4".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0 -> "<out of scope:typeExpression:4.0.1.1.0>.typeInfo:-1,qualification:-1,this.object:-1,this:0";
                            case 1, 2 -> "<out of scope:typeExpression:4.0.1.1.0>.typeInfo:-1,this.object:-1,this:0";
                            case 3 -> "this.object:-1,this:0";
                            default -> "this:0";
                        };
                        assertEquals(expected, d.variableInfo().getLinkedVariables().toString());
                    }
                }
            }
        };
        testClass("VariableScope_8", 4, 15, new DebugConfiguration.Builder()
                //   .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                //   .addEvaluationResultVisitor(evaluationResultVisitor)
                .build());

    }

    @Test
    public void test_9() throws IOException {
        testClass("VariableScope_9", 2, 13, new DebugConfiguration.Builder()

                        .build(),
                new AnalyserConfiguration.Builder()
                        .setComputeFieldAnalyserAcrossAllMethods(true)
                        .setForceAlphabeticAnalysisInPrimaryType(true)
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
                        case 0 -> "<null-check>&&this!=(<instanceOf:VariableDefinedOutsideLoop>&&<m:startsWith>?<f:vdol.previousVariableNature>:<vl:vn>)";
                        case 1 -> "<null-check>&&this!=(vn$1/*(VariableDefinedOutsideLoop)*/.statementIndex.startsWith(index+\".\")&&vn$1 instanceof VariableDefinedOutsideLoop?<oos:previousVariableNature>:nullable instance type VariableScope_10)";
                        default -> "";
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
                            case 0 -> "<instanceOf:VariableDefinedOutsideLoop>&&<m:startsWith>?<f:vdol.previousVariableNature>:<vl:vn>";
                            // FIXME the value vn$1 should not be present here as a self reference, it should have been replaced (value vdol...)
                            case 1, 2, 3 -> "vn$1/*(VariableDefinedOutsideLoop)*/.statementIndex.startsWith(index+\".\")&&vn$1 instanceof VariableDefinedOutsideLoop?<f:vdol.previousVariableNature>:nullable instance type VariableScope_10";
                            default -> "instance type VariableDefinedOutsideLoop.statementIndex.startsWith(index+\".\")&&vn$1 instanceof VariableDefinedOutsideLoop?instance type VariableDefinedOutsideLoop.previousVariableNature:nullable instance type VariableScope_10";
                        };
                        assertEquals(merge, d.currentValue().toString());
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
                            case 1 -> "<vp:vn:cm:scope-vdol:1.previousVariableNature@Method_removeInSubBlockMerge_3-E;cm:scope-vdol:1@Method_removeInSubBlockMerge_3-E;cm@Parameter_index;cm@Parameter_previousVariableNature;cm@Parameter_statementIndex;initial:vn@Method_removeInSubBlockMerge_1-C;mom@Parameter_previousVariableNature;mom@Parameter_statementIndex>/*(VariableDefinedOutsideLoop)*/";
                            case 2, 3 -> "<vp:vn:cm@Parameter_index>/*(VariableDefinedOutsideLoop)*/";
                            default -> "vn$1/*(VariableDefinedOutsideLoop)*/";
                        };
                        assertEquals(expected, d.currentValue().toString());
                    }
                    // variable should not exist further than 1...
                    assertTrue(d.statementId().compareTo("2") < 0, "Found in " + d.statementId());
                }
                if (d.variable() instanceof FieldReference fr && "statementIndex".equals(fr.fieldInfo.name)) {
                    if (d.iteration() <= 3) {
                        assertEquals("vdol", fr.scope.toString());
                    } else {
                        assertTrue(Set.of("vdol", "instance type VariableDefinedOutsideLoop").contains(fr.scope.toString()));
                    }
                    if ("1".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<f:statementIndex>" : "nullable instance type String";
                        assertEquals(expected, d.currentValue().toString());
                        assertTrue(d.variableInfoContainer().variableNature() instanceof VariableNature.NormalLocalVariable);
                    }
                }
                if (d.variable() instanceof FieldReference fr && "previousVariableNature".equals(fr.fieldInfo.name)) {
                    if ("1".equals(d.statementId())) {
                        if ("vdol".equals(fr.scope.toString())) {

                        } else if ("scope-vdol:1".equals(fr.scope.toString())) {
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
                    String expected = switch (d.iteration()) {
                        case 0 -> "CM{state=!<instanceOf:VariableDefinedOutsideLoop>||!<m:startsWith>;parent=CM{}}";
                        default -> "CM{state=!vn$1/*(VariableDefinedOutsideLoop)*/.statementIndex.startsWith(index+\".\")||!(vn instanceof VariableDefinedOutsideLoop);parent=CM{}}";
                    };
                    assertEquals(expected, d.conditionManagerForNextStatement().toString());
                }
                if ("2".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0 -> "CM{state=(!<instanceOf:VariableDefinedOutsideLoop>||!<m:startsWith>)&&(!<null-check>||this==(<instanceOf:VariableDefinedOutsideLoop>&&<m:startsWith>?<f:vdol.previousVariableNature>:<vl:vn>));parent=CM{}}";
                        case 1 -> "CM{state=(!vn$1/*(VariableDefinedOutsideLoop)*/.statementIndex.startsWith(index+\".\")||!(vn instanceof VariableDefinedOutsideLoop))&&(!<null-check>||this==(vn$1/*(VariableDefinedOutsideLoop)*/.statementIndex.startsWith(index+\".\")&&vn$1 instanceof VariableDefinedOutsideLoop?<oos:previousVariableNature>:nullable instance type VariableScope_10));parent=CM{}}";
                        default -> "";
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
                        String expected = d.iteration() <= 1 ? "s.length()==<f:x.i>?<f:x.i>:<return value>"
                                : "s.length()==x.i?s.length():<return value>";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("0".equals(d.statementId())) {
                        String expected = d.iteration() <= 1 ? "xs.isEmpty()||s.length()!=<f:x.i>?<return value>:<f:x.i>"
                                : "xs.isEmpty()||s.length()!=scope-x:0.i?<return value>:s.length()";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        String expected = d.iteration() <= 1 ? "xs.isEmpty()||<f:x.i>!=<m:length>?0:<f:x.i>"
                                : "xs.isEmpty()||s.length()!=scope-x:0.i?0:s.length()";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    String expected = d.iteration() <= 1
                            ? "CM{state=xs.isEmpty()||s.length()!=<f:x.i>;parent=CM{}}"
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
                                : "s.length()==y/*(X)*/.i$0?s.length():<return value>";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("0".equals(d.statementId())) {
                        String expected = d.iteration() <= 1
                                ? "y instanceof X&&null!=y&&s.length()==<f:x.i>?<f:x.i>:<return value>"
                                : "y instanceof X&&null!=y&&s.length()==scope-x:0.i?s.length():<return value>";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        String expected = d.iteration() <= 1
                                ? "y instanceof X&&null!=y&&s.length()==<f:x.i>?<f:x.i>:0"
                                : "y instanceof X&&null!=y&&s.length()==scope-x:0.i?s.length():0";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
                if (d.variable() instanceof FieldReference fr && "i".equals(fr.fieldInfo.name)) {
                    if ("x".equals(fr.scope.toString())) {
                        if ("0.0.0.0.0".equals(d.statementId())) {
                            String expected = d.iteration() <= 1 ? "<f:i>" : "s.length()";
                            assertEquals(expected, d.currentValue().toString());
                        }
                        if ("0.0.0".equals(d.statementId())) {
                            String expected = d.iteration() <= 1 ? "<f:i>"
                                    : "s.length()==y/*(X)*/.i$0?s.length():instance type int";
                            assertEquals(expected, d.currentValue().toString());
                        }
                    } else if ("scope-x:0".equals(fr.scope.toString())) {
                        assertNotNull(fr.scopeVariable);
                        assertEquals("scope-x:0", fr.scopeVariable.fullyQualifiedName());
                    } else fail("Scope " + fr.scope);
                }
                if ("scope-x:0".equals(d.variableName())) {
                    if (d.variable() instanceof LocalVariableReference lvr) {
                        if (lvr.variableNature() instanceof VariableNature.ScopeVariable sv) {
                            assertEquals("0~", sv.indexCreatedInMerge());
                        } else fail();
                    } else fail();
                    assertFalse(d.statementId().startsWith("0."), "in " + d.statementId());
                    if ("0".equals(d.statementId())) {
                        String expected = d.iteration() <= 1 ? "<oos:x>" : "y/*(X)*/";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    String expected = d.iteration() <= 1
                            ? "CM{state=xs.isEmpty()||s.length()!=<f:x.i>;parent=CM{}}"
                            : "CM{state=xs.isEmpty()||s.length()!=scope-x:0.i;parent=CM{}}";
                    assertEquals(expected, d.statementAnalysis().stateData().getConditionManagerForNextStatement().toString());
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
}
