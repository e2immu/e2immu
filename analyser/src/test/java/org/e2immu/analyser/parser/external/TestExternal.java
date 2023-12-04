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

package org.e2immu.analyser.parser.external;

import org.e2immu.analyser.analyser.ChangeData;
import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.it;
import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.it0;
import static org.junit.jupiter.api.Assertions.*;

public class TestExternal extends CommonTestRunner {

    public TestExternal() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                // 'start' stays out of the merge in 2.0.0
                if (d.variable() instanceof ParameterInfo pi && "start".equals(pi.name)) {
                    if ("0".equals(d.statementId())) {
                        assertLinked(d, it(0, "j:0"));
                    }
                    if ("2.0.0".equals(d.statementId())) {
                        VariableInfo initial = d.variableInfoContainer().getPreviousOrInitial();
                        assertLinked(d, initial.getLinkedVariables(), it(0, "j:0"));
                        assertTrue(d.variableInfoContainer().hasMerge());
                        assertLinked(d, it(0, "j:0"));
                    }
                    if ("2.0.0.0.2".equals(d.statementId())) {
                        assertEquals("ExpressionAsStatement{class org.e2immu.analyser.model.expression.Assignment: j=i+1}",
                                d.context().evaluationContext().getCurrentStatement().statement().toString());
                        assertLinked(d, it0("buff:-1,buff[i]:-1,endPos:-1,i:-1,j:-1"), it(1, ""));
                    }
                    if ("2.0.0.0.3".equals(d.statementId()) || "2.0.0.0.4".equals(d.statementId())) {
                        assertEquals("BreakStatement{label=null}",
                                d.context().evaluationContext().getCurrentStatement().statement().toString());
                        assertLinked(d, it0("buff:-1,buff[i]:-1,endPos:-1,i:-1,j:-1"), it(1, ""));
                    }
                }
            }
        };
        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("-----", d.delaySequence());

        testClass("External_0", 0, 0, new DebugConfiguration.Builder()
                .addBreakDelayVisitor(breakDelayVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    // starting error: Property context-not-null, current nullable:1, new not_null:5 overwriting property value CLV
    @Test
    public void test_1() throws IOException {

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "filter".equals(pi.name)) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                        assertLinked(d, it(0, "f:0"));
                    }
                    if ("3".equals(d.statementId())) {
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                        assertLinked(d, it(0, "f:0,h:2"));
                    }
                }
                if ("f".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, DV.TRUE_DV, Property.CNN_TRAVELS_TO_PRECONDITION);
                    }
                }
            }
            if ("report".equals(d.methodInfo().name)) {
                if ("f".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, 2, DV.TRUE_DV, Property.CNN_TRAVELS_TO_PRECONDITION);

                        assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                        assertLinked(d, it0("NOT_YET_SET"), it(1, "filter:0,h:2"));
                    }
                }
                if ("p".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                        assertLinked(d, it0("NOT_YET_SET"), it(1, "process:0"));
                    }
                    if ("1".equals(d.statementId())) {
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                        assertLinked(d, it0("NOT_YET_SET"), it(1, "process:0"));
                    }
                }
                if (d.variable() instanceof ParameterInfo pi && "filter".equals(pi.name)) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                        assertLinked(d, it0("NOT_YET_SET"), it(1, "f:0,h:2"));
                    }
                }
                if (d.variable() instanceof ParameterInfo pi && "process".equals(pi.name)) {
                    if ("1".equals(d.statementId())) {
                        assertLinked(d, it0("NOT_YET_SET"), it(1, "p:0"));
                    }
                }
            }
        };
        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("---", d.delaySequence());
        testClass("External_1", 1, 1, new DebugConfiguration.Builder()
                .addBreakDelayVisitor(breakDelayVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }


    // starting error: java.lang.IllegalStateException: Cannot change statically assigned for variable org.e2immu.analyser.parser.external.testexample.External_3.postAccumulate(org.xml.sax.XMLFilter,org.e2immu.analyser.parser.external.testexample.External_3.ProcessElement):1:process
    //old: p:-1
    //new: p:0
    @Test
    public void test_2() throws IOException {
        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("---", d.delaySequence());

        testClass("External_2", 1, 1, new DebugConfiguration.Builder()
                .addBreakDelayVisitor(breakDelayVisitor)
                .build());
    }

    // starting error: Caught exception analysing statement 0.0.0.0.0 of org.e2immu.analyser.parser.external.testexample.External_3.startDocument()
    // at org.e2immu.analyser.analysis.impl.StateDataImpl.internalAllDoneCheck(StateDataImpl.java:78)
    @Test
    public void test_3() throws IOException {
        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("------", d.delaySequence());

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("startDocument".equals(d.methodInfo().name)) {
                if ("0.0.0.0.0".equals(d.statementId())) {
                    String expected = d.iteration() < 4 ? "!instance 0.0.0 type boolean&&<null-check>"
                            : "!instance 0.0.0 type boolean&&null==document$0";
                    assertEquals(expected, d.statementAnalysis().stateData().getAbsoluteState().toString());
                }
            }
        };

        testClass("External_3", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addBreakDelayVisitor(breakDelayVisitor)
                .build());
    }

    // starting error: java.lang.UnsupportedOperationException: ? have index 11, looking for 9
    // in SASubBlocks:291, Caught exception in method analyser: org.e2immu.analyser.parser.external.testexample.External_4.encode(byte[])
    // then: parameter should not be assigned to (b in checkLine)
    @Test
    public void test_4() throws IOException {
        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("---", d.delaySequence());
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("encode".equals(d.methodInfo().name)) {
                int dot = d.statementId().indexOf('.');
                String firstPart = dot < 0 ? d.statementId() : d.statementId().substring(0, dot);
                assertEquals(2, firstPart.length());
            }
        };
        testClass("External_4", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addBreakDelayVisitor(breakDelayVisitor)
                .build());
    }

    // starting error: java.lang.UnsupportedOperationException: ? no delays, and initial return expression even
    // though return statements are reachable
    @Test
    public void test_5() throws IOException {
        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("-", d.delaySequence());

        testClass("External_5", 0, 0, new DebugConfiguration.Builder()
                .addBreakDelayVisitor(breakDelayVisitor)
                .build());
    }

    // starting error: Changing value of @Independent from not_involved:0 to dependent:1
    @Test
    public void test_6() throws IOException {
        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("-------", d.delaySequence());

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("startDocument".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "currentNode".equals(fr.fieldInfo().name)) {
                    if ("1".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0 -> "<f:document>";
                            case 1, 2, 3 -> "<wrapped:document>";
                            default ->
                                    "null==document$0?External_6.documentBuilder.newDocument():null==(nullable instance type Document).getDocumentElement()?nullable instance type Document:instance 0.1.0.0.0 type Document";
                        };
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 4, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER);
                    }
                }
            }
        };
        testClass("External_6", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addBreakDelayVisitor(breakDelayVisitor)
                .build());
    }

    // starting error: overwriting value: Property context-not-null, current nullable:1, new not_null:5
    // problem in explicit constructor invocation this(...)
    @Test
    public void test_7() throws IOException {
        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("----", d.delaySequence());

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            int n = d.methodInfo().methodInspection.get().getParameters().size();
            if (d.methodInfo().isConstructor() && n == 1) {
                if (d.variable() instanceof FieldReference fr && "K".equals(fr.fieldInfo().name)) {
                    assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                }
            }
            if (d.methodInfo().isConstructor() && n == 2) {
                if (d.variable() instanceof ParameterInfo pi && "key".equals(pi.name)) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                    }
                }
            }
        };

        testClass("External_7", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addBreakDelayVisitor(breakDelayVisitor)
                .build());
    }

    // starting error: Changing value of @Independent from dependent:1 to independent:21
    // runs OK when 'else' keyword is removed on line 15
    @Test
    public void test_8A() throws IOException {
        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("---", d.delaySequence());

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("convertToLongArray".equals(d.methodInfo().name)) {
                if ("0.1.0.0.1.0.1".equals(d.statementId())) {
                    ChangeData cd = d.findValueChange("av-18:32");
                    String expected = d.iteration() == 0 ? "<v:av-18:32[i]>" : "source/*(Object[])*/";
                    assertEquals(expected, cd.value().toString());
                }
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("convertToLongArray".equals(d.methodInfo().name)) {
                if ("value".equals(d.variableName())) {
                    if ("0.1.0.0.1.0.0".equals(d.statementId())) {
                        assertEquals("Type Object", d.variable().parameterizedType().toString());
                        String expected = d.iteration() == 0 ? "<v:av-18:32[i]>"
                                : "source/*(Object[])*/[i$0.1.0.0.1]$0.1.0.0.1.0.0";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
                if ("av-18:32".equals(d.variableName())) {
                    if ("0.1.0.0.1.0.1".equals(d.statementId())) {
                        assertEquals("Type Object[]", d.variable().parameterizedType().toString());
                        assertDv(d, MultiLevel.EFFECTIVELY_FINAL_FIELDS_DV, Property.IMMUTABLE);
                    }
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("convertToLong".equals(d.methodInfo().name)) {
                String expected = d.iteration() == 0 ? "<m:convertToLong>"
                        : "/*inline convertToLong*/source instanceof Number?source/*(Number)*/.longValue():source instanceof String?Long.parseLong(source/*(String)*/):<return value>";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                assertEquals("Type long", d.methodAnalysis().getSingleReturnValue().returnType().toString());

                assertEquals("source instanceof Number||source instanceof String",
                        d.methodAnalysis().getPrecondition().expression().toString());
            }
        };
        testClass("External_8A", 0, 1, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addBreakDelayVisitor(breakDelayVisitor)
                .build());
    }

    // variant: even with removed keyword, it fails; here, we have introduced a Pattern variable list
    @Test
    public void test_8B() throws IOException {
        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("---S-", d.delaySequence());

        testClass("External_8B", 0, 1, new DebugConfiguration.Builder()
                .addBreakDelayVisitor(breakDelayVisitor)
                .build());
    }

    // variant: when using 2 pattern variables, it does not fail
    @Test
    public void test_8C() throws IOException {
        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("---S-", d.delaySequence());

        testClass("External_8C", 0, 1, new DebugConfiguration.Builder()
                .addBreakDelayVisitor(breakDelayVisitor)
                .build());
    }
}
