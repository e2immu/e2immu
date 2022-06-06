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

package org.e2immu.analyser.parser.own.output;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class Test_61_OutputBuilderSimplified extends CommonTestRunner {

    public Test_61_OutputBuilderSimplified() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        testClass("OutputBuilderSimplified_0", 0, 0, new DebugConfiguration.Builder()
                .build());
    }


    @Test
    public void test_1() throws IOException {
        testClass("OutputBuilderSimplified_1", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_2() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("go".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    if ("0.0.0".equals(d.statementId())) {
                        assertEquals(MultiLevel.NOT_INVOLVED_DV, d.getProperty(Property.EXTERNAL_IMMUTABLE));

                        assertEquals(MultiLevel.NOT_INVOLVED_DV, d.getProperty(Property.EXTERNAL_NOT_NULL));
                    }
                }
                if (d.variable() instanceof ParameterInfo p && "o1".equals(p.name)) {
                    if ("0".equals(d.statementId())) {
                        String expectedValue = d.iteration() == 0 ? "<p:o1>" : "nullable instance type OutputBuilderSimplified_2/*@Identity*/";
                        assertEquals(expectedValue, d.currentValue().toString());

                        assertEquals("", d.variableInfo().getLinkedVariables().toString());

                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);

                        assertDv(d, MultiLevel.MUTABLE_DV, Property.CONTEXT_IMMUTABLE);
                        assertDv(d, 1, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
                    }
                }

                if (d.variable() instanceof ParameterInfo p && "o2".equals(p.name)) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, 0, MultiLevel.MUTABLE_DV, Property.CONTEXT_IMMUTABLE);

                        String expect = "nullable instance type OutputBuilderSimplified_2";
                        assertEquals(expect, d.currentValue().toString(), d.statementId());
                        // mutable, because self type
                        assertDv(d, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
                    }
                    if ("1".equals(d.statementId())) {
                        String expectedValue = d.iteration() == 0 ? "<p:o2>" : "nullable instance type OutputBuilderSimplified_2";
                        assertEquals(expectedValue, d.currentValue().toString());

                        assertEquals("", d.variableInfo().getLinkedVariables().toString());

                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);


                        // links have not been established
                        assertDv(d, MultiLevel.MUTABLE_DV, Property.CONTEXT_IMMUTABLE);
                        // mutable, because self type
                        assertDv(d, 1, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
                    }
                    if ("0.0.0".equals(d.statementId())) {
                        assertEquals("nullable instance type OutputBuilderSimplified_2", d.currentValue().toString());
                        // mutable, because self type
                        assertDv(d, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
                    }
                }
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("OutputBuilderSimplified_2".equals(d.typeInfo().simpleName)) {
                assertDv(d, 2, MultiLevel.EFFECTIVELY_E1IMMUTABLE_DV, Property.IMMUTABLE);
            }
        };

        // SUMMARY: in iteration 4, o2 should have IMMUTABLE = @E1Immutable
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("go".equals(d.methodInfo().name)) {
                for (ParameterAnalysis param : d.parameterAnalyses()) {
                    // no direct link with a parameter which has to be/will be dynamically immutable
                    assertEquals(MultiLevel.NOT_INVOLVED_DV, param.getProperty(Property.EXTERNAL_IMMUTABLE));
                    assertDv(d, 3, MultiLevel.EFFECTIVELY_E1IMMUTABLE_DV, Property.IMMUTABLE);
                }
            }
            if ("isEmpty".equals(d.methodInfo().name)) {
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
        };

        testClass("OutputBuilderSimplified_2", 0, 0, new DebugConfiguration.Builder()
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_3() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("combiner".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    String expectValue = d.iteration() == 0 ? "<m:isEmpty>" : "`a.list`.isEmpty()";
                    assertEquals(expectValue, d.evaluationResult().value().toString());
                }
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("combiner".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo p && "a".equals(p.name)) {
                    if ("0".equals(d.statementId()) || "1".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<p:a>"
                                : "nullable instance type OutputBuilderSimplified_3/*@Identity*/";
                        assertEquals(expected, d.currentValue().toString(),
                                "Statement " + d.statementId() + " it " + d.iteration());
                    }
                    if ("2.0.0".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<p:a>"
                                : "nullable instance type OutputBuilderSimplified_3/*@Identity*/";
                        assertEquals(expected, d.currentValue().toString(),
                                "Statement " + d.statementId() + " it " + d.iteration());
                    }
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("0".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<m:isEmpty>?b:<return value>"
                                : "`a.list`.isEmpty()?b:<return value>";
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId()) || "2".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<m:isEmpty>?<p:a>:<m:isEmpty>?b:<return value>"
                                : "`b.list`.isEmpty()?a:`a.list`.isEmpty()?b:<return value>";
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                }
                if (d.variable() instanceof FieldReference fr && "list".equals(fr.fieldInfo.name)) {
                    if ("a.list".equals(d.variable().toString())) {
                        if ("0".equals(d.statementId())) {
                            assertTrue(d.iteration() > 0);
                            // question 1: why does a.list appear while the evaluation is still <m:isEmpty> ?
                            // because a.list has been identified, but aspects of .isEmpty() have not been cleared yet
                            assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(Property.CONTEXT_NOT_NULL));
                        }
                    }
                }
            }
        };

        // the warning is correct but not particularly useful, because we have no way of ensuring that "new LinkedList"
        // will become content not null
        testClass("OutputBuilderSimplified_3", 0, 1, new DebugConfiguration.Builder()
                        .addEvaluationResultVisitor(evaluationResultVisitor)
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .build(),
                new AnalyserConfiguration.Builder()
                        .setComputeContextPropertiesOverAllMethods(true)
                        .setComputeFieldAnalyserAcrossAllMethods(true)
                        .build());
    }

    /*
     There is no pressing need to turn the NewObject into a Lambda (that's another war to fight, plus, in OutputBuilder
     we have a Collector, which is not a functional interface).

     */
    @Test
    public void test_4() throws IOException {
        testClass("OutputBuilderSimplified_4", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_5() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("j2".equals(d.methodInfo().name)) {
                assertEquals(DV.FALSE_DV, d.methodAnalysis().getProperty(Property.MODIFIED_METHOD));
                assertEquals("null", d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("j1".equals(d.methodInfo().name)) {
                assertEquals(DV.FALSE_DV, d.methodAnalysis().getProperty(Property.MODIFIED_METHOD));
            }
        };
        testClass("OutputBuilderSimplified_5", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test_6() throws IOException {
        // method should be marked static
        testClass("OutputBuilderSimplified_6", 1, 0, new DebugConfiguration.Builder()
                .build());
    }

    // delay, but not minimized; see 9
    // see also _12, _12_alpha, where we had to ensure that only the relevant variables are reported upwards
    // still, this one is yet again a bit different; the order of execution remains (wrongly) important
    @Test
    public void test_7() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            assertFalse(d.context().evaluationContext().allowBreakDelay());

            if ("apply".equals(d.methodInfo().name) && "$6".equals(d.methodInfo().typeInfo.simpleName)) {
                if ("result".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("new OutputBuilderSimplified_7()", d.currentValue().toString());
                        assertDv(d, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER);
                    }
                    if ("1".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0 -> "<f:NONE>==start?new OutputBuilderSimplified_7():<mmc:result>";
                            case 1 -> "<vp:NONE:container@Class_Space>==start?new OutputBuilderSimplified_7():<mmc:result>";
                            default -> "Space.NONE==start?new OutputBuilderSimplified_7():instance type OutputBuilderSimplified_7";
                        };
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 2, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER);
                    }
                    if ("2".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0 -> "<f:NONE>==start?new OutputBuilderSimplified_7():<mmc:result>";
                            case 1 -> "<vp:NONE:container@Class_Space>==start?new OutputBuilderSimplified_7():<mmc:result>";
                            default -> "instance type OutputBuilderSimplified_7";
                        };
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 2, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER);
                    }
                }
                if (d.variable() instanceof ParameterInfo pi && "start".equals(pi.name)) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("nullable instance type OutputElement", d.currentValue().toString());
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("1".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0 -> "<f:NONE>==nullable instance type OutputElement?nullable instance type OutputElement:<p:start>";
                            case 1 -> "<vp:NONE:container@Class_Space>==nullable instance type OutputElement?nullable instance type OutputElement:<p:start>";
                            case 2 -> "Space.NONE==nullable instance type OutputElement?nullable instance type OutputElement:<p:start>";
                            default -> "nullable instance type OutputElement";
                        };
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 3, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("4".equals(d.statementId())) {
                        assertDv(d, 3, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if (d.variable() instanceof ParameterInfo pi && "end".equals(pi.name)) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("nullable instance type OutputElement", d.currentValue().toString());
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("3.0.0".equals(d.statementId())) {
                        String expected = d.iteration() <= 2 ? "<p:end>" : "nullable instance type OutputElement";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 3, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("3".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0, 1 -> "<f:NONE>==nullable instance type OutputElement?nullable instance type OutputElement:<p:end>";
                            case 2 -> "Space.NONE==nullable instance type OutputElement?nullable instance type OutputElement:<p:end>";
                            default -> "nullable instance type OutputElement";
                        };
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 3, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("4".equals(d.statementId())) {
                        assertDv(d, 3, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }
            if ("combiner".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    // modifying method, so the lambda becomes an instance
                    String expect = d.iteration() <= 2 ? "<m:apply>" : "instance type $5";
                    assertEquals(expect, d.currentValue().toString());
                    assertDv(d, 3, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
                }
            }
            if ("accumulator".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    assertEquals("instance type $3", d.currentValue().toString());
                    assertDv(d, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
                }
            }
            if ("joining".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "separator".equals(pi.name)) {
                    assertDv(d, 3, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                } else if (d.variable() instanceof ParameterInfo pi && "start".equals(pi.name)) {
                    assertDv(d, 3, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                } else if (d.variable() instanceof ParameterInfo pi && "end".equals(pi.name)) {
                    assertDv(d, 3, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                } else if (d.variable() instanceof This) {
                    assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                } else if (d.variable() instanceof FieldReference fr && "list".equals(fr.fieldInfo.name)) {
                    fail("list does not occur without in a scope known to the top-level method");
                } else if (d.variable() instanceof FieldReference fr && "NONE".equals(fr.fieldInfo.name)) {
                    fail("Variable NONE should not have been transferred");
                } else if (d.variable() instanceof ParameterInfo pi && "a".equals(pi.name)) {
                    fail("Variable should not have been transferred");
                } else if (d.variable() instanceof ParameterInfo pi && "b".equals(pi.name)) {
                    fail("Variable should not have been transferred");
                } else if (d.variable() instanceof ParameterInfo pi && "aa".equals(pi.name)) {
                    fail("Variable should not have been transferred");
                } else if (d.variable() instanceof ParameterInfo pi && "bb".equals(pi.name)) {
                    fail("Variable should not have been transferred");
                } else if (d.variable() instanceof ReturnVariable) {
                    assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    String expected = d.iteration() <= 4 ? "<new:Collector<OutputBuilderSimplified_7,OutputBuilderSimplified_7,OutputBuilderSimplified_7>>"
                            : "new Collector<>(){private final AtomicInteger countMid=new AtomicInteger();public Supplier<OutputBuilderSimplified_7> supplier(){return OutputBuilderSimplified_7::new;}public BiConsumer<OutputBuilderSimplified_7,OutputBuilderSimplified_7> accumulator(){return (a,b)->{... debugging ...};}public BinaryOperator<OutputBuilderSimplified_7> combiner(){return (a,b)->{... debugging ...};}public Function<OutputBuilderSimplified_7,OutputBuilderSimplified_7> finisher(){return t->{... debugging ...};}public Set<Characteristics> characteristics(){return Set.of(Characteristics.CONCURRENT);}}";
                    assertEquals(expected, d.currentValue().toString());
                } else fail("Variable " + d.variableName());
            }
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("OutputBuilderSimplified_7".equals(d.typeInfo().simpleName)) {
                assertDv(d, 2, MultiLevel.EFFECTIVELY_E1IMMUTABLE_DV, Property.IMMUTABLE);
                assertDv(d, 4, MultiLevel.CONTAINER_DV, Property.CONTAINER);
                if (d.iteration() == 3) {
                    assertEquals("cm@Parameter_end;cm@Parameter_separator;cm@Parameter_start",
                            d.typeAnalysis().getProperty(Property.CONTAINER).causesOfDelay().toString());
                }
            }
            if ("$2".equals(d.typeInfo().simpleName)) {
                assertEquals(d.iteration() <= 2, d.typeAnalysis().approvedPreconditionsStatus(true).isDelayed());
                assertDv(d, 3, MultiLevel.EFFECTIVELY_E1IMMUTABLE_DV, Property.IMMUTABLE);
                assertDv(d, 5, MultiLevel.CONTAINER_DV, Property.CONTAINER);
            }
            if ("$5".equals(d.typeInfo().simpleName)) {
                assertDv(d, 4, MultiLevel.EFFECTIVELY_E1IMMUTABLE_DV, Property.IMMUTABLE);
                assertDv(d, 2, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER);
            }
            if ("$6".equals(d.typeInfo().simpleName)) {
                assertDv(d, 4, MultiLevel.EFFECTIVELY_E1IMMUTABLE_DV, Property.IMMUTABLE);
                assertDv(d, 6, MultiLevel.CONTAINER_DV, Property.CONTAINER);
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                TypeInfo t0 = d.methodInfo().methodInspection.get().getParameters().get(0).parameterizedType.typeInfo;
                if ("OutputElement".equals(t0.simpleName)) {
                    assertDv(d, DV.TRUE_DV, Property.MODIFIED_METHOD);
                    assertDv(d.p(0), 2, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                } else if ("OutputBuilderSimplified_7".equals(t0.simpleName)) {
                    assertDv(d, DV.TRUE_DV, Property.MODIFIED_METHOD);
                } else fail("Add? " + d.methodInfo().fullyQualifiedName);
            }
            if ("joining".equals(d.methodInfo().name)) {
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }

            if ("apply".equals(d.methodInfo().name) && "$4".equals(d.methodInfo().typeInfo.simpleName)) {
                assertEquals("$2", d.methodInfo().typeInfo.packageNameOrEnclosingType.getRight().simpleName);

                String expected = d.iteration() <= 2 ? "<m:apply>"
                        : "b.list.isEmpty()?a:a.list.isEmpty()&&!b.list.isEmpty()?b:nullable instance type OutputBuilderSimplified_7/*@Identity*//*{L a:dependent:2}*/";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                assertDv(d, 3, DV.TRUE_DV, Property.MODIFIED_METHOD);

                ParameterAnalysis p1 = d.parameterAnalyses().get(1);
                assertEquals(MultiLevel.NOT_INVOLVED_DV, p1.getProperty(Property.EXTERNAL_IMMUTABLE));
            }
            if ("combiner".equals(d.methodInfo().name)) {
                String expected = d.iteration() <= 2 ? "<m:combiner>" : "instance type $5";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                assertDv(d, 3, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
                assertDv(d, 3, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                assertDv(d, 3, DV.TRUE_DV, Property.MODIFIED_METHOD);
            }
            if ("supplier".equals(d.methodInfo().name)) {
                assertEquals("/*inline supplier*/OutputBuilderSimplified_7::new", d.methodAnalysis().getSingleReturnValue().toString());
                assertDv(d, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("apply".equals(d.methodInfo().name) && "$6".equals(d.methodInfo().typeInfo.simpleName)) {
                assertEquals("$2", d.methodInfo().typeInfo.packageNameOrEnclosingType.getRight().simpleName);
                String expected = d.iteration() <= 1 ? "<m:apply>" : "/*inline apply*/result";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());

                assertDv(d.p(0), 3, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_PARAMETER);
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("accumulator".equals(d.methodInfo().name)) {
                assertDv(d, 3, DV.TRUE_DV, Property.MODIFIED_METHOD);
            }
            if ("finisher".equals(d.methodInfo().name)) {
                assertDv(d, 3, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("characteristics".equals(d.methodInfo().name)) {
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
        };
        testClass("OutputBuilderSimplified_7", 0, 0, new DebugConfiguration.Builder()
            //    .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
             //   .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build(), new AnalyserConfiguration.Builder().setForceAlphabeticAnalysisInPrimaryType(false).build());
    }

    // picked up a bug in Identity computation
    @Test
    public void test_8() throws IOException {
        // unused parameter, +4x nullable instead of at least not null
        testClass("OutputBuilderSimplified_8", 4, 1, new DebugConfiguration.Builder()
                .build());
    }

    // again, simplifying to find the infinite loop; this time, there's a modifying method
    @Test
    public void test_9() throws IOException {
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("countMid".equals(d.fieldInfo().name)) {
                assertDv(d, DV.TRUE_DV, Property.MODIFIED_OUTSIDE_METHOD);
            }
        };
        testClass("OutputBuilderSimplified_9", 0, 0, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }

    // function instead of consumer
    @Test
    public void test_10() throws IOException {
        testClass("OutputBuilderSimplified_10", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    // again, simplifying to find the infinite loop; this time, everything is immutable
    // but when/how do we reach that conclusion?
    @Test
    public void test_11() throws IOException {
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("OutputBuilderSimplified_11".equals(d.typeInfo().simpleName)) {
                assertDv(d, 2, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
            }
            if ("$1".equals(d.typeInfo().simpleName)) {
                assertDv(d, 3, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
            }
            if ("$2".equals(d.typeInfo().simpleName)) {
                assertDv(d, 4, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("countMid".equals(d.fieldInfo().name)) {
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_OUTSIDE_METHOD);
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("get".equals(d.methodInfo().name) && "$1".equals(d.methodInfo().typeInfo.simpleName)) {
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
        };

        // ignoring the result of a non-modifying method call
        testClass("OutputBuilderSimplified_11", 0, 1, new DebugConfiguration.Builder()
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    /*
    Identical to _7, except for the class name, which has an effect on the order. _7 never runs green; this one
    runs green when alphabetic is false, but goes into an infinite delay loop when alphabetic is true.

    FAIL [Method OutputBuilderSimplified_12, Method debug, Method Space, Method add, Method add, Method joining, Field NONE, Field list, Type OutputBuilderSimplified_12, Type OutputElement, Type Space]
    SUCC [Method joining, Method add, Method Space, Method add, Method OutputBuilderSimplified_12, Method debug, Field list, Field NONE, Type OutputElement, Type Space, Type OutputBuilderSimplified_12]
    FAIL [Method OutputBuilderSimplified_7, Method debug, Method Space, Method joining, Method add, Method add, Field list, Field NONE, Type OutputElement, Type Space, Type OutputBuilderSimplified_7]

     */
    @Test
    public void test_12() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("apply".equals(d.methodInfo().name) && "$6".equals(d.methodInfo().typeInfo.simpleName)) {
                if ("result".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("new OutputBuilderSimplified_12()", d.currentValue().toString());
                    }
                }
            }
            if ("joining".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "separator".equals(pi.name)) {
                    assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                } else if (d.variable() instanceof ParameterInfo pi && "start".equals(pi.name)) {
                    assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                } else if (d.variable() instanceof ParameterInfo pi && "end".equals(pi.name)) {
                    assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                } else if (d.variable() instanceof This) {
                    assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                } else if (d.variable() instanceof FieldReference fr && "list".equals(fr.fieldInfo.name)) {
                    fail("list does not occur without in a scope known to the top-level method");
                } else if (d.variable() instanceof FieldReference fr && "NONE".equals(fr.fieldInfo.name)) {
                    fail("Variable NONE should not have been transferred");
                } else if (d.variable() instanceof ParameterInfo pi && "a".equals(pi.name)) {
                    fail("Variable should not have been transferred");
                } else if (d.variable() instanceof ParameterInfo pi && "b".equals(pi.name)) {
                    fail("Variable should not have been transferred");
                } else if (d.variable() instanceof ParameterInfo pi && "aa".equals(pi.name)) {
                    fail("Variable should not have been transferred");
                } else if (d.variable() instanceof ParameterInfo pi && "bb".equals(pi.name)) {
                    fail("Variable should not have been transferred");
                } else if (d.variable() instanceof ReturnVariable) {
                    assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                } else fail("Variable " + d.variableName());
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                TypeInfo t0 = d.methodInfo().methodInspection.get().getParameters().get(0).parameterizedType.typeInfo;
                if ("OutputElement".equals(t0.simpleName)) {
                    assertDv(d, DV.TRUE_DV, Property.MODIFIED_METHOD);
                } else if ("OutputBuilderSimplified_12".equals(t0.simpleName)) {
                    assertDv(d, DV.TRUE_DV, Property.MODIFIED_METHOD);
                } else fail("Add? " + d.methodInfo().fullyQualifiedName);
            }
            if ("joining".equals(d.methodInfo().name)) {
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("supplier".equals(d.methodInfo().name)) {
                assertEquals("$2", d.methodInfo().typeInfo.simpleName);
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
                assertEquals("/*inline supplier*/OutputBuilderSimplified_12::new", d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("accept".equals(d.methodInfo().name)) {
                assertEquals("$3", d.methodInfo().typeInfo.simpleName);
                assertEquals("$2", d.methodInfo().typeInfo.packageNameOrEnclosingType.getRight().simpleName);
                // accumulator!
                assertDv(d, 3, DV.TRUE_DV, Property.MODIFIED_METHOD);
            }
            if ("apply".equals(d.methodInfo().name) && "$5".equals(d.methodInfo().typeInfo.simpleName)) {
                assertEquals("$2", d.methodInfo().typeInfo.packageNameOrEnclosingType.getRight().simpleName);
                // combiner!
                String expected = d.iteration() <= 2 ? "<m:apply>"
                        : "bb.list.isEmpty()?aa:aa.list.isEmpty()?bb:nullable instance type OutputBuilderSimplified_12/*@Identity*//*{L aa:statically_assigned:0}*//*@NotNull*/";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());

                assertDv(d.p(0), 3, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_PARAMETER);
                assertDv(d, 3, DV.TRUE_DV, Property.MODIFIED_METHOD);
            }
            if ("apply".equals(d.methodInfo().name) && "$6".equals(d.methodInfo().typeInfo.simpleName)) {
                assertEquals("$2", d.methodInfo().typeInfo.packageNameOrEnclosingType.getRight().simpleName);
                // finisher!
                String expected = d.iteration() <= 1 ? "<m:apply>" : "/*inline apply*/result";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());

                assertDv(d.p(0), 3, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_PARAMETER);
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("countMid".equals(d.fieldInfo().name)) {
                assertDv(d, DV.TRUE_DV, Property.MODIFIED_OUTSIDE_METHOD);
            }
            if ("list".equals(d.fieldInfo().name)) {
                assertDv(d, DV.TRUE_DV, Property.MODIFIED_OUTSIDE_METHOD);
            }
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("OutputBuilderSimplified_12".equals(d.typeInfo().simpleName)) {
                assertEquals(d.iteration() >= 2, d.typeAnalysis().approvedPreconditionsStatus(true).isDone());
                assertDv(d, 2, MultiLevel.EFFECTIVELY_E1IMMUTABLE_DV, Property.IMMUTABLE);
            }
            if ("$2".equals(d.typeInfo().simpleName)) {
                assertDv(d, 3, MultiLevel.EFFECTIVELY_E1IMMUTABLE_DV, Property.IMMUTABLE);
            }
            if ("$5".equals(d.typeInfo().simpleName)) {
                assertDv(d, 4, MultiLevel.EFFECTIVELY_E1IMMUTABLE_DV, Property.IMMUTABLE);
            }
            if ("$6".equals(d.typeInfo().simpleName)) {
                assertDv(d, 4, MultiLevel.EFFECTIVELY_E1IMMUTABLE_DV, Property.IMMUTABLE);
            }
        };
        testClass("OutputBuilderSimplified_12", 0, 0, new DebugConfiguration.Builder()
          //      .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
          //      .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
          //      .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
          //      .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build(), new AnalyserConfiguration.Builder().setForceAlphabeticAnalysisInPrimaryType(false).build());
    }

    @Test
    public void test_12_alpha() throws IOException {
        testClass("OutputBuilderSimplified_12", 0, 0, new DebugConfiguration.Builder()
                .build(), new AnalyserConfiguration.Builder().setForceAlphabeticAnalysisInPrimaryType(true).build());
    }
}
