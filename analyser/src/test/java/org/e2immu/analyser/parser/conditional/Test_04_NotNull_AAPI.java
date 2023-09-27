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

package org.e2immu.analyser.parser.conditional;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.it;
import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.it0;
import static org.junit.jupiter.api.Assertions.*;

public class Test_04_NotNull_AAPI extends CommonTestRunner {
    public Test_04_NotNull_AAPI() {
        super(true);
    }

    @Test
    public void test_3() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("isStrictPrefix".equals(d.methodInfo().name)) {
                if ("node".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        String expected = d.iteration() < 2 ? "<m:goTo>" : "this.goTo(prefix,prefix.length)";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 2, MultiLevel.NULLABLE_DV, Property.NOT_NULL_EXPRESSION);
                    }
                }
            }
            if ("add".equals(d.methodInfo().name)) {
                if ("node".equals(d.variableName())) {
                    if ("2".equals(d.statementId())) {
                        String linked = switch (d.iteration()) {
                            case 0 -> "node.map:-1,strings:-1,this.root:0,this:-1";
                            default -> "node.map:3,this.root:0,this:3";
                        };
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("3".equals(d.statementId())) {
                        String linked = switch (d.iteration()) {
                            case 0 -> "data:-1,node.data:-1,node.map:-1,strings:-1,this.root:0,this:-1";
                            case 1 -> "data:-1,node.data:-1,node.map:-1,this.root:0,this:-1";
                            default -> "node.data:4,node.map:3,this.root:0,this:3";
                        };
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                }
                if (d.variable() instanceof FieldReference fr && "map".equals(fr.fieldInfo.name)) {
                    assertNotNull(fr.scopeVariable);
                    if ("node".equals(fr.scopeVariable.simpleName())) {
                        if ("2".equals(d.statementId())) {
                            String linked = switch (d.iteration()) {
                                case 0 -> "node:-1,strings:-1,this.root:-1,this:-1";
                                default -> "node:2,this.root:2,this:3";
                            };
                            assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                        }
                        if ("3".equals(d.statementId())) {
                            String linked = switch (d.iteration()) {
                                case 0 -> "data:-1,node.data:-1,node:-1,strings:-1,this.root:-1,this:-1";
                                case 1 -> "data:-1,node.data:-1,node:-1,this.root:-1,this:-1";
                                default -> "node.data:4,node:2,this.root:2,this:3";
                            };
                            assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                        }
                    }
                }
                if (d.variable() instanceof FieldReference fr && "data".equals(fr.fieldInfo.name)) {
                    assertNotNull(fr.scopeVariable);
                    if ("node".equals(fr.scopeVariable.simpleName())) {
                        if ("1".equals(d.statementId())) {
                            fail();
                        }
                        if ("2.0.0".equals(d.statementId())) {
                            assertEquals("new LinkedList<>()", d.currentValue().toString());
                        }
                        if ("2".equals(d.statementId())) {
                            VariableInfo eval = d.variableInfoContainer().best(Stage.EVALUATION);
                            String evalValue = d.iteration() < 2 ? "<f:node.data>" : "nullable instance type List<T>";
                            assertEquals(evalValue, eval.getValue().toString());

                            String expected = switch (d.iteration()) {
                                case 0 -> "<null-check>?new LinkedList<>():<f:node.data>";
                                default ->
                                        "null==nullable instance type List<T>?new LinkedList<>():nullable instance type List<T>";
                            };
                            assertEquals(expected, d.currentValue().toString());
                            assertEquals("Type java.util.List<T>", d.currentValue().returnType().toString());
                        }
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                String string = d.statementAnalysis().stateData().equalityAccordingToStateStream()
                        .map(Object::toString).sorted().toList().toString();
                String equality = d.iteration() == 0 ? "[<f:node.map>=null]" : "[<f:node.map>=null, node$1.map$0=null]";
                if ("1.0.1.0.0".equals(d.statementId())) {
                    assertEquals(equality, string);

                    // no local ignore
                    String cm = d.iteration() == 0
                            ? "CM{condition=<null-check>;parent=CM{condition=<null-check>;parent=CM{condition=strings.length>=1;parent=CM{condition=strings.length>=1;parent=CM{ignore=node;parent=CM{}}}}}}"
                            : "CM{condition=null==node$1.map$0;parent=CM{condition=null==node$1.map$0;parent=CM{condition=strings.length>=1;parent=CM{condition=strings.length>=1;parent=CM{ignore=node;parent=CM{}}}}}}";
                    assertEquals(cm, d.conditionManagerForNextStatement().toString());
                    String absolute = d.iteration() == 0
                            ? "<null-check>&&strings.length>=1"
                            : "null==node$1.map$0&&strings.length>=1";
                    assertEquals(absolute, d.absoluteState().toString());
                }
                if ("1.0.1.0.1".equals(d.statementId())) {
                    assertEquals(equality, string);

                    // local ignore: map
                    String cm = d.iteration() == 0
                            ? "CM{condition=<null-check>;ignore=map;parent=CM{condition=<null-check>;parent=CM{condition=strings.length>=1;parent=CM{condition=strings.length>=1;parent=CM{ignore=node;parent=CM{}}}}}}"
                            : "CM{condition=null==node$1.map$0;ignore=map;parent=CM{condition=null==node$1.map$0;parent=CM{condition=strings.length>=1;parent=CM{condition=strings.length>=1;parent=CM{ignore=node;parent=CM{}}}}}}";
                    assertEquals(cm, d.conditionManagerForNextStatement().toString());

                    // IMPORTANT: the null-check on node.map should be gone, since node.map was overwritten in
                    // this statement.
                    assertEquals("strings.length>=1", d.absoluteState().toString());
                }
                if ("1.0.1.0.2".equals(d.statementId())) {
                    assertEquals("[]", string);

                    // local ignore: map and newTrieNode
                    String cm = d.iteration() == 0
                            ? "CM{condition=<null-check>;ignore=map,newTrieNode;parent=CM{condition=<null-check>;parent=CM{condition=strings.length>=1;parent=CM{condition=strings.length>=1;parent=CM{ignore=node;parent=CM{}}}}}}"
                            : "CM{condition=null==node$1.map$0;ignore=map,newTrieNode;parent=CM{condition=null==node$1.map$0;parent=CM{condition=strings.length>=1;parent=CM{condition=strings.length>=1;parent=CM{ignore=node;parent=CM{}}}}}}";
                    assertEquals(cm, d.conditionManagerForNextStatement().toString());
                    assertEquals("strings.length>=1", d.absoluteState().toString());
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("goTo".equals(d.methodInfo().name)) {
                String expected = d.iteration() < 2 ? "<m:goTo>"
                        : "-1-(instance type int)+upToPosition>=0&&(null==(null==node$1.map$0?node$1:node$1.map$0.get(nullable instance type String)).map$1.get(nullable instance type String)||null==(null==node$1.map$0?node$1:node$1.map$0.get(nullable instance type String)).map$1)?null:-1-(instance type int)+upToPosition>=0?null==node$1.map$0?node$1:node$1.map$0.get(nullable instance type String):root";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                assertDv(d, 2, MultiLevel.NULLABLE_DV, Property.NOT_NULL_EXPRESSION);
            }
        };

        BreakDelayVisitor breakDelayVisitor = d -> {
            assertEquals("-----", d.delaySequence());
        };

        testClass("NotNull_3", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addBreakDelayVisitor(breakDelayVisitor)
                .build(), new AnalyserConfiguration.Builder()
                .setComputeFieldAnalyserAcrossAllMethods(true)
                .build());
    }


    // the normal Analyser setting would be computeFieldAnalyserAcrossAllMethods, rather
    // than ComputeContextPropertiesOverAllMethods. (used to crash before better logic in GreaterThanZero and And...)
    @Test
    public void test_3_1() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                if ("newTrieNode".equals(d.variableName())) {
                    if ("1.0.1.0.3".equals(d.statementId())) {
                        assertLinked(d,
                                it0("node.map:-1,node:-1,this.root:-1,this:-1"),
                                it(1, "node.map:3,node:3,this.root:3,this:3"));
                        assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("1.0.1.1.1.0.1".equals(d.statementId())) {
                        assertEquals(0, d.iteration());
                        String linked = "node.map:-1,node:-1,s:-1,this.root:-1,this:-1";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("1.0.1.1.1".equals(d.statementId())) {
                        assertEquals(0, d.iteration());
                        String linked = "node.map:-1,node:-1,s:-1,this.root:-1,this:-1";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("1.0.1".equals(d.statementId())) {
                        assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                        assertLinked(d,
                                it0("node.map:-1,node:-1,s:-1,this.root:-1,this:-1"),
                                it(1, "node.map:3,node:3,this.root:3,this:3"));
                    }
                    if ("1.0.2".equals(d.statementId())) {
                        assertLinked(d,
                                it0("node.map:-1,node:0,s:-1,this.root:-1,this:-1"),
                                it(1, "node.map:3,node:0,this.root:3,this:3"));
                        assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if ("node".equals(d.variableName())) {
                    if ("1.0.1.0.3".equals(d.statementId())) {
                        assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("1.0.1.1.1".equals(d.statementId())) {
                        assertEquals(0, d.iteration());
                    }
                    if ("1.0.1".equals(d.statementId())) {
                        assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                        assertLinked(d,
                                it0("newTrieNode:-1,node.map:-1,s:-1,this.root:0,this:-1"),
                                it(1, "node.map:4,this.root:0,this:3"));
                    }
                    if ("1.0.2".equals(d.statementId())) {
                        assertLinked(d,
                                it0("newTrieNode:0,node.map:-1,s:-1,this.root:-1,this:-1"),
                                it(1, "newTrieNode:0,node.map:3,this.root:3,this:3"));
                        assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }
            if ("isStrictPrefix".equals(d.methodInfo().name)) {
                if ("node".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                        assertCurrentValue(d, 8, "this.goTo(prefix,prefix.length)");
                    }
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("1".equals(d.statementId())) {
                        assertCurrentValue(d, 8, "null!=this.goTo(prefix,prefix.length)");
                    }
                }
            }
        };

        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("----S-SF---", d.delaySequence());

        testClass("NotNull_3", 6, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addBreakDelayVisitor(breakDelayVisitor)
                .build(), new AnalyserConfiguration.Builder()
                .setComputeContextPropertiesOverAllMethods(true)
                .build());
    }


    @Test
    public void test_4() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("get".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    String expected = d.iteration() <= 1 ? "<null-check>?null:<f:node.data>" : "null";
                    assertEquals(expected, d.evaluationResult().value().toString());

                    // important: whatever happens, 'node' cannot have CNN not null!
                    ChangeData cd = d.findValueChangeByToString("node");
                    DV cnn = cd.getProperty(Property.CONTEXT_NOT_NULL);
                    assertNotEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, cnn);
                }
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("get".equals(d.methodInfo().name)) {
                if ("node".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        String expected = d.iteration() <= 1 ? "<m:goTo>" : "this.goTo(strings,upToPosition)";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 2, MultiLevel.NULLABLE_DV, Property.NOT_NULL_EXPRESSION);
                    }
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("1".equals(d.statementId())) {
                        assertCurrentValue(d, 2, "null");
                    }
                }
            }
            if ("isStrictPrefix".equals(d.methodInfo().name)) {
                if ("node".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        String expected = d.iteration() <= 1 ? "<m:goTo>" : "this.goTo(prefix,prefix.length)";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 2, MultiLevel.NULLABLE_DV, Property.NOT_NULL_EXPRESSION);
                    }
                }
            }
            if ("goTo".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "root".equals(fr.fieldInfo.name)) {
                    assertTrue(fr.scopeIsThis());
                    assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.EXTERNAL_NOT_NULL);
                }
                if (d.variable() instanceof ReturnVariable) {
                    String expected = d.iteration() <= 1
                            ? "upToPosition>=strings.length?null:<f:root>"
                            : "upToPosition>=strings.length?null:root";
                    assertEquals(expected, d.currentValue().toString());
                    assertDv(d, MultiLevel.NULLABLE_DV, Property.NOT_NULL_EXPRESSION);
                    // the contribution of "root" travels to the return variable
                    // it'll be used only
                    assertDv(d, 2, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.EXTERNAL_NOT_NULL);
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("goTo".equals(d.methodInfo().name)) {
                String expected = d.iteration() <= 1 ? "<m:goTo>" : "upToPosition>=strings.length?null:root";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                assertDv(d, 2, MultiLevel.NULLABLE_DV, Property.NOT_NULL_EXPRESSION);
            }
        };

        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("----", d.delaySequence());

        testClass("NotNull_4", 2, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addBreakDelayVisitor(breakDelayVisitor)
                .build(), new AnalyserConfiguration.Builder().setComputeContextPropertiesOverAllMethods(true).build());
    }


    @Test
    public void test_4_1() throws IOException {
        testClass("NotNull_4_1", 2, 0, new DebugConfiguration.Builder()
                .build(), new AnalyserConfiguration.Builder().setComputeContextPropertiesOverAllMethods(true).build());
    }

    /*
    show that the null-check excludes a check on in.toUpperCase()

    show that in method3, the in!=null situation does not contain an 'in == null' check anymore
     */
    @Test
    public void test_6() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("method3".equals(d.methodInfo().name)) {
                if ("2".equals(d.statementId())) {
                    String expected = "null==NotNull_6.compute(in)?null:\"Not null: \"+NotNull_6.compute(in)+\" == \"+NotNull_6.compute(in)";
                    assertEquals(expected, d.evaluationResult().getExpression().toString());
                }
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method2".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    if ("1".equals(d.statementId())) {
                        String expected = "null==NotNull_6.compute(in)?null:\"Not null: \"+NotNull_6.compute(in)+\" == \"+NotNull_6.compute(in)";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method2".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    assertEquals("null!=NotNull_6.compute(in)",
                            d.statementAnalysis().stateData().getAbsoluteState().toString());
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("compute".equals(d.methodInfo().name)) {
                String expected = "null==in?null:in.toUpperCase()";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("method".equals(d.methodInfo().name)) {
                assertEquals("null==NotNull_6.compute(s)", d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("method2".equals(d.methodInfo().name)) {
                String expected = "null==NotNull_6.compute(in)?null:\"Not null: \"+NotNull_6.compute(in)+\" == \"+NotNull_6.compute(in)";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("method3".equals(d.methodInfo().name)) {
                String expected = "null==NotNull_6.compute(in)?null:\"Not null: \"+NotNull_6.compute(in)+\" == \"+NotNull_6.compute(in)";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
            }
        };
        testClass("NotNull_6", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build(), new AnalyserConfiguration.Builder().setNormalizeMore(true).build());
    }

    @Disabled("Null pointer warning is wrong!")
    @Test
    public void test_8() throws IOException {
        testClass("NotNull_8", 0, 0, new DebugConfiguration.Builder()
                .build(), new AnalyserConfiguration.Builder().setComputeContextPropertiesOverAllMethods(false).build());
    }
}
